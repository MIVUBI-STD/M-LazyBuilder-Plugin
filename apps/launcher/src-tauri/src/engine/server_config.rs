use crate::engine::{paths, persistence};
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};
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

const SERVER_CONFIG_LABEL: &str = "server configuration";

pub fn load() -> Result<ServerConfig, String> {
    let path = config_path()?;
    persistence::recover_atomic_file(&path, SERVER_CONFIG_LABEL)?;
    if !persistence::metadata_entry_exists(&path, SERVER_CONFIG_LABEL)? {
        let legacy = legacy_config_path()?;
        let config = if persistence::metadata_entry_exists(&legacy, "legacy server configuration")? {
            read_from(&legacy)?
        } else {
            ServerConfig::default()
        };
        save(&config)?;
        return Ok(config);
    }
    let config = read_from(&path)?;
    persistence::cleanup_recovery_files(&path, SERVER_CONFIG_LABEL)?;
    Ok(config)
}

pub fn save(config: &ServerConfig) -> Result<(), String> {
    validate(config)?;
    let path = config_path()?;
    persistence::write_json_atomically(&path, config, SERVER_CONFIG_LABEL)
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
    let config: ServerConfig = persistence::read_json(path, SERVER_CONFIG_LABEL)?;
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

        persistence::recover_atomic_file(&path, SERVER_CONFIG_LABEL).unwrap();
        assert_eq!(fs::read_to_string(&path).unwrap(), "previous");
        assert!(temporary.exists());
        persistence::cleanup_recovery_files(&path, SERVER_CONFIG_LABEL).unwrap();
        assert!(!temporary.exists());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn staging_config_recovers_when_no_committed_copy_exists() {
        let path = temp_config_path();
        let temporary = path.with_extension("json.tmp");
        fs::write(&temporary, b"incoming").unwrap();

        persistence::recover_atomic_file(&path, SERVER_CONFIG_LABEL).unwrap();
        assert_eq!(fs::read_to_string(&path).unwrap(), "incoming");
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn malformed_main_config_preserves_recovery_evidence() {
        let path = temp_config_path();
        let previous = path.with_extension("json.previous");
        let temporary = path.with_extension("json.tmp");
        fs::write(&path, b"not-json").unwrap();
        fs::write(&previous, b"previous").unwrap();
        fs::write(&temporary, b"incoming").unwrap();
        persistence::recover_atomic_file(&path, SERVER_CONFIG_LABEL).unwrap();
        assert!(read_from(&path).is_err());
        assert!(previous.exists());
        assert!(temporary.exists());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }
}
