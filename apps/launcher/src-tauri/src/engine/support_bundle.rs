use crate::engine::{diagnostics, privacy_redaction::RedactionPolicy};
use serde::Serialize;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
#[cfg(windows)]
use std::os::windows::fs::MetadataExt;
use zip::write::SimpleFileOptions;
use zip::ZipWriter;

const MAX_LOG_FILES: usize = 5;
const MAX_LOG_FILE_BYTES: u64 = 1024 * 1024;
#[cfg(windows)]
const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x00000400;

pub fn write_bundle<S, O, T>(
    destination: &Path,
    summary: &S,
    operations: &O,
    startup: &T,
    sensitive_paths: &[String],
) -> Result<PathBuf, String>
where
    S: Serialize,
    O: Serialize,
    T: Serialize,
{
    let parent = destination
        .parent()
        .ok_or_else(|| "Support bundle destination has no parent directory".to_string())?;
    fs::create_dir_all(parent).map_err(|error| format!("Could not prepare support bundle destination: {error}"))?;

    let staging = destination.with_extension("zip.incoming");
    remove_regular_file_if_exists(&staging, "support bundle staging file")?;
    ensure_regular_file_if_exists(destination, "support bundle destination")?;

    let file = OpenOptions::new()
        .create_new(true)
        .write(true)
        .open(&staging)
        .map_err(|error| format!("Could not create support bundle: {error}"))?;
    let mut zip = ZipWriter::new(file);
    let options = SimpleFileOptions::default();
    let redaction = RedactionPolicy::for_support_bundle(sensitive_paths);

    write_json(&mut zip, options, "diagnostics.json", summary, &redaction)?;
    write_json(&mut zip, options, "operations.json", operations, &redaction)?;
    write_json(&mut zip, options, "startup.json", startup, &redaction)?;

    let readme = "LazyBuilder Support Bundle\n\nThis archive was created locally for troubleshooting.\nIt is not uploaded automatically.\nIt intentionally excludes worlds, server configuration, plugin configuration/data, authentication material, and signing keys.\nAbsolute workspace/user-data paths are redacted where known, including JSON-escaped Windows path forms.\n";
    zip.start_file("README.txt", options).map_err(|error| error.to_string())?;
    zip.write_all(readme.as_bytes()).map_err(|error| error.to_string())?;

    for (index, path) in diagnostics::launcher_log_paths().into_iter().take(MAX_LOG_FILES).enumerate() {
        if !path.is_file() {
            continue;
        }
        let metadata = fs::metadata(&path).map_err(|error| error.to_string())?;
        if metadata.len() > MAX_LOG_FILE_BYTES {
            return Err(format!("Launcher diagnostic log unexpectedly exceeds its bounded size: {}", path.display()));
        }
        let text = fs::read_to_string(&path).map_err(|error| format!("Could not read launcher diagnostic log: {error}"))?;
        let name = if index == 0 { "logs/launcher.log".to_string() } else { format!("logs/launcher.{index}.log") };
        zip.start_file(name, options).map_err(|error| error.to_string())?;
        zip.write_all(redaction.redact(&text).as_bytes()).map_err(|error| error.to_string())?;
    }

    let finished = zip.finish().map_err(|error| format!("Could not finalize support bundle: {error}"))?;
    finished.sync_all().map_err(|error| format!("Could not flush support bundle staging file: {error}"))?;

    if metadata_entry_exists(destination, "support bundle destination")? {
        ensure_regular_file(destination, "support bundle destination")?;
        let previous = destination.with_extension("zip.previous");
        remove_regular_file_if_exists(&previous, "previous support bundle")?;
        fs::rename(destination, &previous).map_err(|error| format!("Could not stage existing support bundle: {error}"))?;
        match fs::rename(&staging, destination) {
            Ok(()) => {
                let _ = fs::remove_file(previous);
            }
            Err(error) => {
                let _ = fs::rename(&previous, destination);
                return Err(format!("Could not publish support bundle: {error}"));
            }
        }
    } else {
        fs::rename(&staging, destination).map_err(|error| format!("Could not publish support bundle: {error}"))?;
    }

    Ok(destination.to_path_buf())
}

fn metadata_entry_exists(path: &Path, label: &str) -> Result<bool, String> {
    match fs::symlink_metadata(path) {
        Ok(_) => Ok(true),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(false),
        Err(error) => Err(format!("Could not inspect {label}: {error}")),
    }
}

fn ensure_regular_file_if_exists(path: &Path, label: &str) -> Result<(), String> {
    if metadata_entry_exists(path, label)? {
        ensure_regular_file(path, label)?;
    }
    Ok(())
}

fn ensure_regular_file(path: &Path, label: &str) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path).map_err(|error| format!("Could not inspect {label}: {error}"))?;
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

fn remove_regular_file_if_exists(path: &Path, label: &str) -> Result<(), String> {
    if !metadata_entry_exists(path, label)? {
        return Ok(());
    }
    ensure_regular_file(path, label)?;
    fs::remove_file(path).map_err(|error| format!("Could not remove {label}: {error}"))
}

fn write_json<T: Serialize>(
    zip: &mut ZipWriter<fs::File>,
    options: SimpleFileOptions,
    name: &str,
    value: &T,
    redaction: &RedactionPolicy,
) -> Result<(), String> {
    let text = serde_json::to_string_pretty(value).map_err(|error| error.to_string())?;
    zip.start_file(name, options).map_err(|error| error.to_string())?;
    zip.write_all(redaction.redact(&text).as_bytes()).map_err(|error| error.to_string())
}
