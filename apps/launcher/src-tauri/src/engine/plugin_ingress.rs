use std::fs::{self, File, OpenOptions};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use zip::ZipArchive;

const MAX_PLUGIN_JAR_BYTES: u64 = 1024 * 1024 * 1024;
const MAX_PLUGIN_ARCHIVE_ENTRIES: usize = 100_000;
const MAX_PLUGIN_METADATA_BYTES: u64 = 1024 * 1024;
const INGRESS_FILE_PREFIX: &str = "lazybuilder-plugin-ingress-";
static NEXT_INGRESS_ID: AtomicU64 = AtomicU64::new(1);

pub struct PluginIngressLease {
    path: PathBuf,
}

impl PluginIngressLease {
    pub fn path(&self) -> &Path { &self.path }
}

impl Drop for PluginIngressLease {
    fn drop(&mut self) {
        let _ = fs::remove_file(&self.path);
    }
}

pub fn stage_selected_jar(source: &Path) -> Result<PluginIngressLease, String> {
    validate_source_path(source)?;
    let directory = ingress_directory()?;
    prepare_ingress_directory(&directory)?;

    let sequence = NEXT_INGRESS_ID.fetch_add(1, Ordering::Relaxed);
    let staged = directory.join(format!("{INGRESS_FILE_PREFIX}{}-{sequence}.jar", std::process::id()));
    let mut input = File::open(source).map_err(|error| format!("Could not open selected plugin JAR: {error}"))?;
    let mut output = OpenOptions::new()
        .create_new(true)
        .write(true)
        .open(&staged)
        .map_err(|error| format!("Could not create plugin ingress snapshot: {error}"))?;

    let result = (|| -> Result<(), String> {
        let mut copied = 0u64;
        let mut buffer = [0u8; 64 * 1024];
        loop {
            let count = input.read(&mut buffer).map_err(|error| format!("Could not read selected plugin JAR: {error}"))?;
            if count == 0 { break; }
            copied = copied.saturating_add(count as u64);
            if copied > MAX_PLUGIN_JAR_BYTES {
                return Err(format!("Selected plugin JAR exceeds the {} MB safety limit.", to_mb(MAX_PLUGIN_JAR_BYTES)));
            }
            output.write_all(&buffer[..count]).map_err(|error| format!("Could not write plugin ingress snapshot: {error}"))?;
        }
        if copied == 0 { return Err("Selected plugin JAR is empty.".into()); }
        output.sync_all().map_err(|error| format!("Could not flush plugin ingress snapshot: {error}"))?;
        drop(output);
        validate_snapshot(&staged)
    })();

    if let Err(error) = result {
        let _ = fs::remove_file(&staged);
        return Err(error);
    }
    Ok(PluginIngressLease { path: staged })
}

pub fn recover_stale_ingress() -> Result<u32, String> {
    let directory = ingress_directory()?;
    if !directory.exists() { return Ok(0); }
    prepare_ingress_directory(&directory)?;
    let mut removed = 0u32;
    for entry in fs::read_dir(&directory).map_err(|error| format!("Could not inspect plugin ingress cache: {error}"))? {
        let entry = entry.map_err(|error| error.to_string())?;
        let name = entry.file_name().to_string_lossy().to_string();
        if !name.starts_with(INGRESS_FILE_PREFIX) || !name.to_ascii_lowercase().ends_with(".jar") { continue; }
        let metadata = fs::symlink_metadata(entry.path()).map_err(|error| error.to_string())?;
        if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
            return Err(format!("Unsafe link or reparse point found in plugin ingress cache: {}", entry.path().display()));
        }
        if !metadata.file_type().is_file() {
            return Err(format!("Unexpected non-file plugin ingress artifact: {}", entry.path().display()));
        }
        fs::remove_file(entry.path()).map_err(|error| format!("Could not clean stale plugin ingress snapshot: {error}"))?;
        removed = removed.saturating_add(1);
    }
    Ok(removed)
}

