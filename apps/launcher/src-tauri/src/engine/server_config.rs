use crate::engine::paths;
use serde::{Deserialize, Serialize};
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
#[cfg(windows)]
use std::os::windows::fs::MetadataExt;

#[cfg(windows)]
const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x00000400;

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct ServerConfig {
    pub java_path: String,
    pub server_directory: String,
    pub paper_jar: String,
    pub min_memory_mb: u64,
    pub max_memory_mb: u64,
    pub graceful_stop_timeout_seconds: u64,
    pub startup_timeout_seconds: u64,
}

impl Default for ServerConfig {
    fn default() -> Self {
        Self {
            java_path: String::new(),
            server_directory: "server".into(),
            paper_jar: "paper.jar".into(),
            min_memory_mb: 1024,
            max_memory_mb: 2048,
            graceful_stop_timeout_seconds: 30,
            startup_timeout_seconds: 90,
        }
    }
}

pub fn load() -> Result<ServerConfig, String> {
    let path = config_path()?;
    recover_atomic_file(&path)?;
    if !path.is_file() {
        let legacy = legacy_config_path()?;
        let config = if legacy.is_file() {
            read_from(&legacy)?
        } else {
            ServerConfig::default()
        };
        save(&config)?;
        return Ok(config);
    }
    read_from(&path)
}

pub fn save(config: &ServerConfig) -> Result<(), String> {
    validate(config)?;
    let path = config_path()?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    let text = serde_json::to_string_pretty(config).map_err(|error| error.to_string())?;
    let temporary = path.with_extension("json.tmp");
    write_staging_file(&temporary, text.as_bytes())?;
    replace_file(&temporary, &path)
}

pub fn ensure_java_path(java: &Path) -> Result<ServerConfig, String> {
    let mut config = load()?;
    if config.java_path.trim().is_empty() || !Path::new(&config.java_path).is_file() {
        config.java_path = java.display().to_string();
        save(&config)?;
    }
    Ok(config)
}

fn read_from(path: &Path) -> Result<ServerConfig, String> {
    ensure_regular_metadata_file(path, "server configuration")?;
    let text = fs::read_to_string(path).map_err(|error| error.to_string())?;
    let config: ServerConfig = serde_json::from_str(&text).map_err(|error| error.to_string())?;
    validate(&config)?;
    Ok(config)
}

fn validate(config: &ServerConfig) -> Result<(), String> {
    if config.min_memory_mb < 256 {
        return Err("minMemoryMb must be at least 256 MB".into());
    }
    if config.max_memory_mb < config.min_memory_mb {
        return Err("maxMemoryMb must be >= minMemoryMb".into());
    }
    if config.graceful_stop_timeout_seconds < 5 {
        return Err("gracefulStopTimeoutSeconds must be at least 5".into());
    }
    if config.startup_timeout_seconds < 10 {
        return Err("startupTimeoutSeconds must be at least 10".into());
    }
    paths::safe_relative_path(&config.server_directory, "serverDirectory")?;
    paths::safe_file_name(&config.paper_jar, "paperJar")?;
    Ok(())
}

fn config_path() -> Result<PathBuf, String> {
    Ok(paths::lazybuilder_config_dir()?.join("server-manager.json"))
}

fn legacy_config_path() -> Result<PathBuf, String> {
    Ok(paths::lazybuilder_tools_dir()?.join("server-manager.json"))
}

fn write_staging_file(path: &Path, bytes: &[u8]) -> Result<(), String> {
    if path.exists() {
        ensure_regular_metadata_file(path, "server configuration staging file")?;
        fs::remove_file(path).map_err(|error| format!("Could not clear stale server configuration staging file: {error}"))?;
    }
    let mut file = OpenOptions::new()
        .create_new(true)
        .write(true)
        .open(path)
        .map_err(|error| format!("Could not create server configuration staging file: {error}"))?;
    file.write_all(bytes).map_err(|error| format!("Could not write server configuration staging file: {error}"))?;
    file.sync_all().map_err(|error| format!("Could not flush server configuration staging file: {error}"))
}

