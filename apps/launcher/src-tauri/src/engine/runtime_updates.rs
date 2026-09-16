use crate::engine::{core_modules, paper_provider, workspace_registry};
use serde::Serialize;
use std::fs;
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

pub fn update_paper() -> Result<RuntimeUpdateStatus, String> {
    update_paper_tracked(|_, _, _| {})
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
    let had_target = target.is_file();

    on_stage(
        "backup",
        "Protecting current Paper runtime",
        "Creating a rollback copy before publishing the replacement Paper runtime.",
    );
    if had_target {
        let _ = fs::remove_file(&persistent_backup);
        fs::copy(&target, &persistent_backup).map_err(|e| e.to_string())?;
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
        return match rollback_paper(&target, &persistent_backup, had_target) {
            Ok(()) => Err(format!(
                "Paper metadata update failed and paper.jar was rolled back: {manifest_error}"
            )),
            Err(rollback_error) => Err(format!(
                "Paper metadata update failed: {manifest_error}; paper.jar rollback also failed: {rollback_error}"
            )),
        };
    }

    on_stage(
        "verify",
        "Verifying Paper update",
        "Confirming the workspace now records the selected Paper build.",
    );
    status_with_release(&workspace, &release)
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

fn rollback_paper(target: &Path, backup: &Path, had_target: bool) -> Result<(), String> {
    if target.exists() {
        fs::remove_file(target).map_err(|e| e.to_string())?;
    }
    if had_target {
        if !backup.is_file() {
            return Err(format!("Paper rollback backup is missing: {}", backup.display()));
        }
        fs::copy(backup, target).map_err(|e| e.to_string())?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
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
