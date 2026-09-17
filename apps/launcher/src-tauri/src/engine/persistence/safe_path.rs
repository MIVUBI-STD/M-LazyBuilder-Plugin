use std::fs;
use std::path::Path;
#[cfg(windows)]
use std::os::windows::fs::MetadataExt;

#[cfg(windows)]
const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x00000400;

pub fn entry_exists(path: &Path, label: &str) -> Result<bool, String> {
    match fs::symlink_metadata(path) {
        Ok(_) => Ok(true),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(false),
        Err(error) => Err(format!("Could not inspect {label}: {error}")),
    }
}

pub fn ensure_regular_file(path: &Path, label: &str) -> Result<(), String> {
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

pub fn remove_regular_file_if_present(path: &Path, label: &str) -> Result<(), String> {
    if !entry_exists(path, label)? {
        return Ok(());
    }
    ensure_regular_file(path, label)?;
    fs::remove_file(path).map_err(|error| format!("Could not remove stale {label}: {error}"))
}
