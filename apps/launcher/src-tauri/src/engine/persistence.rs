pub mod atomic_json;
pub mod safe_path;

use serde::{de::DeserializeOwned, Serialize};
use std::path::Path;

/// Compatibility facade for existing engine call sites.
/// New persistence code should prefer `persistence::atomic_json` and
/// `persistence::safe_path` directly so all metadata follows one safety policy.
pub fn recover_atomic_file(destination: &Path, label: &str) -> Result<(), String> {
    atomic_json::recover(destination, label)
}

pub fn read_json<T: DeserializeOwned>(path: &Path, label: &str) -> Result<T, String> {
    atomic_json::read(path, label)
}

pub fn write_json_atomically<T: Serialize>(destination: &Path, value: &T, label: &str) -> Result<(), String> {
    atomic_json::save(destination, label, value)
}

pub fn cleanup_recovery_files(destination: &Path, label: &str) -> Result<(), String> {
    atomic_json::cleanup_recovery_files(destination, label)
}

pub fn metadata_entry_exists(path: &Path, label: &str) -> Result<bool, String> {
    safe_path::entry_exists(path, label)
}

pub fn ensure_regular_file(path: &Path, label: &str) -> Result<(), String> {
    safe_path::ensure_regular_file(path, label)
}
