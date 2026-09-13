use std::fs;
use std::io::Read;
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

fn resolve_source(resource_dir: Option<&Path>, file_name: &str, source_relative: &str) -> Result<PathBuf, String> {
    if let Some(resource_dir) = resource_dir {
        let bundled = resource_dir.join("resources").join("core").join(file_name);
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
    if target.is_file() && files_equal(source, target)? {
        return Ok(());
    }

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

fn files_equal(left: &Path, right: &Path) -> Result<bool, String> {
    let left_meta = fs::metadata(left).map_err(|e| e.to_string())?;
    let right_meta = fs::metadata(right).map_err(|e| e.to_string())?;
    if left_meta.len() != right_meta.len() {
        return Ok(false);
    }

    let mut left_file = fs::File::open(left).map_err(|e| e.to_string())?;
    let mut right_file = fs::File::open(right).map_err(|e| e.to_string())?;
    let mut left_buffer = [0u8; 64 * 1024];
    let mut right_buffer = [0u8; 64 * 1024];

    loop {
        let left_count = left_file.read(&mut left_buffer).map_err(|e| e.to_string())?;
        let right_count = right_file.read(&mut right_buffer).map_err(|e| e.to_string())?;
        if left_count != right_count {
            return Ok(false);
        }
        if left_count == 0 {
            return Ok(true);
        }
        if left_buffer[..left_count] != right_buffer[..right_count] {
            return Ok(false);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn test_root(label: &str) -> PathBuf {
        let nonce = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos();
        std::env::temp_dir().join(format!("lazybuilder-{label}-{}-{nonce}", std::process::id()))
    }

    #[test]
    fn resolves_bundled_core_module_from_tauri_resource_tree() {
        let root = test_root("core-resource");
        let bundled = root.join("resources").join("core").join(WORLD_FILE_NAME);
        fs::create_dir_all(bundled.parent().unwrap()).unwrap();
        fs::write(&bundled, b"test-jar").unwrap();

        let resolved = resolve_source(Some(&root), WORLD_FILE_NAME, "missing/source.jar").unwrap();
        assert_eq!(resolved, bundled);

        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn sync_replaces_core_modules_and_keeps_previous_backup() {
        let resource_root = test_root("core-sync-resources");
        let workspace = test_root("core-sync-workspace");
        let core = resource_root.join("resources").join("core");
        fs::create_dir_all(&core).unwrap();
        fs::write(core.join(WORLD_FILE_NAME), b"new-world").unwrap();
        fs::write(core.join(UTILITIES_FILE_NAME), b"new-utilities").unwrap();

        let plugins = workspace.join("server").join("plugins");
        fs::create_dir_all(&plugins).unwrap();
        fs::write(plugins.join(WORLD_FILE_NAME), b"old-world").unwrap();
        fs::write(plugins.join(UTILITIES_FILE_NAME), b"old-utilities").unwrap();

        sync(&workspace, Some(&resource_root)).unwrap();

        assert_eq!(fs::read(plugins.join(WORLD_FILE_NAME)).unwrap(), b"new-world");
        assert_eq!(fs::read(plugins.join(UTILITIES_FILE_NAME)).unwrap(), b"new-utilities");
        let backups = workspace.join("tools").join("lazybuilder").join("plugin-backups");
        let world_backup = backups.join(format!("{WORLD_FILE_NAME}.previous"));
        let utilities_backup = backups.join(format!("{UTILITIES_FILE_NAME}.previous"));
        assert_eq!(fs::read(&world_backup).unwrap(), b"old-world");
        assert_eq!(fs::read(&utilities_backup).unwrap(), b"old-utilities");

        // Re-running sync with identical artifacts must be a no-op. In particular,
        // the previous backup must not be replaced with the already-current JAR.
        sync(&workspace, Some(&resource_root)).unwrap();
        assert_eq!(fs::read(plugins.join(WORLD_FILE_NAME)).unwrap(), b"new-world");
        assert_eq!(fs::read(plugins.join(UTILITIES_FILE_NAME)).unwrap(), b"new-utilities");
        assert_eq!(fs::read(world_backup).unwrap(), b"old-world");
        assert_eq!(fs::read(utilities_backup).unwrap(), b"old-utilities");

        let _ = fs::remove_dir_all(resource_root);
        let _ = fs::remove_dir_all(workspace);
    }
}
