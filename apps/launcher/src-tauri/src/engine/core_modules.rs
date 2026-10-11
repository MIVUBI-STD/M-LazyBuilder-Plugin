use crate::engine::{paths, persistence};
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::io::Read;
use std::path::{Path, PathBuf};
pub const CORE_VERSION: &str = "0.1.0-SNAPSHOT";
const WORLD_FILE_NAME: &str = "World-Manager-0.1.0-SNAPSHOT.jar";
const UTILITIES_FILE_NAME: &str = "Utilities-Manager-0.1.0-SNAPSHOT.jar";

#[derive(Debug)]
pub struct CoreSyncTransaction {
    modules: Vec<ModuleInstall>,
    committed: Vec<usize>,
    finalized: bool,
    pending_marker: Option<PathBuf>,
}

impl CoreSyncTransaction {
    pub fn finalize(mut self) -> Result<(), String> {
        // Manifest publication already committed. Only now promote new
        // previous-valid rollback backups, leaving older backups intact
        // throughout an uncommitted transaction.
        self.finalized = true;
        for module in self.modules.iter().filter(|module| module.needs_update && module.had_target) {
            module.promote_backup()?;
        }
        if let Some(marker) = &self.pending_marker {
            fs::remove_file(marker).map_err(|error| format!(
                "Core modules committed, but could not retire recovery marker {}: {error}",
                marker.display()
            ))?;
        }
        cleanup_staged(&self.modules);
        Ok(())
    }

    pub fn rollback(mut self) -> Result<(), String> {
        let result = self.rollback_inner();
        self.finalized = true;
        if result.is_ok() {
            if let Some(marker) = &self.pending_marker {
                fs::remove_file(marker).map_err(|error| format!(
                    "Core modules restored, but recovery marker {} remains: {error}",
                    marker.display()
                ))?;
            }
            cleanup_staged(&self.modules);
        }
        // On an incomplete rollback keep staged copies, previous backups and
        // the marker; they may be the only evidence for manual reconciliation.
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
            if self.rollback_inner().is_ok() {
                if let Some(marker) = &self.pending_marker {
                    if fs::remove_file(marker).is_err() { return; }
                }
                cleanup_staged(&self.modules);
            }
            // A rollback failure deliberately leaves recovery bytes in place.
        }
    }
}

pub fn begin_sync(workspace: &Path, app_resource_dir: Option<&Path>) -> Result<CoreSyncTransaction, String> {
    let world_source = resolve_source(app_resource_dir, WORLD_FILE_NAME)?;
    let utilities_source = resolve_source(app_resource_dir, UTILITIES_FILE_NAME)?;

    let plugins = workspace.join("server").join("plugins");
    let backups = workspace
        .join("tools")
        .join("lazybuilder")
        .join("plugin-backups");
    paths::ensure_owned_directory(workspace, &plugins, "core plugin directory")?;
    paths::ensure_owned_directory(workspace, &backups, "core rollback directory")?;

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

    let marker_path = backups.join(".core-sync-pending");
    // Even a byte-identical installed pair is not evidence that a previous
    // interrupted sync completed. Refuse to bypass its durable marker.
    if entry_exists(&marker_path)? {
        return Err(format!(
            "Unresolved core module transaction at {}; preserve existing recovery files and reconcile before retrying.",
            marker_path.display()
        ));
    }
    let any_update = modules.iter().any(|module| module.needs_update);
    let pending_marker = any_update.then_some(marker_path);
    if let Some(marker) = &pending_marker {
        // Preflight every staging/swap path before touching the active JARs.
        for module in modules.iter().filter(|module| module.needs_update) {
            for path in [
                &module.staged,
                &module.backup.with_extension("previous.incoming"),
                &module.backup.with_extension("previous.swap"),
                &module.target.with_extension("previous.swap"),
                &module.target.with_extension("jar.restore"),
            ] {
                if entry_exists(path)? {
                    return Err(format!(
                        "Unresolved core module recovery artifact at {}. Preserve it before retrying.",
                        path.display()
                    ));
                }
            }
        }
        let mut file = OpenOptions::new().write(true).create_new(true)
            .open(marker).map_err(|error| format!(
                "Could not reserve durable core transaction {}: {error}", marker.display()
            ))?;
        file.write_all(b"lazybuilder-core-sync-v1\n").map_err(|e| e.to_string())?;
        file.sync_all().map_err(|e| e.to_string())?;
    }

    let preparation = (|| -> Result<(), String> {
        for module in modules.iter_mut().filter(|module| module.needs_update) {
            module.stage()?;
        }
        for module in modules.iter_mut().filter(|module| module.needs_update) {
            module.backup_current()?;
        }
        Ok(())
    })();
    if let Err(error) = preparation {
        // Nothing has been published to the active plugin directory yet.
        // All transient files passed the CREATE_NEW preflight for this attempt.
        cleanup_staged(&modules);
        if let Some(marker) = &pending_marker {
            fs::remove_file(marker).map_err(|remove_error| format!(
                "Core preparation failed ({error}); could not clear its recovery marker {}: {remove_error}",
                marker.display()
            ))?;
        }
        return Err(error);
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
            if rollback_errors.is_empty() {
                if let Some(marker) = &pending_marker {
                    // On a verified rollback, clear only our operation marker.
                    // Staging can then be retired without losing old JARs.
                    fs::remove_file(marker).map_err(|remove_error| format!(
                        "Core module update failed ({error}), JARs were restored, but recovery marker {} could not be removed: {remove_error}",
                        marker.display()
                    ))?;
                }
                cleanup_staged(&modules);
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
        pending_marker,
    })
}

