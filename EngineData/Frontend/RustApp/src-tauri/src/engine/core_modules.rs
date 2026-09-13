use std::fs;
use std::path::{Path, PathBuf};

pub const CORE_VERSION: &str = "0.1.0-SNAPSHOT";
const WORLD_FILE_NAME: &str = "World-Manager-0.1.0-SNAPSHOT.jar";
const UTILITIES_FILE_NAME: &str = "Utilities-Manager-0.1.0-SNAPSHOT.jar";

pub fn sync(workspace: &Path, app_resource_dir: Option<&Path>) -> Result<(), String> {
    let world_source = resolve_source(app_resource_dir, WORLD_FILE_NAME, "modules/world-manager/target/World-Manager-0.1.0-SNAPSHOT.jar")?;
    let utilities_source = resolve_source(app_resource_dir, UTILITIES_FILE_NAME, "modules/utilities-manager/target/Utilities-Manager-0.1.0-SNAPSHOT.jar")?;
    let plugins = workspace.join("server").join("plugins");
    fs::create_dir_all(&plugins).map_err(|e| e.to_string())?;
    let backups = workspace.join("tools").join("lazybuilder").join("plugin-backups");
    fs::create_dir_all(&backups).map_err(|e| e.to_string())?;
    install_one(&world_source, &plugins.join(WORLD_FILE_NAME), &backups.join(format!("{WORLD_FILE_NAME}.previous")))?;
    install_one(&utilities_source, &plugins.join(UTILITIES_FILE_NAME), &backups.join(format!("{UTILITIES_FILE_NAME}.previous")))?;
    Ok(())
}

pub fn ready(workspace: &Path) -> bool {
    let plugins = workspace.join("server").join("plugins");
    plugins.join(WORLD_FILE_NAME).is_file() && plugins.join(UTILITIES_FILE_NAME).is_file()
}

fn resolve_source(resource_dir: Option<&Path>, file_name: &str, source_relative: &str) -> Result<PathBuf, String> {
    if let Some(resource_dir) = resource_dir {
        let bundled = resource_dir.join("core").join(file_name);
        if bundled.is_file() { return Ok(bundled); }
    }
    let source_root = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../../..");
    let source_build = source_root.join(source_relative);
    if source_build.is_file() { return Ok(source_build); }
    Err(format!(
        "LazyBuilder core module {file_name} is unavailable. Release builds must bundle core modules; development builds must run Maven package first."
    ))
}

fn install_one(source: &Path, target: &Path, backup: &Path) -> Result<(), String> {
    let temp = target.with_extension("jar.tmp");
    fs::copy(source, &temp).map_err(|e| e.to_string())?;
    if target.exists() {
        let _ = fs::remove_file(backup);
        fs::copy(target, backup).map_err(|e| e.to_string())?;
        if let Err(error) = fs::remove_file(target) {
            let _ = fs::remove_file(&temp);
            return Err(error.to_string());
        }
    }
    match fs::rename(&temp, target) {
        Ok(()) => Ok(()),
        Err(error) => {
            let _ = fs::remove_file(&temp);
            if backup.is_file() && !target.exists() { let _ = fs::copy(backup, target); }
            Err(error.to_string())
        }
    }
}
