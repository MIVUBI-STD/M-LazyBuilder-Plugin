use serde_json::Value;
use sha2::{Digest, Sha256};
use std::fs::{self, OpenOptions};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::process::Command;
#[cfg(windows)]
use std::os::windows::fs::MetadataExt;
#[cfg(windows)]
use std::os::windows::process::CommandExt;

const JAVA_MAJOR: u32 = 21;
const USER_AGENT: &str = concat!("LazyBuilder/", env!("CARGO_PKG_VERSION"));
const ASSETS_URL: &str = "https://api.adoptium.net/v3/assets/feature_releases/21/ga?architecture=x64&heap_size=normal&image_type=jre&jvm_impl=hotspot&os=windows&page=0&page_size=1&project=jdk&sort_method=DEFAULT&sort_order=DESC&vendor=eclipse";
const MAX_JAVA_ARCHIVE_BYTES: u64 = 512 * 1024 * 1024;
const MAX_JAVA_ARCHIVE_ENTRIES: usize = 20_000;
const MAX_JAVA_EXTRACTED_BYTES: u64 = 2 * 1024 * 1024 * 1024;
#[cfg(windows)]
const CREATE_NO_WINDOW: u32 = 0x0800_0000;
#[cfg(windows)]
const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x00000400;

#[derive(Clone, Debug)]
struct JavaRelease {
    link: String,
    checksum: String,
}

pub fn managed_java_path() -> Result<PathBuf, String> {
    Ok(runtime_root()?.join("bin").join("java.exe"))
}

pub fn managed_java_ready() -> bool {
    managed_java_path()
        .ok()
        .filter(|java| java.is_file())
        .is_some_and(|java| validate_java_21(&java).is_ok())
}

pub fn ensure_managed_java() -> Result<PathBuf, String> {
    let java = managed_java_path()?;
    if java.is_file() && validate_java_21(&java).is_ok() {
        return Ok(java);
    }

    let release = latest_release()?;
    let root = app_data_root()?;
    let cache_dir = root.join("cache").join("java");
    fs::create_dir_all(&cache_dir).map_err(|e| e.to_string())?;
    ensure_safe_directory(&cache_dir, "managed Java cache directory")?;
    let archive = cache_dir.join("temurin-21-windows-x64-jre.zip");
    if !archive.is_file() || sha256_file(&archive)? != release.checksum {
        download_verified(&release.link, &release.checksum, &archive)?;
    }

    let target = runtime_root()?;
    let staging = root.join("runtimes").join("java-21.staging");
    if metadata_entry_exists(&staging)? {
        ensure_safe_directory(&staging, "managed Java staging directory")?;
        fs::remove_dir_all(&staging).map_err(|e| e.to_string())?;
    }
    if metadata_entry_exists(&target)? {
        ensure_safe_directory(&target, "managed Java runtime directory")?;
    }
    fs::create_dir_all(&staging).map_err(|e| e.to_string())?;
    extract_zip_stripping_root(&archive, &staging)?;
    let staged_java = staging.join("bin").join("java.exe");
    if !staged_java.is_file() {
        let _ = fs::remove_dir_all(&staging);
        return Err("Managed Java archive did not contain bin/java.exe".into());
    }
    ensure_regular_file(&staged_java, "managed Java executable")?;
    if let Err(error) = validate_java_21(&staged_java) {
        let _ = fs::remove_dir_all(&staging);
        return Err(error);
    }

    if metadata_entry_exists(&target)? {
        let backup = root.join("runtimes").join("java-21.previous");
        if metadata_entry_exists(&backup)? {
            ensure_safe_directory(&backup, "previous managed Java runtime directory")?;
            fs::remove_dir_all(&backup).map_err(|e| e.to_string())?;
        }
        fs::rename(&target, &backup).map_err(|e| e.to_string())?;
        match fs::rename(&staging, &target) {
            Ok(()) => {
                let _ = fs::remove_dir_all(backup);
            }
            Err(error) => {
                let _ = fs::rename(&backup, &target);
                return Err(error.to_string());
            }
        }
    } else {
        fs::rename(&staging, &target).map_err(|e| e.to_string())?;
    }
    Ok(target.join("bin").join("java.exe"))
}