/// Removes obsolete/disabled copies of LazyBuilder-owned core modules after the
/// canonical bundled pair has been published successfully. Core modules have a
/// single runtime owner and must never coexist as multiple Paper plugin JARs.
pub fn remove_stale_core_jars(workspace: &Path) -> Result<(), String> {
    let plugins = workspace.join("server").join("plugins");
    let disabled = workspace.join("tools").join("lazybuilder").join("disabled-plugins");
    let legacy = workspace.join("server").join("plugins-disabled");
    let backups = workspace.join("tools").join("lazybuilder").join("plugin-backups");
    paths::ensure_existing_owned_directory(workspace, &plugins, "core plugin directory")?;
    paths::ensure_existing_owned_directory(workspace, &disabled, "disabled plugin directory")?;
    paths::ensure_existing_owned_directory(workspace, &legacy, "legacy disabled plugin directory")?;
    paths::ensure_owned_directory(workspace, &backups, "core recovery directory")?;
    retire_obsolete_core(&plugins, &backups, "active", true)?;
    retire_obsolete_core(&disabled, &backups, "disabled", false)?;
    retire_obsolete_core(&legacy, &backups, "legacy", false)
}

fn retire_obsolete_core(directory: &Path, backups: &Path, origin: &str, keep_canonical: bool) -> Result<(), String> {
    if !directory.is_dir() { return Ok(()); }
    for entry in fs::read_dir(directory).map_err(|e| e.to_string())? {
        let source = entry.map_err(|e| e.to_string())?.path();
        let Some(name) = source.file_name().and_then(|value| value.to_str()) else { continue; };
        let lower = name.to_ascii_lowercase();
        if !lower.ends_with(".jar") || !(lower.starts_with("world-manager-") || lower.starts_with("utilities-manager-")) {
            continue;
        }
        if keep_canonical && (name.eq_ignore_ascii_case(WORLD_FILE_NAME) || name.eq_ignore_ascii_case(UTILITIES_FILE_NAME)) {
            continue;
        }
        // A filename resemblance never grants permission to destroy user data.
        require_regular_file(&source, "obsolete core JAR")?;
        let retired = backups.join(format!("retired-{origin}-{name}"));
        if entry_exists(&retired)? {
            return Err(format!("Retired core JAR {} already exists; {} was preserved", retired.display(), source.display()));
        }
        fs::rename(&source, &retired).map_err(|error|
            format!("Could not preserve obsolete core JAR {} at {}: {error}", source.display(), retired.display()))?;
    }
    Ok(())
}

/// Durable core-sync uncertainty is a repair blocker, not an absent JAR.
pub fn sync_recovery_pending(workspace: &Path) -> Result<bool, String> {
    let marker = workspace.join("tools").join("lazybuilder")
        .join("plugin-backups").join(".core-sync-pending");
    entry_exists(&marker)
}

