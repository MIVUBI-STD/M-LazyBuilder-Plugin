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
    let legacy_incoming = legacy_incoming_path(path);

    if safe_path::entry_exists(path, label)? {
        safe_path::ensure_regular_file(path, label)?;
        return Ok(());
    }
    if safe_path::entry_exists(&previous, &format!("previous {label}"))? {
        safe_path::ensure_regular_file(&previous, &format!("previous {label}"))?;
        fs::rename(&previous, path).map_err(|error| format!("Could not restore previous {label}: {error}"))?;
        return Ok(());
    }

    let has_temporary = safe_path::entry_exists(&temporary, &format!("{label} staging file"))?;
    let has_legacy = safe_path::entry_exists(&legacy_incoming, &format!("legacy {label} staging file"))?;
    if has_temporary && has_legacy {
        safe_path::ensure_regular_file(&temporary, &format!("{label} staging file"))?;
        safe_path::ensure_regular_file(&legacy_incoming, &format!("legacy {label} staging file"))?;
        return Err(format!(
            "{label} recovery is ambiguous because both canonical and legacy staging files exist. Recovery evidence was preserved."
        ));
    }

    if has_temporary {
        safe_path::ensure_regular_file(&temporary, &format!("{label} staging file"))?;
        fs::rename(&temporary, path).map_err(|error| format!("Could not publish recovered {label}: {error}"))?;
    } else if has_legacy {
        safe_path::ensure_regular_file(&legacy_incoming, &format!("legacy {label} staging file"))?;
        fs::rename(&legacy_incoming, path).map_err(|error| format!("Could not publish legacy recovered {label}: {error}"))?;
    }
    Ok(())
}

pub fn cleanup_recovery_files(path: &Path, label: &str) -> Result<(), String> {
    safe_path::remove_regular_file_if_present(&previous_path(path), &format!("previous {label}"))?;
    safe_path::remove_regular_file_if_present(&temporary_path(path), &format!("{label} staging file"))?;
    safe_path::remove_regular_file_if_present(
        &legacy_incoming_path(path),
        &format!("legacy {label} staging file"),
    )
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

fn legacy_incoming_path(path: &Path) -> std::path::PathBuf {
    path.with_extension("json.incoming")
}


#[cfg(test)]
mod tests {
    use super::{read, MAX_ATOMIC_JSON_BYTES};
    use std::fs::{self, File};
    use std::sync::atomic::{AtomicU64, Ordering};

    static NEXT_TEST: AtomicU64 = AtomicU64::new(1);

    #[test]
    fn legacy_incoming_recovers_when_no_canonical_copy_exists() {
        let sequence = NEXT_TEST.fetch_add(1, Ordering::Relaxed);
        let directory = std::env::temp_dir().join(format!(
            "lazybuilder-atomic-json-legacy-{}-{sequence}",
            std::process::id()
        ));
        fs::create_dir_all(&directory).unwrap();
        let path = directory.join("legacy.json");
        let legacy = path.with_extension("json.incoming");
        fs::write(&legacy, br#"{"value":1}"#).unwrap();

        super::recover(&path, "legacy metadata").unwrap();
        let value: serde_json::Value = read(&path, "legacy metadata").unwrap();
        assert_eq!(value["value"], 1);
        assert!(!legacy.exists());

        let _ = fs::remove_dir_all(directory);
    }

    #[test]
    fn dual_staging_is_preserved_as_ambiguous() {
        let sequence = NEXT_TEST.fetch_add(1, Ordering::Relaxed);
        let directory = std::env::temp_dir().join(format!(
            "lazybuilder-atomic-json-ambiguous-{}-{sequence}",
            std::process::id()
        ));
        fs::create_dir_all(&directory).unwrap();
        let path = directory.join("ambiguous.json");
        let temporary = path.with_extension("json.tmp");
        let legacy = path.with_extension("json.incoming");
        fs::write(&temporary, b"{}").unwrap();
        fs::write(&legacy, b"{}").unwrap();

        assert!(super::recover(&path, "ambiguous metadata").is_err());
        assert!(temporary.exists());
        assert!(legacy.exists());

        let _ = fs::remove_dir_all(directory);
    }

    #[test]
    fn oversized_atomic_json_is_rejected_before_parse() {
        let sequence = NEXT_TEST.fetch_add(1, Ordering::Relaxed);
        let directory = std::env::temp_dir().join(format!(
            "lazybuilder-atomic-json-limit-{}-{sequence}",
            std::process::id()
        ));
        fs::create_dir_all(&directory).unwrap();
        let path = directory.join("oversized.json");
        let file = File::create(&path).unwrap();
        file.set_len(MAX_ATOMIC_JSON_BYTES + 1).unwrap();

        let error = read::<serde_json::Value>(&path, "test metadata").unwrap_err();
        assert!(error.contains("metadata limit"));

        let _ = fs::remove_dir_all(directory);
    }
}