fn latest_release() -> Result<JavaRelease, String> {
    let response = ureq::get(ASSETS_URL)
        .set("User-Agent", USER_AGENT)
        .call()
        .map_err(|e| format!("Adoptium Java lookup failed: {e}"))?;
    let value: Value = response.into_json().map_err(|e| e.to_string())?;
    let release = value
        .as_array()
        .and_then(|items| items.first())
        .ok_or_else(|| "No Temurin Java 21 release was returned".to_string())?;
    let binary = release
        .get("binaries")
        .and_then(Value::as_array)
        .and_then(|items| items.first())
        .ok_or_else(|| "Temurin Java binary metadata is missing".to_string())?;
    let package = binary
        .get("package")
        .ok_or_else(|| "Temurin package metadata is missing".to_string())?;
    let link = package
        .get("link")
        .and_then(Value::as_str)
        .ok_or_else(|| "Temurin package URL is missing".to_string())?
        .to_string();
    let checksum = package
        .get("checksum")
        .and_then(Value::as_str)
        .ok_or_else(|| "Temurin package checksum is missing".to_string())?
        .to_lowercase();
    Ok(JavaRelease { link, checksum })
}

fn runtime_root() -> Result<PathBuf, String> {
    Ok(app_data_root()?.join("runtimes").join("java-21"))
}

fn app_data_root() -> Result<PathBuf, String> {
    std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .map(|p| p.join("LazyBuilder"))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())
}

fn download_verified(url: &str, expected: &str, destination: &Path) -> Result<(), String> {
    let temp = destination.with_extension("download");
    remove_regular_file_if_exists(&temp, "managed Java download staging file")?;
    let response = ureq::get(url)
        .set("User-Agent", USER_AGENT)
        .call()
        .map_err(|e| format!("Temurin Java download failed: {e}"))?;
    if let Some(length) = response.header("Content-Length").and_then(|value| value.parse::<u64>().ok()) {
        if length > MAX_JAVA_ARCHIVE_BYTES {
            return Err(format!("Temurin Java archive is unexpectedly large ({length} bytes)"));
        }
    }
    let mut reader = response.into_reader().take(MAX_JAVA_ARCHIVE_BYTES.saturating_add(1));
    let mut output = OpenOptions::new().create_new(true).write(true).open(&temp).map_err(|e| e.to_string())?;
    let copied = std::io::copy(&mut reader, &mut output).map_err(|e| e.to_string())?;
    if copied > MAX_JAVA_ARCHIVE_BYTES {
        drop(output);
        let _ = fs::remove_file(&temp);
        return Err("Temurin Java download exceeded the maximum allowed archive size".into());
    }
    output.sync_all().map_err(|e| e.to_string())?;
    drop(output);
    let actual = sha256_file(&temp)?;
    if actual != expected {
        let _ = fs::remove_file(&temp);
        return Err("Temurin Java download failed SHA-256 verification".into());
    }
    replace_file(&temp, destination)
}

fn replace_file(source: &Path, destination: &Path) -> Result<(), String> {
    ensure_regular_file(source, "managed Java staging file")?;
    if metadata_entry_exists(destination)? {
        ensure_regular_file(destination, "managed Java archive")?;
        let previous = destination.with_extension("previous");
        remove_regular_file_if_exists(&previous, "previous managed Java archive")?;
        fs::rename(destination, &previous).map_err(|e| e.to_string())?;
        match fs::rename(source, destination) {
            Ok(()) => {
                let _ = fs::remove_file(previous);
                Ok(())
            }
            Err(error) => {
                let _ = fs::rename(&previous, destination);
                Err(error.to_string())
            }
        }
    } else {
        fs::rename(source, destination).map_err(|e| e.to_string())
    }
}

