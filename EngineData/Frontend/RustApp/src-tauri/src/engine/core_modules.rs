use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};

pub const CORE_VERSION: &str = "0.1.0-SNAPSHOT";
const WORLD_FILE_NAME: &str = "World-Manager-0.1.0-SNAPSHOT.jar";
const UTILITIES_FILE_NAME: &str = "Utilities-Manager-0.1.0-SNAPSHOT.jar";

pub struct CoreSyncTransaction {
    modules: Vec<ModuleInstall>,
    committed: Vec<usize>,
    finalized: bool,
}

impl CoreSyncTransaction {
    pub fn finalize(mut self) {
        self.finalized = true;
        cleanup_staged(&self.modules);
    }

    pub fn rollback(mut self) -> Result<(), String> {
        let result = self.rollback_inner();
        self.finalized = true;
        cleanup_staged(&self.modules);
        result
    }

    fn rollback_inner(&mut self) -> Result<(), String> {
        let mut errors = Vec::new();
        for index in self.committed.drain(..).rev() {
            if let Err(error) = self.modules[index].rollback() {
                errors.push(error);
            }
        }
        if errors.is_empty() {
            Ok(())
        } else {
            Err(errors.join("; "))
        }
    }
}

impl Drop for CoreSyncTransaction {
    fn drop(&mut self) {
        if !self.finalized {
            let _ = self.rollback_inner();
            cleanup_staged(&self.modules);
        }
    }
}

pub fn begin_sync(workspace: &Path, app_resource_dir: Option<&Path>) -> Result<CoreSyncTransaction, String> {
    let world_source = resolve_source(
        app_resource_dir,
        WORLD_FILE_NAME,
        "modules/world-manager/target/World-Manager-0.1.0-SNAPSHOT.jar",
    )?;
    let utilities_source = resolve_source(
        app_resource_dir,
        UTILITIES_FILE_NAME,
        "modules/utilities-manager/target/Utilities-Manager-0.1.0-SNAPSHOT.jar",
    )?;

    let plugins = workspace.join("server").join("plugins");
    let backups = workspace
        .join("tools")
        .join("lazybuilder")
        .join("plugin-backups");
    fs::create_dir_all(&plugins).map_err(|e| e.to_string())?;
    fs::create_dir_all(&backups).map_err(|e| e.to_string())?;

    let mut modules = vec![
        ModuleInstall::new(
            world_source,
            plugins.join(WORLD_FILE_NAME),
            backups.join(format!("{WORLD_FILE_NAME}.previous")),
        )?,
        ModuleInstall::new(
            utilities_source,
            plugins.join(UTILITIES_FILE_NAME),
            backups.join(format!("{UTILITIES_FILE_NAME}.previous")),
        )?,
    ];

    for module in modules.iter_mut().filter(|module| module.needs_update) {
        module.stage()?;
    }
    for module in modules.iter_mut().filter(|module| module.needs_update) {
        module.backup_current()?;
    }

    let mut committed: Vec<usize> = Vec::new();
    for index in 0..modules.len() {
        if !modules[index].needs_update {
            continue;
        }
        if let Err(error) = modules[index].commit() {
            let mut rollback_errors = Vec::new();
            for committed_index in committed.into_iter().rev() {
                if let Err(rollback_error) = modules[committed_index].rollback() {
                    rollback_errors.push(rollback_error);
                }
            }
            if let Err(rollback_error) = modules[index].rollback() {
                rollback_errors.push(rollback_error);
            }
            cleanup_staged(&modules);
            if rollback_errors.is_empty() {
                return Err(format!("Core module update failed and was rolled back: {error}"));
            }
            return Err(format!(
                "Core module update failed: {error}; rollback also reported: {}",
                rollback_errors.join("; ")
            ));
        }
        committed.push(index);
    }

    Ok(CoreSyncTransaction {
        modules,
        committed,
        finalized: false,
    })
}

pub fn sync(workspace: &Path, app_resource_dir: Option<&Path>) -> Result<(), String> {
    let transaction = begin_sync(workspace, app_resource_dir)?;
    transaction.finalize();
    Ok(())
}

struct ModuleInstall {
    source: PathBuf,
    target: PathBuf,
    backup: PathBuf,
    staged: PathBuf,
    needs_update: bool,
    had_target: bool,
}

impl ModuleInstall {
    fn new(source: PathBuf, target: PathBuf, backup: PathBuf) -> Result<Self, String> {
        let needs_update = if target.is_file() {
            !files_equal(&source, &target)?
        } else {
            target.exists() || !target.is_file()
        };
        if target.exists() && !target.is_file() {
            return Err(format!(
                "Core module target exists but is not a file: {}",
                target.display()
            ));
        }
        let staged = target.with_extension("jar.incoming");
        Ok(Self {
            source,
            target,
            backup,
            staged,
            needs_update,
            had_target: false,
        })
    }

    fn stage(&mut self) -> Result<(), String> {
        if self.staged.exists() {
            fs::remove_file(&self.staged).map_err(|e| e.to_string())?;
        }
        fs::copy(&self.source, &self.staged).map_err(|e| {
            format!("Could not stage core module {}: {e}", self.source.display())
        })?;
        if !files_equal(&self.source, &self.staged)? {
            let _ = fs::remove_file(&self.staged);
            return Err(format!(
                "Staged core module verification failed: {}",
                self.source.display()
            ));
        }
        Ok(())
    }

