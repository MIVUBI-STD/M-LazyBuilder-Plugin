use crate::engine::workspace_registry;
use std::fs;
use std::path::{Component, Path, PathBuf};
#[cfg(windows)]
use std::os::windows::fs::MetadataExt;

#[cfg(windows)]
const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x00000400;

pub const WORKSPACE_ENV: &str = "LAZYBUILDER_WORKSPACE_ROOT";

pub fn workspace_root() -> Result<PathBuf, String> {
    // Explicit environment override remains available for development/automation,
    // but installed-app runtime authority comes from the persistent workspace registry.
    if let Ok(configured) = std::env::var(WORKSPACE_ENV) {
        let path = PathBuf::from(configured);
        if path.is_dir() {
            return canonical_directory(&path, WORKSPACE_ENV);
        }
        return Err("LAZYBUILDER_WORKSPACE_ROOT does not point to a directory".into());
    }

    workspace_registry::active_workspace()
}

fn canonical_directory(path: &Path, label: &str) -> Result<PathBuf, String> {
    path.canonicalize()
        .map_err(|error| format!("Could not resolve {label}: {error}"))
}

pub fn safe_relative_path(value: &str, label: &str) -> Result<PathBuf, String> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        return Err(format!("{label} is required"));
    }
    let path = Path::new(trimmed);
    if path.is_absolute() {
        return Err(format!("{label} must be relative to the LazyBuilder workspace"));
    }
    if path.components().any(|component| matches!(component, Component::ParentDir | Component::RootDir | Component::Prefix(_))) {
        return Err(format!("{label} may not escape the LazyBuilder workspace"));
    }
    Ok(path.to_path_buf())
}

pub fn safe_file_name(value: &str, label: &str) -> Result<String, String> {
    let relative = safe_relative_path(value, label)?;
    if relative.components().count() != 1 {
        return Err(format!("{label} must be one file name"));
    }
    relative
        .file_name()
        .and_then(|value| value.to_str())
        .map(ToOwned::to_owned)
        .ok_or_else(|| format!("{label} is not a valid file name"))
}

pub fn lazybuilder_tools_dir() -> Result<PathBuf, String> {
    Ok(workspace_root()?.join("tools").join("lazybuilder"))
}

pub fn lazybuilder_config_dir() -> Result<PathBuf, String> {
    Ok(lazybuilder_tools_dir()?.join("config"))
}

pub fn lazybuilder_cache_dir() -> Result<PathBuf, String> {
    Ok(lazybuilder_tools_dir()?.join("cache"))
}

pub fn lazybuilder_logs_dir() -> Result<PathBuf, String> {
    Ok(lazybuilder_tools_dir()?.join("logs"))
}

pub fn world_system_dir() -> Result<PathBuf, String> {
    Ok(workspace_root()?.join("world-system"))
}

pub fn worlds_dir() -> Result<PathBuf, String> {
    Ok(world_system_dir()?.join("worlds"))
}

pub fn ensure_runtime_layout() -> Result<(), String> {
    let root = workspace_root()?;
    ensure_workspace_root(&root)?;
    let directories = [
        root.join("server"),
        root.join("server").join("plugins"),
        worlds_dir()?,
        world_system_dir()?.join("imports"),
        world_system_dir()?.join("exports"),
        world_system_dir()?.join("backups"),
        world_system_dir()?.join("work"),
        lazybuilder_config_dir()?,
        lazybuilder_cache_dir()?,
        lazybuilder_logs_dir()?,
        lazybuilder_tools_dir()?.join("disabled-plugins"),
        lazybuilder_tools_dir()?.join("plugin-backups"),
    ];

    for directory in directories {
        ensure_owned_directory(&root, &directory, "LazyBuilder runtime directory")?;
    }
    Ok(())
}

pub fn ensure_owned_directory(
    workspace: &Path,
    directory: &Path,
    label: &str,
) -> Result<(), String> {
    fs::create_dir_all(directory)
        .map_err(|error| format!("Could not create {label}: {error}"))?;
    ensure_existing_owned_directory(workspace, directory, label)
}

pub fn ensure_existing_owned_directory(
    workspace: &Path,
    directory: &Path,
    label: &str,
) -> Result<(), String> {
    if !directory.exists() {
        return Ok(());
    }
    ensure_workspace_root(workspace)?;
    let canonical_workspace = workspace
        .canonicalize()
        .map_err(|error| format!("Could not resolve LazyBuilder workspace: {error}"))?;
    let canonical_directory = directory
        .canonicalize()
        .map_err(|error| format!("Could not resolve {label}: {error}"))?;
    if !canonical_directory.starts_with(&canonical_workspace) {
        return Err(format!("LazyBuilder refused {label} outside the active workspace."));
    }

    let relative = directory
        .strip_prefix(workspace)
        .map_err(|_| format!("LazyBuilder refused {label} outside the active workspace."))?;
    let mut current = workspace.to_path_buf();
    for component in relative.components() {
        current.push(component.as_os_str());
        let metadata = fs::symlink_metadata(&current)
            .map_err(|error| format!("Could not inspect {label}: {error}"))?;
        if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
            return Err(format!("LazyBuilder refused symbolic-link/reparse indirection in {label}."));
        }
        if !metadata.file_type().is_dir() {
            return Err(format!("LazyBuilder expected {label} to contain directories only."));
        }
    }
    Ok(())
}

fn ensure_workspace_root(workspace: &Path) -> Result<(), String> {
    let metadata = fs::symlink_metadata(workspace)
        .map_err(|error| format!("Could not inspect LazyBuilder workspace: {error}"))?;
    if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
        return Err("LazyBuilder refused a symbolic-link/reparse workspace root.".into());
    }
    if !metadata.file_type().is_dir() {
        return Err("LazyBuilder workspace root is not a directory.".into());
    }
    Ok(())
}

#[cfg(windows)]
fn is_reparse_point(metadata: &fs::Metadata) -> bool {
    metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0
}

#[cfg(not(windows))]
fn is_reparse_point(_metadata: &fs::Metadata) -> bool {
    false
}
