use serde_json::Value;
use sha2::{Digest, Sha256};
use std::fs;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};

const JAVA_MAJOR: u32 = 21;
const USER_AGENT: &str = "LazyBuilder/0.1.0";
const ASSETS_URL: &str = "https://api.adoptium.net/v3/assets/feature_releases/21/ga?architecture=x64&heap_size=normal&image_type=jre&jvm_impl=hotspot&os=windows&page=0&page_size=1&project=jdk&sort_method=DEFAULT&sort_order=DESC&vendor=eclipse";

#[derive(Clone, Debug)]
struct JavaRelease {
    link: String,
    checksum: String,
}

pub fn managed_java_path() -> Result<PathBuf, String> {
    Ok(runtime_root()?.join("bin").join("java.exe"))
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
    let archive = cache_dir.join("temurin-21-windows-x64-jre.zip");
    if !archive.is_file() || sha256_file(&archive)? != release.checksum {
        download_verified(&release.link, &release.checksum, &archive)?;
    }

    let target = runtime_root()?;
    let staging = root.join("runtimes").join("java-21.staging");
    if staging.exists() {
        fs::remove_dir_all(&staging).map_err(|e| e.to_string())?;
    }
    fs::create_dir_all(&staging).map_err(|e| e.to_string())?;
    extract_zip_stripping_root(&archive, &staging)?;
    let staged_java = staging.join("bin").join("java.exe");
    if !staged_java.is_file() {
        let _ = fs::remove_dir_all(&staging);
        return Err("Managed Java archive did not contain bin/java.exe".into());
    }
    if let Err(error) = validate_java_21(&staged_java) {
        let _ = fs::remove_dir_all(&staging);
        return Err(error);
    }

    if target.exists() {
        let backup = root.join("runtimes").join("java-21.previous");
        let _ = fs::remove_dir_all(&backup);
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
    let response = ureq::get(url)
        .set("User-Agent", USER_AGENT)
        .call()
        .map_err(|e| format!("Temurin Java download failed: {e}"))?;
    let mut reader = response.into_reader();
    let mut output = fs::File::create(&temp).map_err(|e| e.to_string())?;
    std::io::copy(&mut reader, &mut output).map_err(|e| e.to_string())?;
    output.flush().map_err(|e| e.to_string())?;
    let actual = sha256_file(&temp)?;
    if actual != expected {
        let _ = fs::remove_file(&temp);
        return Err("Temurin Java download failed SHA-256 verification".into());
    }
    replace_file(&temp, destination)
}

fn replace_file(source: &Path, destination: &Path) -> Result<(), String> {
    if destination.exists() {
        let previous = destination.with_extension("previous");
        let _ = fs::remove_file(&previous);
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
    let file = fs::File::open(archive).map_err(|e| e.to_string())?;
    let mut zip = zip::ZipArchive::new(file).map_err(|e| e.to_string())?;
    for index in 0..zip.len() {
        let mut entry = zip.by_index(index).map_err(|e| e.to_string())?;
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
        } else {
            if let Some(parent) = output.parent() {
                fs::create_dir_all(parent).map_err(|e| e.to_string())?;
            }
            let mut out = fs::File::create(&output).map_err(|e| e.to_string())?;
            std::io::copy(&mut entry, &mut out).map_err(|e| e.to_string())?;
        }
    }
    Ok(())
}

fn validate_java_21(java: &Path) -> Result<(), String> {
    let output = std::process::Command::new(java)
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

fn sha256_file(path: &Path) -> Result<String, String> {
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