fn extract_zip_stripping_root(archive: &Path, target: &Path) -> Result<(), String> {
    ensure_regular_file(archive, "managed Java archive")?;
    ensure_safe_directory(target, "managed Java extraction directory")?;
    let file = fs::File::open(archive).map_err(|e| e.to_string())?;
    let mut zip = zip::ZipArchive::new(file).map_err(|e| e.to_string())?;
    if zip.len() > MAX_JAVA_ARCHIVE_ENTRIES {
        return Err(format!("Managed Java archive contains too many entries: {}", zip.len()));
    }
    let mut extracted_bytes = 0u64;
    for index in 0..zip.len() {
        let mut entry = zip.by_index(index).map_err(|e| e.to_string())?;
        extracted_bytes = extracted_bytes.saturating_add(entry.size());
        if extracted_bytes > MAX_JAVA_EXTRACTED_BYTES {
            return Err("Managed Java archive exceeds the maximum extracted size".into());
        }
        let enclosed = entry
            .enclosed_name()
            .ok_or_else(|| "Java archive contains an unsafe path".to_string())?;
        let mut components = enclosed.components();
        let _root = components.next();
        let relative: PathBuf = components.collect();
        if relative.as_os_str().is_empty() {
            continue;
        }
        let output = target.join(relative);
        if entry.is_dir() {
            fs::create_dir_all(&output).map_err(|e| e.to_string())?;
            ensure_safe_directory(&output, "managed Java extracted directory")?;
        } else {
            if let Some(parent) = output.parent() {
                fs::create_dir_all(parent).map_err(|e| e.to_string())?;
                ensure_safe_directory(parent, "managed Java extracted parent directory")?;
            }
            let mut out = OpenOptions::new().create_new(true).write(true).open(&output).map_err(|e| e.to_string())?;
            std::io::copy(&mut entry, &mut out).map_err(|e| e.to_string())?;
            out.sync_all().map_err(|e| e.to_string())?;
        }
    }
    Ok(())
}

fn metadata_entry_exists(path: &Path) -> Result<bool, String> {
    match fs::symlink_metadata(path) {
        Ok(_) => Ok(true),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(false),
        Err(error) => Err(error.to_string()),
    }
}

fn ensure_regular_file(path: &Path, label: &str) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path).map_err(|e| format!("Could not inspect {label}: {e}"))?;
    if metadata.file_type().is_symlink() || is_reparse_point(&metadata) || !metadata.file_type().is_file() {
        return Err(format!("LazyBuilder expected {label} to be a regular non-reparse file"));
    }
    Ok(())
}

fn ensure_safe_directory(path: &Path, label: &str) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path).map_err(|e| format!("Could not inspect {label}: {e}"))?;
    if metadata.file_type().is_symlink() || is_reparse_point(&metadata) || !metadata.file_type().is_dir() {
        return Err(format!("LazyBuilder expected {label} to be a directory without symbolic-link or reparse indirection"));
    }
    Ok(())
}

#[cfg(windows)]
fn is_reparse_point(metadata: &fs::Metadata) -> bool {
    metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0
}
#[cfg(not(windows))]
fn is_reparse_point(_metadata: &fs::Metadata) -> bool { false }

fn remove_regular_file_if_exists(path: &Path, label: &str) -> Result<(), String> {
    match fs::symlink_metadata(path) {
        Ok(_) => {
            ensure_regular_file(path, label)?;
            fs::remove_file(path).map_err(|e| format!("Could not remove {label}: {e}"))
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(format!("Could not inspect {label}: {error}")),
    }
}

fn validate_java_21(java: &Path) -> Result<(), String> {
    ensure_regular_file(java, "managed Java executable")?;
    let mut command = Command::new(java);
    hide_windows_console(&mut command);
    let output = command
        .arg("-version")
        .output()
        .map_err(|e| e.to_string())?;
    let text = format!(
        "{}{}",
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
    if !text.contains(&format!("\"{JAVA_MAJOR}")) {
        return Err(format!("Managed runtime is not Java {JAVA_MAJOR}: {text}"));
    }
    Ok(())
}

fn hide_windows_console(command: &mut Command) {
    #[cfg(windows)]
    {
        command.creation_flags(CREATE_NO_WINDOW);
    }
}

fn sha256_file(path: &Path) -> Result<String, String> {
    ensure_regular_file(path, "managed Java archive")?;
    let mut file = fs::File::open(path).map_err(|e| e.to_string())?;
    let mut digest = Sha256::new();
    let mut buffer = [0u8; 64 * 1024];
    loop {
        let count = file.read(&mut buffer).map_err(|e| e.to_string())?;
        if count == 0 {
            break;
        }
        digest.update(&buffer[..count]);
    }
    Ok(format!("{:x}", digest.finalize()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn managed_java_bounds_are_finite() {
        assert!(MAX_JAVA_ARCHIVE_BYTES > 0);
        assert!(MAX_JAVA_ARCHIVE_ENTRIES > 0);
        assert!(MAX_JAVA_EXTRACTED_BYTES >= MAX_JAVA_ARCHIVE_BYTES);
    }

    #[test]
    fn user_agent_tracks_launcher_version() {
        assert!(USER_AGENT.ends_with(env!("CARGO_PKG_VERSION")));
    }
}
