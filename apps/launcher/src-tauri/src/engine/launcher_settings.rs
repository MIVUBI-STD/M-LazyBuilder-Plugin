use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};

const SETTINGS_SCHEMA_VERSION: u32 = 1;

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct LauncherSettings {
    pub schema_version: u32,
    pub remember_last_server: bool,
    pub confirm_close_while_server_running: bool,
    pub auto_check_updates: bool,
    pub update_channel: String,
}

impl Default for LauncherSettings {
    fn default() -> Self {
        Self {
            schema_version: SETTINGS_SCHEMA_VERSION,
            remember_last_server: true,
            confirm_close_while_server_running: true,
            auto_check_updates: true,
            update_channel: "stable".into(),
        }
    }
}

pub fn initialize() -> Result<LauncherSettings, String> { load() }

pub fn load() -> Result<LauncherSettings, String> {
    let path = settings_path()?;
    if !path.is_file() {
        let settings = LauncherSettings::default();
        save(&settings)?;
        return Ok(settings);
    }

    let text = fs::read_to_string(&path)
        .map_err(|error| format!("Could not read launcher settings: {error}"))?;
    let mut value: serde_json::Value = serde_json::from_str(&text)
        .map_err(|error| format!("Could not parse launcher settings: {error}"))?;
    let previous_schema = value.get("schemaVersion").and_then(|value| value.as_u64()).unwrap_or(0);
    migrate(&mut value)?;
    let mut settings: LauncherSettings = serde_json::from_value(value)
        .map_err(|error| format!("Could not decode launcher settings: {error}"))?;
    settings.update_channel = settings.update_channel.trim().to_ascii_lowercase();
    validate(&settings)?;
    if previous_schema != SETTINGS_SCHEMA_VERSION as u64 {
        return save(&settings);
    }
    Ok(settings)
}

pub fn save(settings: &LauncherSettings) -> Result<LauncherSettings, String> {
    let mut normalized = settings.clone();
    normalized.schema_version = SETTINGS_SCHEMA_VERSION;
    normalized.update_channel = normalized.update_channel.trim().to_ascii_lowercase();
    validate(&normalized)?;

    let path = settings_path()?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    let temporary = path.with_extension("json.tmp");
    let text = serde_json::to_string_pretty(&normalized).map_err(|error| error.to_string())?;
    fs::write(&temporary, text).map_err(|error| error.to_string())?;
    replace_file(&temporary, &path)?;
    Ok(normalized)
}

fn migrate(value: &mut serde_json::Value) -> Result<(), String> {
    let schema = value.get("schemaVersion").and_then(|value| value.as_u64()).unwrap_or(0);
    if schema > SETTINGS_SCHEMA_VERSION as u64 {
        return Err("Launcher settings schema is newer than this LazyBuilder version".into());
    }
    let Some(object) = value.as_object_mut() else {
        return Err("Launcher settings must be a JSON object".into());
    };
    object.insert("schemaVersion".into(), serde_json::json!(SETTINGS_SCHEMA_VERSION));
    Ok(())
}

fn validate(settings: &LauncherSettings) -> Result<(), String> {
    match settings.update_channel.as_str() {
        "stable" | "preview" => Ok(()),
        _ => Err("updateChannel must be stable or preview".into()),
    }
}

fn settings_path() -> Result<PathBuf, String> {
    let base = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())?;
    Ok(base.join("LazyBuilder").join("settings.json"))
}

fn replace_file(source: &Path, destination: &Path) -> Result<(), String> {
    if destination.exists() {
        let backup = destination.with_extension("json.previous");
        let _ = fs::remove_file(&backup);
        fs::rename(destination, &backup).map_err(|error| error.to_string())?;
        match fs::rename(source, destination) {
            Ok(()) => { let _ = fs::remove_file(backup); Ok(()) }
            Err(error) => { let _ = fs::rename(&backup, destination); Err(error.to_string()) }
        }
    } else {
        fs::rename(source, destination).map_err(|error| error.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn defaults_use_stable_update_channel() {
        let settings = LauncherSettings::default();
        assert_eq!(settings.update_channel, "stable");
        assert!(settings.auto_check_updates);
    }

    #[test]
    fn invalid_update_channel_is_rejected() {
        let mut settings = LauncherSettings::default();
        settings.update_channel = "other".into();
        assert!(validate(&settings).is_err());
    }
}