pub fn canonical_core_pair_ready(workspace: &Path) -> bool {
    // An interrupted transaction cannot be reported Ready even when the
    // newly published JAR filenames are present and readable.
    if !matches!(sync_recovery_pending(workspace), Ok(false)) { return false; }
    let plugins = workspace.join("server").join("plugins");
    [WORLD_FILE_NAME, UTILITIES_FILE_NAME]
        .iter()
        .all(|name| require_regular_file(&plugins.join(name), "canonical core JAR").is_ok())
}

#[derive(Debug)]
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
        require_regular_file(&source, "bundled core source")?;
        let target_exists = entry_exists(&target)?;
        if target_exists {
            require_regular_file(&target, "core module target")?;
        }
        let needs_update = !target_exists || !files_equal(&source, &target)?;
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
        copy_verified_new(&self.source, &self.staged)
    }

    fn backup_current(&mut self) -> Result<(), String> {
        self.had_target = entry_exists(&self.target)?;
        if !self.had_target {
            return Ok(());
        }

        let incoming = self.backup.with_extension("previous.incoming");
        copy_verified_new(&self.target, &incoming)
    }

    fn promote_backup(&self) -> Result<(), String> {
        if !self.had_target { return Ok(()); }
        let incoming = self.backup.with_extension("previous.incoming");
        replace_file(&incoming, &self.backup).map_err(|error| format!(
            "Core JAR committed but prior rollback backup {} could not be finalized: {error}. Core recovery marker was preserved.",
            self.backup.display()
        ))
    }

    fn commit(&mut self) -> Result<(), String> {
        // Never delete the existing runnable JAR before the verified candidate
        // has been published; replace_file keeps the old bytes in a swap.
        replace_file(&self.staged, &self.target)
            .map_err(|error| format!("Could not publish core module {}: {error}", self.target.display()))
    }

    fn rollback(&mut self) -> Result<(), String> {
        if self.had_target {
            let transaction_backup = self.backup.with_extension("previous.incoming");
            require_regular_file(&transaction_backup, "core transaction rollback backup")?;
            let restore = self.target.with_extension("jar.restore");
            copy_verified_new(&transaction_backup, &restore)?;
            replace_file(&restore, &self.target)?;
            if !files_equal(&transaction_backup, &self.target)? {
                return Err(format!(
                    "Restored core JAR does not match transaction backup {}; recovery copy retained",
                    transaction_backup.display()
                ));
            }
            return Ok(());
        }
        if entry_exists(&self.target)? {
            require_regular_file(&self.target, "newly published core JAR")?;
            if !files_equal(&self.source, &self.target)? {
                return Err(format!(
                    "New core JAR {} changed after publication; refusing rollback deletion",
                    self.target.display()
                ));
            }
            fs::remove_file(&self.target).map_err(|e| e.to_string())?;
        }
        Ok(())
    }
}

fn cleanup_staged(modules: &[ModuleInstall]) {
    // A module with no change never staged anything in this transaction.
    // Its pre-existing .incoming files can be the only surviving user recovery
    // evidence and must never be removed as collateral cleanup.
    for module in modules.iter().filter(|module| module.needs_update) {
        if module.staged.exists() {
            let _ = fs::remove_file(&module.staged);
        }
        let incoming_backup = module.backup.with_extension("previous.incoming");
        if incoming_backup.exists() {
            let _ = fs::remove_file(incoming_backup);
        }
    }
}

fn replace_file(source: &Path, destination: &Path) -> Result<(), String> {
    require_regular_file(source, "staged core JAR")?;
    let previous = destination.with_extension("previous.swap");
    if entry_exists(&previous)? {
        return Err(format!(
            "Unresolved core JAR swap at {}; existing recovery data was preserved",
            previous.display()
        ));
    }
    if entry_exists(destination)? {
        require_regular_file(destination, "current core JAR")?;
        fs::rename(destination, &previous).map_err(|e| e.to_string())?;
        match fs::rename(source, destination) {
            Ok(()) => {
                // Publication is complete; losing cleanup cannot undo commit.
                let _ = fs::remove_file(previous);
                Ok(())
            }
            Err(error) => match fs::rename(&previous, destination) {
                Ok(()) => Err(format!("Could not publish core JAR; old JAR restored: {error}")),
                Err(restore_error) => Err(format!(
                    "Core JAR publication failed ({error}) and rollback failed ({restore_error}); recovery preserved at {}",
                    previous.display()
                )),
            },
        }
    } else {
        fs::rename(source, destination).map_err(|e| e.to_string())
    }
}

