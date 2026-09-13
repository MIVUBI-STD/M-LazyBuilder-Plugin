use std::fs;
use std::path::{Path, PathBuf};

pub const WORKSPACE_ENV: &str = "LAZYBUILDER_WORKSPACE_ROOT";

pub fn workspace_root() -> Result<PathBuf, String> {
    if let Ok(configured) = std::env::var(WORKSPACE_ENV) {
        let path = PathBuf::from(configured);
        if path.is_dir() {
            return Ok(path);
        }
        return Err("LAZYBUILDER_WORKSPACE_ROOT does not point to a directory".into());
    }

    if let Ok(executable) = std::env::current_exe() {
        if let Some(parent) = executable.parent() {
            if let Some(root) = find_workspace(parent) {
                return Ok(root);
            }
        }
    }

    let current = std::env::current_dir().map_err(|error| error.to_string())?;
    if let Some(root) = find_workspace(&current) {
        return Ok(root);
    }

    Ok(current)
}

fn find_workspace(start: &Path) -> Option<PathBuf> {
    for ancestor in start.ancestors() {
        let runtime_layout = ancestor.join("server").is_dir();
        let source_layout = ancestor.join("pom.xml").is_file() && ancestor.join("modules").is_dir();
        if runtime_layout || source_layout {
            return Some(ancestor.to_path_buf());
        }
    }
    None
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
    // Archive is lifecycle metadata today, not a second physical world store. Keep the
    // runtime root limited to directories with an active owner so maintenance stays clear.
    let directories = [
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
        fs::create_dir_all(directory).map_err(|error| error.to_string())?;
    }
    Ok(())
}
