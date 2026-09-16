use crate::engine::workspace_registry;
use serde::Serialize;
use std::path::{Path, PathBuf};
use sysinfo::Disks;

const CRITICAL_AVAILABLE_BYTES: u64 = 512 * 1024 * 1024;
const WARNING_MIN_AVAILABLE_BYTES: u64 = 2 * 1024 * 1024 * 1024;

#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum StoragePressure { Normal, Warning, Critical, Unknown }

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct WorkspaceStorageStatus {
    pub pressure: StoragePressure,
    pub total_bytes: Option<u64>,
    pub available_bytes: Option<u64>,
    pub warning_threshold_bytes: Option<u64>,
    pub critical_threshold_bytes: u64,
    pub mount_point: Option<String>,
    pub details: String,
}

pub fn inspect_workspace(workspace_id: &str) -> Result<WorkspaceStorageStatus, String> {
    let entry = workspace_registry::get(workspace_id)?;
    let root = PathBuf::from(&entry.path)
        .canonicalize()
        .map_err(|error| format!("Could not resolve server storage location: {error}"))?;
    inspect_path(&root)
}

fn inspect_path(path: &Path) -> Result<WorkspaceStorageStatus, String> {
    let disks = Disks::new_with_refreshed_list();
    let Some(disk) = disks
        .list()
        .iter()
        .filter(|disk| path.starts_with(disk.mount_point()))
        .max_by_key(|disk| disk.mount_point().as_os_str().len())
    else {
        return Ok(WorkspaceStorageStatus {
            pressure: StoragePressure::Unknown,
            total_bytes: None,
            available_bytes: None,
            warning_threshold_bytes: None,
            critical_threshold_bytes: CRITICAL_AVAILABLE_BYTES,
            mount_point: None,
            details: "LazyBuilder could not map this workspace to a storage volume.".into(),
        });
    };

    let total = disk.total_space();
    let available = disk.available_space();
    let warning_threshold = WARNING_MIN_AVAILABLE_BYTES.max(total / 20);
    let pressure = if available < CRITICAL_AVAILABLE_BYTES {
        StoragePressure::Critical
    } else if available < warning_threshold {
        StoragePressure::Warning
    } else {
        StoragePressure::Normal
    };
    let details = format!(
        "{} MB available of {} MB on {}. Warning threshold: {} MB; critical threshold: {} MB.",
        to_mb(available),
        to_mb(total),
        disk.mount_point().display(),
        to_mb(warning_threshold),
        to_mb(CRITICAL_AVAILABLE_BYTES),
    );

    Ok(WorkspaceStorageStatus {
        pressure,
        total_bytes: Some(total),
        available_bytes: Some(available),
        warning_threshold_bytes: Some(warning_threshold),
        critical_threshold_bytes: CRITICAL_AVAILABLE_BYTES,
        mount_point: Some(disk.mount_point().display().to_string()),
        details,
    })
}

fn to_mb(bytes: u64) -> u64 { bytes / (1024 * 1024) }

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn warning_threshold_has_absolute_floor() {
        assert_eq!(WARNING_MIN_AVAILABLE_BYTES.max(10 * 1024 * 1024 * 1024 / 20), WARNING_MIN_AVAILABLE_BYTES);
    }

    #[test]
    fn pressure_order_keeps_critical_below_warning() {
        let total = 100 * 1024 * 1024 * 1024u64;
        let warning = WARNING_MIN_AVAILABLE_BYTES.max(total / 20);
        assert!(CRITICAL_AVAILABLE_BYTES < warning);
    }
}