fn entry_exists(path: &Path) -> Result<bool, String> {
    match fs::symlink_metadata(path) {
        Ok(_) => Ok(true),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(false),
        Err(error) => Err(error.to_string()),
    }
}

fn require_regular_file(path: &Path, label: &str) -> Result<(), String> {
    // Reuse the Launcher filesystem owner, including Windows reparse-point
    // rejection. Core staging and stale-JAR retirement must have the same
    // path-safety semantics as workspace metadata persistence.
    persistence::safe_path::ensure_regular_file(path, label)
}

fn copy_verified_new(source: &Path, destination: &Path) -> Result<(), String> {
    require_regular_file(source, "core copy source")?;
    let mut input = fs::File::open(source).map_err(|e| e.to_string())?;
    let mut output = OpenOptions::new().write(true).create_new(true).open(destination)
        .map_err(|error| format!(
            "Core staging path {} could not be reserved; recovery data was preserved: {error}",
            destination.display()
        ))?;
    let copy_result = std::io::copy(&mut input, &mut output)
        .and_then(|_| output.sync_all()).map_err(|e| e.to_string());
    drop(output);
    if let Err(error) = copy_result {
        let _ = fs::remove_file(destination);
        return Err(error);
    }
    if !files_equal(source, destination)? {
        return Err(format!(
            "Core staged-copy verification failed at {}; preserve both copies",
            destination.display()
        ));
    }
    Ok(())
}

