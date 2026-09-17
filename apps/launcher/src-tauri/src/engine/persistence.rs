use serde::{de::DeserializeOwned, Serialize};
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::Path;
#[cfg(windows)]
use std::os::windows::fs::MetadataExt;

#[cfg(windows)]
const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x00000400;

/// Recover a metadata file after an interrupted atomic publish.
///
/// Recovery order is deliberately conservative: an existing committed file wins,
/// then the previous committed copy, and only then an incoming staging file.
pub fn recover_atomic_file(destination: &Path, label: &str) -> Result<(), String> {
    let previous = destination.with_extension("json.previous");
    let temporary = destination.with_extension("json.tmp");

    if metadata_entry_exists(destination, label)? {
        ensure_regular_file(destination, label)?;
        return Ok(());
    }

    if metadata_entry_exists(&previous, &format!("previous {label}"))? {
        ensure_regular_file(&previous, &format!("previous {label}"))?;
        fs::rename(&previous, destination)
            .map_err(|error| format!("Could not restore previous {label}: {error}"))?;
        return Ok(());
    }

    if metadata_entry_exists(&temporary, &format!("{label} staging file"))? {
        ensure_regular_file(&temporary, &format!("{label} staging file"))?;
        fs::rename(&temporary, destination)
            .map_err(|error| format!("Could not publish recovered {label}: {error}"))?;
    }
    Ok(())
}

pub fn read_json<T: DeserializeOwned>(path: &Path, label: &str) -> Result<T, String> {
    ensure_regular_file(path, label)?;
    let text = fs::read_to_string(path)
        .map_err(|error| format!("Could not read {label}: {error}"))?;
    serde_json::from_str(&text).map_err(|error| format!("Could not parse {label}: {error}"))
}

pub fn write_json_atomically<T: Serialize>(destination: &Path, value: &T, label: &str) -> Result<(), String> {
    if let Some(parent) = destination.parent() {
        fs::create_dir_all(parent)
            .map_err(|error| format!("Could not create directory for {label}: {error}"))?;
    }

    let temporary = destination.with_extension("json.tmp");
    let text = serde_json::to_string_pretty(value)
        .map_err(|error| format!("Could not encode {label}: {error}"))?;
    write_staging_file(&temporary, text.as_bytes(), label)?;
    replace_file(&temporary, destination, label)
}

pub fn cleanup_recovery_files(destination: &Path, label: &str) -> Result<(), String> {
    remove_stale_file(&destination.with_extension("json.previous"), &format!("previous {label}"))?;
    remove_stale_file(&destination.with_extension("json.tmp"), &format!("{label} staging file"))
}

pub fn metadata_entry_exists(path: &Path, label: &str) -> Result<bool, String> {
    match fs::symlink_metadata(path) {
        Ok(_) => Ok(true),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(false),
        Err(error) => Err(format!("Could not inspect {label}: {error}")),
    }
}

pub fn ensure_regular_file(path: &Path, label: &str) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path)
        .map_err(|error| format!("Could not inspect {label}: {error}"))?;
    if metadata.file_type().is_symlink() {
        return Err(format!("LazyBuilder refused a symbolic link as {label}"));
    }
    #[cfg(windows)]
    if metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0 {
        return Err(format!("LazyBuilder refused a Windows reparse point as {label}"));
    }
    if !metadata.file_type().is_file() {
        return Err(format!("LazyBuilder expected {label} to be a regular file"));
    }
    Ok(())
}

fn write_staging_file(path: &Path, bytes: &[u8], label: &str) -> Result<(), String> {
    if metadata_entry_exists(path, &format!("{label} staging file"))? {
        ensure_regular_file(path, &format!("{label} staging file"))?;
        fs::remove_file(path)
            .map_err(|error| format!("Could not clear stale {label} staging file: {error}"))?;
    }

    let mut file = OpenOptions::new()
        .create_new(true)
        .write(true)
        .open(path)
        .map_err(|error| format!("Could not create {label} staging file: {error}"))?;
    file.write_all(bytes)
        .map_err(|error| format!("Could not write {label} staging file: {error}"))?;
    file.sync_all()
        .map_err(|error| format!("Could not flush {label} staging file: {error}"))
}

fn replace_file(source: &Path, destination: &Path, label: &str) -> Result<(), String> {
    ensure_regular_file(source, &format!("{label} staging file"))?;
    if metadata_entry_exists(destination, label)? {
        ensure_regular_file(destination, label)?;
        let backup = destination.with_extension("json.previous");
        remove_stale_file(&backup, &format!("previous {label}"))?;
        fs::rename(destination, &backup)
            .map_err(|error| format!("Could not preserve previous {label}: {error}"))?;
        match fs::rename(source, destination) {
            Ok(()) => {
                let _ = fs::remove_file(backup);
                Ok(())
            }
            Err(error) => match fs::rename(&backup, destination) {
                Ok(()) => Err(format!("Could not publish {label}; previous value was restored: {error}")),
                Err(rollback_error) => Err(format!(
                    "Could not publish {label} ({error}) and could not restore the previous value ({rollback_error}). Recovery files were preserved."
                )),
            },
        }
    } else {
        fs::rename(source, destination)
            .map_err(|error| format!("Could not publish {label}: {error}"))
    }
}

fn remove_stale_file(path: &Path, label: &str) -> Result<(), String> {
    if !metadata_entry_exists(path, label)? {
        return Ok(());
    }
    ensure_regular_file(path, label)?;
    fs::remove_file(path).map_err(|error| format!("Could not remove stale {label}: {error}"))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde::{Deserialize, Serialize};
    use std::path::PathBuf;
    use std::sync::atomic::{AtomicU64, Ordering};

    static NEXT_TEST: AtomicU64 = AtomicU64::new(1);

    fn temp_path() -> PathBuf {
        let sequence = NEXT_TEST.fetch_add(1, Ordering::Relaxed);
        let directory = std::env::temp_dir().join(format!("lazybuilder-persistence-test-{}-{sequence}", std::process::id()));
        fs::create_dir_all(&directory).unwrap();
        directory.join("metadata.json")
    }

    #[derive(Debug, PartialEq, Eq, Serialize, Deserialize)]
    struct TestValue { value: String }

    #[test]
    fn atomic_json_round_trip_uses_shared_contract() {
        let path = temp_path();
        let expected = TestValue { value: "ready".into() };
        write_json_atomically(&path, &expected, "test metadata").unwrap();
        let actual: TestValue = read_json(&path, "test metadata").unwrap();
        assert_eq!(actual, expected);
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn recovery_prefers_previous_committed_copy() {
        let path = temp_path();
        let previous = path.with_extension("json.previous");
        let temporary = path.with_extension("json.tmp");
        fs::write(&previous, b"previous").unwrap();
        fs::write(&temporary, b"incoming").unwrap();
        recover_atomic_file(&path, "test metadata").unwrap();
        assert_eq!(fs::read_to_string(&path).unwrap(), "previous");
        assert!(temporary.exists());
        cleanup_recovery_files(&path, "test metadata").unwrap();
        assert!(!temporary.exists());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }
}
