use serde::{Deserialize, Serialize};
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

const APP_DATA_SCHEMA_VERSION: u32 = 1;
const APP_ID: &str = "com.halokaryamedia.lazybuilder";

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AppDataMigrationReport {
    pub from_schema: u32,
    pub to_schema: u32,
    pub migrated: bool,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct AppDataManifest {
    schema_version: u32,
    app_id: String,
    updated_unix_seconds: u64,
}

pub fn initialize() -> Result<AppDataMigrationReport, String> {
    let root = app_data_root()?;
    fs::create_dir_all(&root).map_err(|error| format!("Could not prepare LazyBuilder application data: {error}"))?;
    let path = root.join("app-data.json");

    let from_schema = if path.is_file() {
        let manifest = read_manifest(&path)?;
        validate_identity(&manifest)?;
        manifest.schema_version
    } else {
        0
    };

    if from_schema > APP_DATA_SCHEMA_VERSION {
        return Err(format!(
            "LazyBuilder application data schema {from_schema} is newer than this Launcher supports ({APP_DATA_SCHEMA_VERSION}). Install a newer Launcher instead of opening this data with an older build."
        ));
    }

    let mut current = from_schema;
    while current < APP_DATA_SCHEMA_VERSION {
        current = migrate_one(&root, current)?;
    }

    let manifest = AppDataManifest {
        schema_version: APP_DATA_SCHEMA_VERSION,
        app_id: APP_ID.into(),
        updated_unix_seconds: now_unix_seconds(),
    };
    write_manifest(&path, &manifest)?;

    Ok(AppDataMigrationReport {
        from_schema,
        to_schema: APP_DATA_SCHEMA_VERSION,
        migrated: from_schema != APP_DATA_SCHEMA_VERSION,
    })
}

fn migrate_one(root: &Path, from_schema: u32) -> Result<u32, String> {
    match from_schema {
        0 => {
            // Schema 1 establishes the global application-data version gate only.
            // Existing subsystem files remain owned by their current authorities.
            // No user/server data is rewritten during this bootstrap migration.
            if !root.is_dir() {
                return Err("LazyBuilder application data root is unavailable during migration".into());
            }
            Ok(1)
        }
        other => Err(format!("No LazyBuilder application-data migration exists from schema {other}")),
    }
}

fn validate_identity(manifest: &AppDataManifest) -> Result<(), String> {
    if manifest.app_id != APP_ID {
        return Err(format!(
            "Application-data identity mismatch: expected {APP_ID}, found {}",
            manifest.app_id
        ));
    }
    Ok(())
}

fn read_manifest(path: &Path) -> Result<AppDataManifest, String> {
    let text = fs::read_to_string(path).map_err(|error| format!("Could not read LazyBuilder application-data manifest: {error}"))?;
    serde_json::from_str(&text).map_err(|error| format!("Could not parse LazyBuilder application-data manifest: {error}"))
}

fn write_manifest(path: &Path, manifest: &AppDataManifest) -> Result<(), String> {
    let incoming = path.with_extension("json.incoming");
    let previous = path.with_extension("json.previous");
    let text = serde_json::to_string_pretty(manifest).map_err(|error| error.to_string())?;

    {
        let mut file = OpenOptions::new().create(true).truncate(true).write(true).open(&incoming)
            .map_err(|error| format!("Could not write application-data manifest staging file: {error}"))?;
        file.write_all(text.as_bytes()).map_err(|error| error.to_string())?;
        file.sync_all().map_err(|error| format!("Could not flush application-data manifest: {error}"))?;
    }

    if path.exists() {
        if previous.exists() {
            fs::remove_file(&previous).map_err(|error| format!("Could not clear stale previous application-data manifest: {error}"))?;
        }
        fs::rename(path, &previous).map_err(|error| format!("Could not preserve previous application-data manifest: {error}"))?;
        match fs::rename(&incoming, path) {
            Ok(()) => {
                let _ = fs::remove_file(previous);
                Ok(())
            }
            Err(publish_error) => match fs::rename(&previous, path) {
                Ok(()) => Err(format!("Could not publish application-data manifest; previous manifest was restored: {publish_error}")),
                Err(rollback_error) => Err(format!(
                    "Could not publish application-data manifest ({publish_error}) and could not restore the previous manifest ({rollback_error})."
                )),
            },
        }
    } else {
        fs::rename(incoming, path).map_err(|error| format!("Could not publish application-data manifest: {error}"))
    }
}

fn app_data_root() -> Result<PathBuf, String> {
    std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .map(|path| path.join("LazyBuilder"))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())
}

fn now_unix_seconds() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|value| value.as_secs()).unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bootstrap_migration_does_not_require_rewriting_subsystem_data() {
        let root = std::env::temp_dir().join(format!("lazybuilder-app-data-migration-test-{}", std::process::id()));
        fs::create_dir_all(&root).unwrap();
        assert_eq!(migrate_one(&root, 0).unwrap(), 1);
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn manifest_identity_is_strict() {
        let valid = AppDataManifest { schema_version: 1, app_id: APP_ID.into(), updated_unix_seconds: 0 };
        assert!(validate_identity(&valid).is_ok());
        let invalid = AppDataManifest { schema_version: 1, app_id: "other.app".into(), updated_unix_seconds: 0 };
        assert!(validate_identity(&invalid).is_err());
    }
}