fn resolve_source(resource_dir: Option<&Path>, file_name: &str) -> Result<PathBuf, String> {
    let resource_dir = resource_dir.ok_or_else(|| {
        format!("LazyBuilder core module {file_name} is unavailable because the packaged resource directory could not be resolved.")
    })?;
    let bundled = resource_dir.join("resources").join("core").join(file_name);
    if bundled.is_file() {
        return Ok(bundled);
    }
    Err(format!(
        "LazyBuilder core module {file_name} is unavailable. Runtime-ready Launcher builds must bundle the tested core pair."
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
        let resolved = resolve_source(Some(&root), WORLD_FILE_NAME).unwrap();
        assert_eq!(resolved, bundled);
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn finalized_transaction_replaces_core_modules_and_keeps_previous_backup() {
        let resource_root = test_root("core-sync-resources");
        let workspace = test_root("core-sync-workspace");
        resource_tree(&resource_root, b"new-world", b"new-utilities");
        let plugins = workspace.join("server").join("plugins");
        fs::create_dir_all(&plugins).unwrap();
        fs::write(plugins.join(WORLD_FILE_NAME), b"old-world").unwrap();
        fs::write(plugins.join(UTILITIES_FILE_NAME), b"old-utilities").unwrap();
        begin_sync(&workspace, Some(&resource_root)).unwrap().finalize().unwrap();
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
    fn aborted_core_sync_keeps_previous_valid_rollback_snapshots() {
        let resource_root = test_root("core-keep-prior-resource");
        let workspace = test_root("core-keep-prior-workspace");
        resource_tree(&resource_root, b"new-world", b"new-utilities");
        let plugins = workspace.join("server").join("plugins");
        let backups = workspace.join("tools").join("lazybuilder").join("plugin-backups");
        fs::create_dir_all(&plugins).unwrap();
        fs::create_dir_all(&backups).unwrap();
        fs::write(plugins.join(WORLD_FILE_NAME), b"old-world").unwrap();
        fs::write(plugins.join(UTILITIES_FILE_NAME), b"old-utilities").unwrap();
        let earlier = backups.join(format!("{WORLD_FILE_NAME}.previous"));
        fs::write(&earlier, b"older-valid-rollback").unwrap();

        {
            let _transaction = begin_sync(&workspace, Some(&resource_root)).unwrap();
            assert_eq!(fs::read(&earlier).unwrap(), b"older-valid-rollback");
        }
        assert_eq!(fs::read(&earlier).unwrap(), b"older-valid-rollback");
        assert_eq!(fs::read(plugins.join(WORLD_FILE_NAME)).unwrap(), b"old-world");
        assert!(!backups.join(".core-sync-pending").exists());
        let _ = fs::remove_dir_all(resource_root);
        let _ = fs::remove_dir_all(workspace);
    }

    #[test]
    fn finalized_core_sync_promotes_only_committed_previous_jars() {
        let resource_root = test_root("core-promote-resource");
        let workspace = test_root("core-promote-workspace");
        resource_tree(&resource_root, b"new-world", b"new-utilities");
        let plugins = workspace.join("server").join("plugins");
        let backups = workspace.join("tools").join("lazybuilder").join("plugin-backups");
        fs::create_dir_all(&plugins).unwrap();
        fs::create_dir_all(&backups).unwrap();
        fs::write(plugins.join(WORLD_FILE_NAME), b"old-world").unwrap();
        fs::write(plugins.join(UTILITIES_FILE_NAME), b"old-utilities").unwrap();
        let previous = backups.join(format!("{WORLD_FILE_NAME}.previous"));
        fs::write(&previous, b"ancient-history").unwrap();

        let transaction = begin_sync(&workspace, Some(&resource_root)).unwrap();
        assert_eq!(fs::read(&previous).unwrap(), b"ancient-history");
        transaction.finalize().unwrap();
        assert_eq!(fs::read(&previous).unwrap(), b"old-world");
        assert_eq!(fs::read(plugins.join(WORLD_FILE_NAME)).unwrap(), b"new-world");
        assert!(!backups.join(".core-sync-pending").exists());
        let _ = fs::remove_dir_all(resource_root);
        let _ = fs::remove_dir_all(workspace);
    }

    #[test]
    fn unrelated_core_staging_survives_another_module_update() {
        let resources = test_root("core-unrelated-stage-resources");
        let workspace = test_root("core-unrelated-stage-workspace");
        resource_tree(&resources, b"same-world", b"new-utility");
        let plugins = workspace.join("server").join("plugins");
        let backups = workspace.join("tools").join("lazybuilder").join("plugin-backups");
        fs::create_dir_all(&plugins).unwrap();
        fs::create_dir_all(&backups).unwrap();
        fs::write(plugins.join(WORLD_FILE_NAME), b"same-world").unwrap();
        fs::write(plugins.join(UTILITIES_FILE_NAME), b"old-utility").unwrap();
        let pending = plugins.join(WORLD_FILE_NAME).with_extension("jar.incoming");
        let prior_backup = backups.join(format!("{WORLD_FILE_NAME}.previous")).with_extension("previous.incoming");
        fs::write(&pending, b"UNRELATED RECOVERY").unwrap();
        fs::write(&prior_backup, b"UNRELATED BACKUP").unwrap();

        begin_sync(&workspace, Some(&resources)).unwrap().finalize().unwrap();
        assert_eq!(fs::read(&pending).unwrap(), b"UNRELATED RECOVERY");
        assert_eq!(fs::read(&prior_backup).unwrap(), b"UNRELATED BACKUP");
        assert_eq!(fs::read(plugins.join(UTILITIES_FILE_NAME)).unwrap(), b"new-utility");
        let _ = fs::remove_dir_all(resources);
        let _ = fs::remove_dir_all(workspace);
    }

    #[test]
    fn preexisting_core_staging_is_preserved_before_sync() {
        let resource_root = test_root("core-staging-conflict-resource");
        let workspace = test_root("core-staging-conflict-workspace");
        resource_tree(&resource_root, b"new-world", b"new-utilities");
        let plugins = workspace.join("server").join("plugins");
        fs::create_dir_all(&plugins).unwrap();
        fs::write(plugins.join(WORLD_FILE_NAME), b"old-world").unwrap();
        fs::write(plugins.join(UTILITIES_FILE_NAME), b"old-utilities").unwrap();
        let staged = plugins.join(WORLD_FILE_NAME).with_extension("jar.incoming");
        fs::write(&staged, b"ONLY RECOVERY COPY").unwrap();

        assert!(begin_sync(&workspace, Some(&resource_root)).is_err());
        assert_eq!(fs::read(&staged).unwrap(), b"ONLY RECOVERY COPY");
        assert_eq!(fs::read(plugins.join(WORLD_FILE_NAME)).unwrap(), b"old-world");
        let _ = fs::remove_dir_all(resource_root);
        let _ = fs::remove_dir_all(workspace);
    }

    #[test]
    fn begin_sync_preflights_both_targets_before_replacing_either_module() {
        let resource_root = test_root("core-preflight-resources");
        let workspace = test_root("core-preflight-workspace");
        resource_tree(&resource_root, b"new-world", b"new-utilities");
        let plugins = workspace.join("server").join("plugins");
        fs::create_dir_all(&plugins).unwrap();
        fs::write(plugins.join(WORLD_FILE_NAME), b"old-world").unwrap();
        fs::create_dir_all(plugins.join(UTILITIES_FILE_NAME)).unwrap();
        let error = begin_sync(&workspace, Some(&resource_root)).unwrap_err();
        assert!(error.contains("not a file"));
        assert_eq!(fs::read(plugins.join(WORLD_FILE_NAME)).unwrap(), b"old-world");
        let _ = fs::remove_dir_all(resource_root);
        let _ = fs::remove_dir_all(workspace);
    }

    #[test]
    fn stale_core_jars_are_removed_but_canonical_pair_is_preserved() {
        let workspace = test_root("core-stale-cleanup");
        let plugins = workspace.join("server").join("plugins");
        let disabled = workspace.join("tools").join("lazybuilder").join("disabled-plugins");
        fs::create_dir_all(&plugins).unwrap();
        fs::create_dir_all(&disabled).unwrap();
        fs::write(plugins.join(WORLD_FILE_NAME), b"current-world").unwrap();
        fs::write(plugins.join(UTILITIES_FILE_NAME), b"current-utilities").unwrap();
        fs::write(plugins.join("World-Manager-0.0.9.jar"), b"stale-world").unwrap();
        fs::write(disabled.join(UTILITIES_FILE_NAME), b"disabled-current-name").unwrap();
        fs::write(disabled.join("Utilities-Manager-0.0.9.jar"), b"stale-utilities").unwrap();
        fs::write(plugins.join("Some-Plugin-1.0.jar"), b"third-party").unwrap();

        remove_stale_core_jars(&workspace).unwrap();

        assert!(plugins.join(WORLD_FILE_NAME).is_file());
        assert!(plugins.join(UTILITIES_FILE_NAME).is_file());
        assert!(!plugins.join("World-Manager-0.0.9.jar").exists());
        assert!(!disabled.join(UTILITIES_FILE_NAME).exists());
        assert!(!disabled.join("Utilities-Manager-0.0.9.jar").exists());
        let retired = workspace.join("tools").join("lazybuilder").join("plugin-backups");
        assert_eq!(fs::read(retired.join("retired-active-World-Manager-0.0.9.jar")).unwrap(), b"stale-world");
        assert_eq!(fs::read(retired.join("retired-disabled-Utilities-Manager-0.0.9.jar")).unwrap(), b"stale-utilities");
        assert_eq!(fs::read(retired.join(format!("retired-disabled-{UTILITIES_FILE_NAME}"))).unwrap(), b"disabled-current-name");
        assert!(plugins.join("Some-Plugin-1.0.jar").is_file());
        let _ = fs::remove_dir_all(workspace);
    }    #[test]
    fn interrupted_sync_marker_blocks_even_identical_bundled_modules() {
        let resources = test_root("core-noop-marker-resources");
        let workspace = test_root("core-noop-marker-workspace");
        resource_tree(&resources, b"world", b"utilities");
        let plugins = workspace.join("server").join("plugins");
        let backup = workspace.join("tools").join("lazybuilder").join("plugin-backups");
        fs::create_dir_all(&plugins).unwrap();
        fs::create_dir_all(&backup).unwrap();
        fs::write(plugins.join(WORLD_FILE_NAME), b"world").unwrap();
        fs::write(plugins.join(UTILITIES_FILE_NAME), b"utilities").unwrap();
        let pending = backup.join(".core-sync-pending");
        fs::write(&pending, b"unresolved").unwrap();

        assert!(begin_sync(&workspace, Some(&resources)).unwrap_err().contains("Unresolved core module transaction"));
        assert_eq!(fs::read(&pending).unwrap(), b"unresolved");
        let _ = fs::remove_dir_all(resources);
        let _ = fs::remove_dir_all(workspace);
    }

    #[test]
    fn stale_core_collision_preserves_both_original_and_recovery_bytes() {
        let workspace = test_root("core-retire-collision");
        let plugins = workspace.join("server").join("plugins");
        let backups = workspace.join("tools").join("lazybuilder").join("plugin-backups");
        fs::create_dir_all(&plugins).unwrap();
        fs::create_dir_all(&backups).unwrap();
        let stale = plugins.join("World-Manager-0.0.9.jar");
        let retired = backups.join("retired-active-World-Manager-0.0.9.jar");
        fs::write(&stale, b"unretired").unwrap();
        fs::write(&retired, b"EXISTING USER DATA").unwrap();
        assert!(remove_stale_core_jars(&workspace).is_err());
        assert_eq!(fs::read(&stale).unwrap(), b"unretired");
        assert_eq!(fs::read(&retired).unwrap(), b"EXISTING USER DATA");
        let _ = fs::remove_dir_all(workspace);
    }

    #[test]
    fn pending_core_recovery_blocks_readiness_even_with_both_jars_present() {
        let workspace = test_root("core-recovery-readiness");
        let plugins = workspace.join("server").join("plugins");
        let backup_dir = workspace.join("tools").join("lazybuilder").join("plugin-backups");
        fs::create_dir_all(&plugins).unwrap();
        fs::create_dir_all(&backup_dir).unwrap();
        fs::write(plugins.join(WORLD_FILE_NAME), b"world").unwrap();
        fs::write(plugins.join(UTILITIES_FILE_NAME), b"utilities").unwrap();
        assert!(canonical_core_pair_ready(&workspace));
        let marker = backup_dir.join(".core-sync-pending");
        fs::write(&marker, b"uncertain old sync").unwrap();
        assert!(sync_recovery_pending(&workspace).unwrap());
        assert!(!canonical_core_pair_ready(&workspace));
        assert_eq!(fs::read(&marker).unwrap(), b"uncertain old sync");
        let _ = fs::remove_dir_all(workspace);
    }

    #[test]
    fn only_canonical_pair_passes_server_health_readiness() {
        let workspace = test_root("core-readiness");
        let plugins = workspace.join("server").join("plugins");
        fs::create_dir_all(&plugins).unwrap();
        fs::write(plugins.join("World-Manager-0.0.9.jar"), b"old-world").unwrap();
        fs::write(plugins.join("Utilities-Manager-0.0.9.jar"), b"old-utility").unwrap();
        assert!(!canonical_core_pair_ready(&workspace));
        fs::write(plugins.join(WORLD_FILE_NAME), b"current-world").unwrap();
        fs::write(plugins.join(UTILITIES_FILE_NAME), b"current-utility").unwrap();
        assert!(canonical_core_pair_ready(&workspace));
        let _ = fs::remove_dir_all(workspace);
    }

    #[cfg(unix)]
    #[test]
    fn symlinked_core_artifact_is_not_published_or_retired() {
        use std::os::unix::fs::symlink;
        let resource_root = test_root("core-symlink-resource");
        let workspace = test_root("core-symlink-workspace");
        resource_tree(&resource_root, b"new-world", b"new-utilities");
        let plugins = workspace.join("server").join("plugins");
        fs::create_dir_all(&plugins).unwrap();
        let outside = workspace.join("important-user-file.txt");
        fs::write(&outside, b"PROTECTED").unwrap();
        symlink(&outside, plugins.join(WORLD_FILE_NAME)).unwrap();

        assert!(begin_sync(&workspace, Some(&resource_root)).is_err());
        assert_eq!(fs::read(&outside).unwrap(), b"PROTECTED");
        assert!(plugins.join(WORLD_FILE_NAME).is_symlink());
        let _ = fs::remove_dir_all(resource_root);
        let _ = fs::remove_dir_all(workspace);
    }

    #[test]
    fn owned_directory_guard_rejects_directory_outside_workspace() {
        let workspace = test_root("core-owned-root");
        let outside = test_root("core-owned-outside");
        fs::create_dir_all(&workspace).unwrap();
        fs::create_dir_all(&outside).unwrap();

        let error = paths::ensure_existing_owned_directory(&workspace, &outside, "test directory").unwrap_err();
        assert!(error.contains("outside the active workspace"));

        let _ = fs::remove_dir_all(workspace);
        let _ = fs::remove_dir_all(outside);
    }


}
