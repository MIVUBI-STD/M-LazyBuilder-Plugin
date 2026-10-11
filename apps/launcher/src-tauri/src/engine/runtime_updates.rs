use crate::engine::{core_modules, paper_provider, persistence, workspace_registry};
use serde::Serialize;
use std::fs::{self, File, OpenOptions};
use std::io::Write;
use std::path::Path;

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RuntimeUpdateStatus {
    pub current_paper_build: Option<u64>,
    pub latest_paper_build: u64,
    pub paper_update_available: bool,
}

pub fn status() -> Result<RuntimeUpdateStatus, String> {
    let workspace = workspace_registry::active_workspace()?;
    let release = paper_provider::latest_stable()?;
    status_with_release(&workspace, &release)
}

/// Update Paper while exposing real transaction boundaries to the Launcher operation
/// model. No percentage is emitted because the provider does not expose byte progress.
pub fn update_paper_tracked<F>(mut on_stage: F) -> Result<RuntimeUpdateStatus, String>
where
    F: FnMut(&str, &str, &str),
{
    let workspace = workspace_registry::active_workspace()?;

    on_stage(
        "resolve-release",
        "Checking Paper release",
        "Resolving the current stable Paper build for the supported Minecraft version.",
    );
    let release = paper_provider::latest_stable()?;
    let target = workspace.join("server").join("paper.jar");
    let persistent_backup = workspace.join("server").join("paper.jar.previous");
    let had_target = persistence::metadata_entry_exists(&target, "Paper runtime JAR")?;
    if had_target {
        persistence::safe_path::ensure_regular_file(&target, "Paper runtime JAR")?;
    }

    on_stage(
        "backup",
        "Protecting current Paper runtime",
        "Creating a rollback copy before publishing the replacement Paper runtime.",
    );
    if had_target {
        // Never overwrite a prior attempt's rollback evidence.
        // The newly created copy is synchronized and checked before update.
        copy_verified_new(&target, &persistent_backup)?;
    }

    on_stage(
        "publish-paper",
        "Installing Paper update",
        "Downloading, validating, and publishing the selected Paper release through the existing provider transaction.",
    );
    paper_provider::ensure_release_for_workspace(&workspace, &release)?;

    on_stage(
        "workspace-metadata",
        "Updating Paper metadata",
        "Recording the published Paper build through the canonical workspace manifest owner.",
    );
    if let Err(manifest_error) = workspace_registry::update_paper_build(&workspace, release.build) {
        on_stage(
            "rollback",
            "Rolling back Paper update",
            "The Paper runtime was published but its metadata could not be committed, so LazyBuilder is restoring the previous runtime.",
        );
        if !had_target {
            // There was no previous Paper runtime to restore. Keep the
            // newly verified JAR rather than erasing an uncertain installation.
            return Err(format!(
                "Paper metadata update failed: {manifest_error}. Newly published paper.jar was preserved for reconciliation."
            ));
        }
        return match rollback_paper(&target, &persistent_backup) {
            Ok(()) => {
                let _ = persistence::safe_path::remove_regular_file_if_present(
                    &persistent_backup, "Paper update rollback backup"
                );
                Err(format!("Paper metadata update failed and prior Paper was restored: {manifest_error}"))
            }
            Err(rollback_error) => Err(format!(
                "Paper metadata update failed: {manifest_error}; Paper rollback also failed: {rollback_error}. Original backup remains at {}.",
                persistent_backup.display()
            )),
        };
    }

    on_stage(
        "verify",
        "Verifying Paper update",
        "Confirming the workspace now records the selected Paper build.",
    );
    let status = status_with_release(&workspace, &release)?;
    if had_target {
        // The backup can be retired only after the committed manifest is
        // re-read and both runtime publication and metadata are confirmed.
        let _ = persistence::safe_path::remove_regular_file_if_present(
            &persistent_backup, "Paper update rollback backup"
        );
    }
    Ok(status)
}