fn validate_source_path(path: &Path) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path)
        .map_err(|error| format!("Could not inspect selected plugin JAR: {error}"))?;
    if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
        return Err("LazyBuilder refuses a symbolic link or Windows reparse point as a plugin JAR source.".into());
    }
    if !metadata.file_type().is_file() { return Err("Selected plugin JAR is not a regular file.".into()); }
    if path.extension().and_then(|value| value.to_str()).map(|value| value.eq_ignore_ascii_case("jar")) != Some(true) {
        return Err("Selected plugin file must be a .jar file.".into());
    }
    if metadata.len() == 0 { return Err("Selected plugin JAR is empty.".into()); }
    if metadata.len() > MAX_PLUGIN_JAR_BYTES {
        return Err(format!(
            "Selected plugin JAR is too large for safe inspection ({} MB; maximum {} MB).",
            to_mb(metadata.len()), to_mb(MAX_PLUGIN_JAR_BYTES),
        ));
    }
    Ok(())
}

fn validate_snapshot(path: &Path) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path).map_err(|error| error.to_string())?;
    if !metadata.file_type().is_file() || metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
        return Err("Plugin ingress snapshot is not a safe regular file.".into());
    }
    if metadata.len() == 0 || metadata.len() > MAX_PLUGIN_JAR_BYTES {
        return Err("Plugin ingress snapshot violates the bounded JAR size policy.".into());
    }

    let file = File::open(path).map_err(|error| format!("Could not open plugin ingress snapshot: {error}"))?;
    let mut archive = ZipArchive::new(file).map_err(|error| format!("Selected plugin JAR is not a valid ZIP/JAR archive: {error}"))?;
    if archive.len() > MAX_PLUGIN_ARCHIVE_ENTRIES {
        return Err(format!("Selected plugin JAR contains too many archive entries ({}; maximum {}).", archive.len(), MAX_PLUGIN_ARCHIVE_ENTRIES));
    }

    let mut plugin_metadata_size = None;
    let mut paper_metadata_size = None;
    for index in 0..archive.len() {
        let entry = archive.by_index(index).map_err(|error| format!("Could not inspect plugin archive entry: {error}"))?;
        match entry.name() {
            "plugin.yml" => plugin_metadata_size = Some(entry.size()),
            "paper-plugin.yml" => paper_metadata_size = Some(entry.size()),
            _ => {}
        }
    }
    let (metadata_name, metadata_size) = if let Some(size) = plugin_metadata_size {
        ("plugin.yml", size)
    } else if let Some(size) = paper_metadata_size {
        ("paper-plugin.yml", size)
    } else {
        return Err("JAR does not contain plugin.yml or paper-plugin.yml".into());
    };
    if metadata_size > MAX_PLUGIN_METADATA_BYTES {
        return Err(format!(
            "Plugin metadata {metadata_name} is too large for safe parsing ({metadata_size} bytes; maximum {MAX_PLUGIN_METADATA_BYTES} bytes)."
        ));
    }
    Ok(())
}

fn ingress_directory() -> Result<PathBuf, String> {
    let base = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())?;
    Ok(base.join("LazyBuilder").join("cache").join("plugin-ingress"))
}

fn prepare_ingress_directory(path: &Path) -> Result<(), String> {
    fs::create_dir_all(path).map_err(|error| format!("Could not prepare plugin ingress cache: {error}"))?;
    let metadata = fs::symlink_metadata(path).map_err(|error| error.to_string())?;
    if metadata.file_type().is_symlink() || is_reparse_point(&metadata) || !metadata.file_type().is_dir() {
        return Err("LazyBuilder plugin ingress cache is not a safe directory.".into());
    }
    Ok(())
}

fn to_mb(bytes: u64) -> u64 { bytes.saturating_add(1024 * 1024 - 1) / (1024 * 1024) }

#[cfg(windows)]
fn is_reparse_point(metadata: &fs::Metadata) -> bool {
    use std::os::windows::fs::MetadataExt;
    const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x0400;
    metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0
}

#[cfg(not(windows))]
fn is_reparse_point(_metadata: &fs::Metadata) -> bool { false }

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn plugin_ingress_limits_are_bounded() {
        assert_eq!(MAX_PLUGIN_JAR_BYTES, 1024 * 1024 * 1024);
        assert_eq!(MAX_PLUGIN_METADATA_BYTES, 1024 * 1024);
        assert_eq!(MAX_PLUGIN_ARCHIVE_ENTRIES, 100_000);
    }
}
