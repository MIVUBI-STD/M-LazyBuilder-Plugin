use crate::engine::paths;
use std::fs;
use std::path::Path;
use std::time::SystemTime;

const MAX_RETAINED_PLUGIN_JAR_BACKUPS: usize = 20;

/// Prunes only timestamped/historical plugin JAR backups.
///
/// Core `*.previous` rollback snapshots and quarantined plugin-data directories
/// are intentionally preserved. World backups are owned by World-Manager and
/// are never touched by this maintenance path.
pub fn maintain_plugin_backups() -> Result<(), String> {
    let root = paths::lazybuilder_tools_dir()?.join("plugin-backups");
    maintain_plugin_backups_in(&root)
}

fn maintain_plugin_backups_in(root: &Path) -> Result<(), String> {
    if !root.is_dir() {
        return Ok(());
    }

    let mut historical = Vec::new();
    for entry in fs::read_dir(root).map_err(|error| error.to_string())? {
        let entry = entry.map_err(|error| error.to_string())?;
        let file_type = entry.file_type().map_err(|error| error.to_string())?;
        if !file_type.is_file() {
            continue;
        }

        let path = entry.path();
        let Some(name) = path.file_name().and_then(|value| value.to_str()) else {
            continue;
        };
        if !is_historical_plugin_jar(name) {
            continue;
        }

        let modified = entry
            .metadata()
            .and_then(|metadata| metadata.modified())
            .unwrap_or(SystemTime::UNIX_EPOCH);
        historical.push((modified, path));
    }

    historical.sort_by(|left, right| {
        right.0.cmp(&left.0).then_with(|| right.1.cmp(&left.1))
    });
    for (_, path) in historical.into_iter().skip(MAX_RETAINED_PLUGIN_JAR_BACKUPS) {
        let _ = fs::remove_file(path);
    }
    Ok(())
}

fn is_historical_plugin_jar(name: &str) -> bool {
    name.to_ascii_lowercase().ends_with(".jar") && !name.ends_with(".previous")
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn test_root() -> PathBuf {
        let nonce = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        std::env::temp_dir().join(format!(
            "lazybuilder-plugin-backup-retention-{}-{nonce}",
            std::process::id()
        ))
    }

    #[test]
    fn prunes_only_historical_jar_backups() {
        let root = test_root();
        fs::create_dir_all(&root).unwrap();

        for index in 0..(MAX_RETAINED_PLUGIN_JAR_BACKUPS + 5) {
            fs::write(root.join(format!("plugin-{index:02}.jar")), b"backup").unwrap();
        }
        let core_previous = root.join("World-Manager-0.1.0-SNAPSHOT.jar.previous");
        fs::write(&core_previous, b"core rollback").unwrap();
        let quarantine = root.join("plugin-data-removed-123");
        fs::create_dir_all(&quarantine).unwrap();
        fs::write(quarantine.join("config.yml"), b"preserve").unwrap();

        maintain_plugin_backups_in(&root).unwrap();

        let historical_count = fs::read_dir(&root)
            .unwrap()
            .filter_map(Result::ok)
            .filter(|entry| {
                entry
                    .file_name()
                    .to_str()
                    .map(is_historical_plugin_jar)
                    .unwrap_or(false)
            })
            .count();
        assert_eq!(historical_count, MAX_RETAINED_PLUGIN_JAR_BACKUPS);
        assert!(core_previous.is_file());
        assert!(quarantine.join("config.yml").is_file());

        let _ = fs::remove_dir_all(root);
    }
}