fn recover_atomic_file(destination: &Path) -> Result<(), String> {
    let previous = destination.with_extension("json.previous");
    let temporary = destination.with_extension("json.tmp");

    if destination.exists() {
        ensure_regular_metadata_file(destination, "server configuration")?;
        remove_stale_metadata_file(&previous, "previous server configuration")?;
        remove_stale_metadata_file(&temporary, "server configuration staging file")?;
        return Ok(());
    }

    if previous.exists() {
        ensure_regular_metadata_file(&previous, "previous server configuration")?;
        fs::rename(&previous, destination)
            .map_err(|error| format!("Could not restore previous server configuration: {error}"))?;
        remove_stale_metadata_file(&temporary, "server configuration staging file")?;
        return Ok(());
    }

    if temporary.exists() {
        ensure_regular_metadata_file(&temporary, "server configuration staging file")?;
        fs::rename(&temporary, destination)
            .map_err(|error| format!("Could not publish recovered server configuration: {error}"))?;
    }
    Ok(())
}

fn replace_file(source: &Path, destination: &Path) -> Result<(), String> {
    ensure_regular_metadata_file(source, "server configuration staging file")?;
    if destination.exists() {
        ensure_regular_metadata_file(destination, "server configuration")?;
        let backup = destination.with_extension("json.previous");
        remove_stale_metadata_file(&backup, "previous server configuration")?;
        fs::rename(destination, &backup).map_err(|error| error.to_string())?;
        match fs::rename(source, destination) {
            Ok(()) => {
                let _ = fs::remove_file(backup);
                Ok(())
            }
            Err(error) => {
                match fs::rename(&backup, destination) {
                    Ok(()) => Err(format!("Could not publish server configuration; previous configuration was restored: {error}")),
                    Err(rollback_error) => Err(format!(
                        "Could not publish server configuration ({error}) and could not restore the previous configuration ({rollback_error}). Recovery files were preserved."
                    )),
                }
            }
        }
    } else {
        fs::rename(source, destination).map_err(|error| error.to_string())
    }
}

fn remove_stale_metadata_file(path: &Path, label: &str) -> Result<(), String> {
    if !path.exists() {
        return Ok(());
    }
    ensure_regular_metadata_file(path, label)?;
    fs::remove_file(path).map_err(|error| format!("Could not remove stale {label}: {error}"))
}

fn ensure_regular_metadata_file(path: &Path, label: &str) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path).map_err(|error| format!("Could not inspect {label}: {error}"))?;
    if metadata.file_type().is_symlink() {
        return Err(format!("LazyBuilder refused a symbolic link as {label}"));
    }
    #[cfg(windows)]
    if metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0 {
        return Err(format!("LazyBuilder refused a Windows reparse point as {label}"));
    }
    if !metadata.file_type().is_file() {
        return Err(format!("LazyBuilder expected {label} to be a regular file"));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicU64, Ordering};

    static NEXT_TEST: AtomicU64 = AtomicU64::new(1);

    fn temp_config_path() -> PathBuf {
        let sequence = NEXT_TEST.fetch_add(1, Ordering::Relaxed);
        let directory = std::env::temp_dir().join(format!("lazybuilder-server-config-test-{}-{sequence}", std::process::id()));
        fs::create_dir_all(&directory).unwrap();
        directory.join("server-manager.json")
    }

    #[test]
    fn interrupted_publish_prefers_previous_committed_config() {
        let path = temp_config_path();
        let previous = path.with_extension("json.previous");
        let temporary = path.with_extension("json.tmp");
        fs::write(&previous, b"previous").unwrap();
        fs::write(&temporary, b"incoming").unwrap();

        recover_atomic_file(&path).unwrap();
        assert_eq!(fs::read_to_string(&path).unwrap(), "previous");
        assert!(!temporary.exists());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn staging_config_recovers_when_no_committed_copy_exists() {
        let path = temp_config_path();
        let temporary = path.with_extension("json.tmp");
        fs::write(&temporary, b"incoming").unwrap();

        recover_atomic_file(&path).unwrap();
        assert_eq!(fs::read_to_string(&path).unwrap(), "incoming");
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }
}
