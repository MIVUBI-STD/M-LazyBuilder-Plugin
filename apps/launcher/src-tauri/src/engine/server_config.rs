use crate::engine::paths;
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

pub fn load() -> Result<ServerConfig, String> {
    let path = config_path()?;
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
    fs::write(&temporary, text).map_err(|error| error.to_string())?;
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

fn replace_file(source: &Path, destination: &Path) -> Result<(), String> {
    if destination.exists() {
        let backup = destination.with_extension("json.previous");
        let _ = fs::remove_file(&backup);
        fs::rename(destination, &backup).map_err(|error| error.to_string())?;
        match fs::rename(source, destination) {
            Ok(()) => {
                let _ = fs::remove_file(backup);
                Ok(())
            }
            Err(error) => {
                let _ = fs::rename(&backup, destination);
                Err(error.to_string())
            }
        }
    } else {
        fs::rename(source, destination).map_err(|error| error.to_string())
    }
}