    fn backup_current(&mut self) -> Result<(), String> {
        self.had_target = self.target.is_file();
        if !self.had_target {
            return Ok(());
        }
        if self.backup.exists() {
            fs::remove_file(&self.backup).map_err(|e| e.to_string())?;
        }
        fs::copy(&self.target, &self.backup).map_err(|e| {
            format!("Could not back up core module {}: {e}", self.target.display())
        })?;
        Ok(())
    }

    fn commit(&mut self) -> Result<(), String> {
        if self.target.exists() {
            fs::remove_file(&self.target).map_err(|e| {
                format!("Could not replace active core module {}: {e}", self.target.display())
            })?;
        }
        fs::rename(&self.staged, &self.target).map_err(|error| {
            format!("Could not publish core module {}: {error}", self.target.display())
        })
    }

    fn rollback(&mut self) -> Result<(), String> {
        if self.target.exists() {
            fs::remove_file(&self.target).map_err(|e| e.to_string())?;
        }
        if self.had_target {
            if !self.backup.is_file() {
                return Err(format!(
                    "Core module rollback backup is missing: {}",
                    self.backup.display()
                ));
            }
            fs::copy(&self.backup, &self.target).map_err(|e| {
                format!("Could not restore core module {}: {e}", self.target.display())
            })?;
        }
        Ok(())
    }
}

fn cleanup_staged(modules: &[ModuleInstall]) {
    for module in modules {
        if module.staged.exists() {
            let _ = fs::remove_file(&module.staged);
        }
    }
}

fn resolve_source(
    resource_dir: Option<&Path>,
    file_name: &str,
    source_relative: &str,
) -> Result<PathBuf, String> {
    if let Some(resource_dir) = resource_dir {
        let bundled = resource_dir.join("resources").join("core").join(file_name);
        if bundled.is_file() {
            return Ok(bundled);
        }
    }
    let source_root = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../../..");
    let source_build = source_root.join(source_relative);
    if source_build.is_file() {
        return Ok(source_build);
    }
    Err(format!(
        "LazyBuilder core module {file_name} is unavailable. Release builds must bundle core modules; development builds must run Maven package first."
    ))
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

    fn resource_tree(root: &Path, world: &[u8], utilities: &[u8]) {
        let core = root.join("resources").join("core");
        fs::create_dir_all(&core).unwrap();
        fs::write(core.join(WORLD_FILE_NAME), world).unwrap();
        fs::write(core.join(UTILITIES_FILE_NAME), utilities).unwrap();
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
        resource_tree(&resource_root, b"new-world", b"new-utilities");
        let plugins = workspace.join("server").join("plugins");
        fs::create_dir_all(&plugins).unwrap();
        fs::write(plugins.join(WORLD_FILE_NAME), b"old-world").unwrap();
        fs::write(plugins.join(UTILITIES_FILE_NAME), b"old-utilities").unwrap();
        sync(&workspace, Some(&resource_root)).unwrap();
        assert_eq!(fs::read(plugins.join(WORLD_FILE_NAME)).unwrap(), b"new-world");
        assert_eq!(fs::read(plugins.join(UTILITIES_FILE_NAME)).unwrap(), b"new-utilities");
        let backups = workspace.join("tools").join("lazybuilder").join("plugin-backups");
        assert_eq!(fs::read(backups.join(format!("{WORLD_FILE_NAME}.previous"))).unwrap(), b"old-world");
        assert_eq!(fs::read(backups.join(format!("{UTILITIES_FILE_NAME}.previous"))).unwrap(), b"old-utilities");
        let _ = fs::remove_dir_all(resource_root);
        let _ = fs::remove_dir_all(workspace);
    }

    #[test]
    fn unfinalized_transaction_restores_both_modules() {
        let resource_root = test_root("core-rollback-resources");
        let workspace = test_root("core-rollback-workspace");
        resource_tree(&resource_root, b"new-world", b"new-utilities");
        let plugins = workspace.join("server").join("plugins");
        fs::create_dir_all(&plugins).unwrap();
        fs::write(plugins.join(WORLD_FILE_NAME), b"old-world").unwrap();
        fs::write(plugins.join(UTILITIES_FILE_NAME), b"old-utilities").unwrap();
        {
            let _transaction = begin_sync(&workspace, Some(&resource_root)).unwrap();
            assert_eq!(fs::read(plugins.join(WORLD_FILE_NAME)).unwrap(), b"new-world");
            assert_eq!(fs::read(plugins.join(UTILITIES_FILE_NAME)).unwrap(), b"new-utilities");
        }
        assert_eq!(fs::read(plugins.join(WORLD_FILE_NAME)).unwrap(), b"old-world");
        assert_eq!(fs::read(plugins.join(UTILITIES_FILE_NAME)).unwrap(), b"old-utilities");
        let _ = fs::remove_dir_all(resource_root);
        let _ = fs::remove_dir_all(workspace);
    }

    #[test]
    fn sync_preflights_both_targets_before_replacing_either_module() {
        let resource_root = test_root("core-preflight-resources");
        let workspace = test_root("core-preflight-workspace");
        resource_tree(&resource_root, b"new-world", b"new-utilities");
        let plugins = workspace.join("server").join("plugins");
        fs::create_dir_all(&plugins).unwrap();
        fs::write(plugins.join(WORLD_FILE_NAME), b"old-world").unwrap();
        fs::create_dir_all(plugins.join(UTILITIES_FILE_NAME)).unwrap();
        let error = sync(&workspace, Some(&resource_root)).unwrap_err();
        assert!(error.contains("not a file"));
        assert_eq!(fs::read(plugins.join(WORLD_FILE_NAME)).unwrap(), b"old-world");
        let _ = fs::remove_dir_all(resource_root);
        let _ = fs::remove_dir_all(workspace);
    }
}
