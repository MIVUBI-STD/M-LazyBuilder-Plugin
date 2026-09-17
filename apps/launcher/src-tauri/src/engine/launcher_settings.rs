use crate::engine::persistence;
use serde::{Deserialize, Serialize};
use std::path::PathBuf;

const SETTINGS_SCHEMA_VERSION: u32 = 2;
const SETTINGS_LABEL: &str = "launcher settings";

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
            auto_check_updates: false,
            update_channel: "stable".into(),
        }
    }
}

pub fn initialize() -> Result<LauncherSettings, String> { load() }

pub fn load() -> Result<LauncherSettings, String> {
    let path = settings_path()?;
    persistence::recover_atomic_file(&path, SETTINGS_LABEL)?;
    if !persistence::metadata_entry_exists(&path, SETTINGS_LABEL)? {
        let settings = LauncherSettings::default();
        save(&settings)?;
        return Ok(settings);
    }

    let mut value: serde_json::Value = persistence::read_json(&path, SETTINGS_LABEL)?;
    let previous_schema = value.get("schemaVersion").and_then(|value| value.as_u64()).unwrap_or(0);
    migrate(&mut value)?;
    let mut settings: LauncherSettings = serde_json::from_value(value)
        .map_err(|error| format!("Could not decode launcher settings: {error}"))?;
    settings.update_channel = settings.update_channel.trim().to_ascii_lowercase();
    validate(&settings)?;

    if previous_schema != SETTINGS_SCHEMA_VERSION as u64 {
        return save(&settings);
    }

    persistence::cleanup_recovery_files(&path, SETTINGS_LABEL)?;
    Ok(settings)
}

pub fn save(settings: &LauncherSettings) -> Result<LauncherSettings, String> {
    let mut normalized = settings.clone();
    normalized.schema_version = SETTINGS_SCHEMA_VERSION;
    normalized.update_channel = normalized.update_channel.trim().to_ascii_lowercase();
    validate(&normalized)?;

    let path = settings_path()?;
    persistence::write_json_atomically(&path, &normalized, SETTINGS_LABEL)?;
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
    if schema < 2 {
        object.insert("autoCheckUpdates".into(), serde_json::json!(false));
        object.insert("updateChannel".into(), serde_json::json!("stable"));
    }
    object.insert("schemaVersion".into(), serde_json::json!(SETTINGS_SCHEMA_VERSION));
    Ok(())
}

fn validate(settings: &LauncherSettings) -> Result<(), String> {
    if settings.update_channel != "stable" {
        return Err("Only the stable update channel is available until the in-app updater runtime is configured".into());
    }
    if settings.auto_check_updates {
        return Err("Automatic update checks are unavailable until the signed in-app updater runtime is configured".into());
    }
    Ok(())
}

fn settings_path() -> Result<PathBuf, String> {
    let base = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())?;
    Ok(base.join("LazyBuilder").join("settings.json"))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::sync::atomic::{AtomicU64, Ordering};

    static NEXT_TEST: AtomicU64 = AtomicU64::new(1);

    fn temp_settings_path() -> PathBuf {
        let sequence = NEXT_TEST.fetch_add(1, Ordering::Relaxed);
        let directory = std::env::temp_dir().join(format!("lazybuilder-settings-test-{}-{sequence}", std::process::id()));
        fs::create_dir_all(&directory).unwrap();
        directory.join("settings.json")
    }

    #[test]
    fn defaults_fail_closed_until_updater_runtime_exists() {
        let settings = LauncherSettings::default();
        assert_eq!(settings.update_channel, "stable");
        assert!(!settings.auto_check_updates);
    }

    #[test]
    fn legacy_update_preferences_migrate_to_supported_state() {
        let mut value = serde_json::json!({
            "schemaVersion": 1,
            "rememberLastServer": true,
            "confirmCloseWhileServerRunning": true,
            "autoCheckUpdates": true,
            "updateChannel": "preview"
        });
        migrate(&mut value).unwrap();
        assert_eq!(value["schemaVersion"], SETTINGS_SCHEMA_VERSION);
        assert_eq!(value["autoCheckUpdates"], false);
        assert_eq!(value["updateChannel"], "stable");
    }

    #[test]
    fn unavailable_update_preferences_are_rejected() {
        let mut settings = LauncherSettings::default();
        settings.update_channel = "preview".into();
        assert!(validate(&settings).is_err());
        settings.update_channel = "stable".into();
        settings.auto_check_updates = true;
        assert!(validate(&settings).is_err());
    }

    #[test]
    fn interrupted_settings_publish_prefers_previous_committed_copy() {
        let path = temp_settings_path();
        let previous = path.with_extension("json.previous");
        let temporary = path.with_extension("json.tmp");
        fs::write(&previous, b"previous").unwrap();
        fs::write(&temporary, b"incoming").unwrap();
        persistence::recover_atomic_file(&path, SETTINGS_LABEL).unwrap();
        assert_eq!(fs::read_to_string(&path).unwrap(), "previous");
        assert!(temporary.exists());
        persistence::cleanup_recovery_files(&path, SETTINGS_LABEL).unwrap();
        assert!(!temporary.exists());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn settings_staging_recovers_when_no_committed_copy_exists() {
        let path = temp_settings_path();
        let temporary = path.with_extension("json.tmp");
        fs::write(&temporary, b"incoming").unwrap();
        persistence::recover_atomic_file(&path, SETTINGS_LABEL).unwrap();
        assert_eq!(fs::read_to_string(&path).unwrap(), "incoming");
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn malformed_main_settings_preserve_recovery_evidence() {
        let path = temp_settings_path();
        let previous = path.with_extension("json.previous");
        let temporary = path.with_extension("json.tmp");
        fs::write(&path, b"not-json").unwrap();
        fs::write(&previous, b"previous").unwrap();
        fs::write(&temporary, b"incoming").unwrap();
        persistence::recover_atomic_file(&path, SETTINGS_LABEL).unwrap();
        assert!(serde_json::from_str::<serde_json::Value>(&fs::read_to_string(&path).unwrap()).is_err());
        assert!(previous.exists());
        assert!(temporary.exists());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }
}
