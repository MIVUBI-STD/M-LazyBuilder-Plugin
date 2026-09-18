use super::safe_path;
use serde::{de::DeserializeOwned, Serialize};
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::Path;

const MAX_ATOMIC_JSON_BYTES: u64 = 4 * 1024 * 1024;

pub fn read<T: DeserializeOwned>(path: &Path, label: &str) -> Result<T, String> {
    safe_path::ensure_regular_file(path, label)?;
    let size = fs::metadata(path)
        .map_err(|error| format!("Could not inspect {label}: {error}"))?
        .len();
    if size > MAX_ATOMIC_JSON_BYTES {
        return Err(format!(
            "{label} exceeds the {} byte metadata limit.",
            MAX_ATOMIC_JSON_BYTES
        ));
    }
    let text = fs::read_to_string(path).map_err(|error| format!("Could not read {label}: {error}"))?;
    serde_json::from_str(&text).map_err(|error| format!("Could not parse {label}: {error}"))
}

pub fn save<T: Serialize>(path: &Path, label: &str, value: &T) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| format!("Could not create {label} directory: {error}"))?;
    }
    let temporary = temporary_path(path);
    safe_path::remove_regular_file_if_present(&temporary, &format!("{label} staging file"))?;
    let text = serde_json::to_string_pretty(value).map_err(|error| format!("Could not encode {label}: {error}"))?;
    if text.len() as u64 > MAX_ATOMIC_JSON_BYTES {
        return Err(format!(
            "{label} exceeds the {} byte metadata limit.",
            MAX_ATOMIC_JSON_BYTES
        ));
    }
    let mut file = OpenOptions::new()
        .create_new(true)
        .write(true)
        .open(&temporary)
        .map_err(|error| format!("Could not create {label} staging file: {error}"))?;
    file.write_all(text.as_bytes()).map_err(|error| format!("Could not write {label} staging file: {error}"))?;
    file.sync_all().map_err(|error| format!("Could not flush {label} staging file: {error}"))?;
    replace(&temporary, path, label)
}

pub fn recover(path: &Path, label: &str) -> Result<(), String> {
    let previous = previous_path(path);
    let temporary = temporary_path(path);
    if safe_path::entry_exists(path, label)? {
        safe_path::ensure_regular_file(path, label)?;
        return Ok(());
    }
    if safe_path::entry_exists(&previous, &format!("previous {label}"))? {
        safe_path::ensure_regular_file(&previous, &format!("previous {label}"))?;
        fs::rename(&previous, path).map_err(|error| format!("Could not restore previous {label}: {error}"))?;
        return Ok(());
    }
    if safe_path::entry_exists(&temporary, &format!("{label} staging file"))? {
        safe_path::ensure_regular_file(&temporary, &format!("{label} staging file"))?;
        fs::rename(&temporary, path).map_err(|error| format!("Could not publish recovered {label}: {error}"))?;
    }
    Ok(())
}

pub fn cleanup_recovery_files(path: &Path, label: &str) -> Result<(), String> {
    safe_path::remove_regular_file_if_present(&previous_path(path), &format!("previous {label}"))?;
    safe_path::remove_regular_file_if_present(&temporary_path(path), &format!("{label} staging file"))
}

fn replace(source: &Path, destination: &Path, label: &str) -> Result<(), String> {
    safe_path::ensure_regular_file(source, &format!("{label} staging file"))?;
    if !safe_path::entry_exists(destination, label)? {
        return fs::rename(source, destination).map_err(|error| format!("Could not publish {label}: {error}"));
    }
    safe_path::ensure_regular_file(destination, label)?;
    let previous = previous_path(destination);
    safe_path::remove_regular_file_if_present(&previous, &format!("previous {label}"))?;
    fs::rename(destination, &previous).map_err(|error| format!("Could not preserve previous {label}: {error}"))?;
    match fs::rename(source, destination) {
        Ok(()) => {
            let _ = fs::remove_file(previous);
            Ok(())
        }
        Err(error) => match fs::rename(&previous, destination) {
            Ok(()) => Err(format!("Could not publish {label}; previous value was restored: {error}")),
            Err(rollback_error) => Err(format!(
                "Could not publish {label} ({error}) and could not restore the previous value ({rollback_error}). Recovery files were preserved."
            )),
        },
    }
}

fn temporary_path(path: &Path) -> std::path::PathBuf {
    path.with_extension("json.tmp")
}

fn previous_path(path: &Path) -> std::path::PathBuf {
    path.with_extension("json.previous")
}
