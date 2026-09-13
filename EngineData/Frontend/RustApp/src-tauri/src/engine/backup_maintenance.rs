use crate::engine::paths;
use std::fs;
use std::path::PathBuf;
use std::time::SystemTime;

const MAX_RETAINED_PLUGIN_JAR_BACKUPS: usize = 20;

/// Prunes only timestamped/historical plugin JAR backups.
///
/// Core `*.previous` rollback snapshots and quarantined plugin-data directories
/// are intentionally preserved. World backups are owned by World-Manager and
/// are never touched by this maintenance path.
pub fn maintain_plugin_backups() -> Result<(), String> {
    let root = paths::lazybuilder_tools_dir()?.join("plugin-backups");
    if !root.is_dir() {
        return Ok(());
    }

    let mut historical = Vec::new();
    for entry in fs::read_dir(&root).map_err(|error| error.to_string())? {
        let entry = entry.map_err(|error| error.to_string())?;
        let file_type = entry.file_type().map_err(|error| error.to_string())?;
        if !file_type.is_file() {
            continue;
        }

        let path = entry.path();
        let Some(name) = path.file_name().and_then(|value| value.to_str()) else {
            continue;
        };
        if !name.to_ascii_lowercase().ends_with(".jar") || name.ends_with(".previous") {
            continue;
        }

        let modified = entry
            .metadata()
            .and_then(|metadata| metadata.modified())
            .unwrap_or(SystemTime::UNIX_EPOCH);
        historical.push((modified, path));
    }

    historical.sort_by(|left, right| right.0.cmp(&left.0));
    for (_, path) in historical.into_iter().skip(MAX_RETAINED_PLUGIN_JAR_BACKUPS) {
        let _ = fs::remove_file(path);
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn retention_limit_is_bounded_but_not_zero() {
        assert!(MAX_RETAINED_PLUGIN_JAR_BACKUPS >= 5);
        assert!(MAX_RETAINED_PLUGIN_JAR_BACKUPS <= 50);
    }
}