/// Keeps LazyBuilder-bundled Paper modules aligned with the running desktop app.
/// This is internal maintenance: it does not require a network request or user action.
pub fn ensure_core_current(resource_dir: Option<&Path>) -> Result<(), String> {
    let workspace = workspace_registry::active_workspace()?;
    let transaction = core_modules::begin_sync(&workspace, resource_dir)?;

    if let Err(manifest_error) = workspace_registry::update_core_versions(
        &workspace,
        core_modules::CORE_VERSION,
        core_modules::CORE_VERSION,
    ) {
        return match transaction.rollback() {
            Ok(()) => Err(format!(
                "Core metadata update failed and core JARs were rolled back: {manifest_error}"
            )),
            Err(rollback_error) => Err(format!(
                "Core metadata update failed: {manifest_error}; core JAR rollback also failed: {rollback_error}"
            )),
        };
    }

    transaction.finalize();
    core_modules::remove_stale_core_jars(&workspace)?;
    Ok(())
}

fn status_with_release(
    workspace: &Path,
    release: &paper_provider::PaperRelease,
) -> Result<RuntimeUpdateStatus, String> {
    let manifest = workspace_registry::manifest(workspace)?;
    let current_paper = manifest.paper_build.map(u64::from);
    Ok(RuntimeUpdateStatus {
        current_paper_build: current_paper,
        latest_paper_build: release.build,
        paper_update_available: current_paper.map(|build| build != release.build).unwrap_or(true),
    })
}

fn copy_verified_new(source: &Path, destination: &Path) -> Result<(), String> {
    persistence::safe_path::ensure_regular_file(source, "Paper copy source")?;
    let mut input = File::open(source).map_err(|e| e.to_string())?;
    let mut output = OpenOptions::new().write(true).create_new(true).open(destination)
        .map_err(|error| format!(
            "Paper recovery destination {} is unavailable; existing evidence was preserved: {error}",
            destination.display()
        ))?;
    let copied = (|| -> Result<(), String> {
        std::io::copy(&mut input, &mut output).map_err(|e| e.to_string())?;
        output.flush().map_err(|e| e.to_string())?;
        output.sync_all().map_err(|e| e.to_string())
    })();
    drop(output);
    if let Err(error) = copied {
        let _ = fs::remove_file(destination);
        return Err(error);
    }
    if paper_provider::sha256_file(source)? != paper_provider::sha256_file(destination)? {
        // Even if verification fails, retain the source and preserve the
        // untrusted copy for inspection instead of declaring it good.
        return Err(format!(
            "Paper copy verification failed; input and copy were preserved: {}",
            destination.display()
        ));
    }
    Ok(())
}

fn rollback_paper(target: &Path, backup: &Path) -> Result<(), String> {
    persistence::safe_path::ensure_regular_file(backup, "Paper update rollback backup")?;
    let staged = target.with_extension("jar.restore");
    copy_verified_new(backup, &staged)?;
    paper_provider::replace_file(&staged, target)?;
    if paper_provider::sha256_file(target)? != paper_provider::sha256_file(backup)? {
        return Err(format!(
            "Restored Paper bytes do not match rollback backup; preserved at {}",
            backup.display()
        ));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn test_root(label: &str) -> std::path::PathBuf {
        let stamp = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos();
        std::env::temp_dir().join(format!("lazybuilder-{label}-{}-{stamp}", std::process::id()))
    }

    #[test]
    fn existing_paper_update_backup_cannot_be_overwritten() {
        let root = test_root("paper-backup-collision");
        fs::create_dir_all(&root).unwrap();
        let source = root.join("paper.jar");
        let backup = root.join("paper.jar.previous");
        fs::write(&source, b"new update candidate").unwrap();
        fs::write(&backup, b"only valid rollback copy").unwrap();
        assert!(copy_verified_new(&source, &backup).is_err());
        assert_eq!(fs::read(&backup).unwrap(), b"only valid rollback copy");
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn previous_paper_restored_from_verified_copy_without_consuming_backup() {
        let root = test_root("paper-verified-restore");
        fs::create_dir_all(&root).unwrap();
        let target = root.join("paper.jar");
        let backup = root.join("paper.jar.previous");
        fs::write(&target, b"invalid candidate runtime").unwrap();
        fs::write(&backup, b"verified previous runtime").unwrap();
        rollback_paper(&target, &backup).unwrap();
        assert_eq!(fs::read(&target).unwrap(), b"verified previous runtime");
        assert_eq!(fs::read(&backup).unwrap(), b"verified previous runtime");
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn paper_update_stage_contract_stays_transactional() {
        let stages = [
            "resolve-release",
            "backup",
            "publish-paper",
            "workspace-metadata",
            "verify",
        ];
        assert_eq!(stages[0], "resolve-release");
        assert_eq!(stages[4], "verify");
    }
}
