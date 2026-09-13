use crate::engine::{core_modules, paper_provider, workspace_registry};
use serde::Serialize;
use serde_json::Value;
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RuntimeUpdateStatus {
    pub current_paper_build: Option<u64>,
    pub latest_paper_build: u64,
    pub paper_update_available: bool,
    pub current_core_version: Option<String>,
    pub bundled_core_version: String,
    pub core_update_available: bool,
}

pub fn status() -> Result<RuntimeUpdateStatus, String> {
    let workspace = workspace_registry::active_workspace()?;
    let release = paper_provider::latest_stable()?;
    status_with_release(&workspace, &release)
}

pub fn update_paper() -> Result<RuntimeUpdateStatus, String> {
    let workspace = workspace_registry::active_workspace()?;
    let release = paper_provider::latest_stable()?;
    let target = workspace.join("server").join("paper.jar");
    let persistent_backup = workspace.join("server").join("paper.jar.previous");
    let had_target = target.is_file();

    if had_target {
        let _ = fs::remove_file(&persistent_backup);
        fs::copy(&target, &persistent_backup).map_err(|e| e.to_string())?;
    }

    paper_provider::ensure_release_for_workspace(&workspace, &release)?;
    if let Err(manifest_error) = update_manifest_field(&workspace, "paperBuild", Value::from(release.build)) {
        return match rollback_paper(&target, &persistent_backup, had_target) {
            Ok(()) => Err(format!(
                "Paper metadata update failed and paper.jar was rolled back: {manifest_error}"
            )),
            Err(rollback_error) => Err(format!(
                "Paper metadata update failed: {manifest_error}; paper.jar rollback also failed: {rollback_error}"
            )),
        };
    }

    status_with_release(&workspace, &release)
}

/// Internal compatibility maintenance for LazyBuilder-bundled Paper modules.
/// This is intentionally separate from Paper update discovery: keeping the app's
/// own core JARs current must not require a network request or a user decision.
pub fn ensure_core_current(resource_dir: Option<&Path>) -> Result<(), String> {
    let workspace = workspace_registry::active_workspace()?;
    sync_core_at(&workspace, resource_dir)
}

/// Temporary public wrapper retained for the current desktop contract. The UI
/// surface may remove this action once it no longer exposes bundled-core sync.
pub fn sync_core(resource_dir: Option<&Path>) -> Result<RuntimeUpdateStatus, String> {
    let workspace = workspace_registry::active_workspace()?;
    sync_core_at(&workspace, resource_dir)?;
    let release = paper_provider::latest_stable()?;
    status_with_release(&workspace, &release)
}

fn sync_core_at(workspace: &Path, resource_dir: Option<&Path>) -> Result<(), String> {
    let transaction = core_modules::begin_sync(workspace, resource_dir)?;

    let mut manifest = read_manifest(workspace)?;
    manifest["worldManagerVersion"] = Value::String(core_modules::CORE_VERSION.into());
    manifest["utilitiesManagerVersion"] = Value::String(core_modules::CORE_VERSION.into());

    if let Err(manifest_error) = write_json_atomic(&manifest_path(workspace), &manifest) {
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
    Ok(())
}

fn status_with_release(
    workspace: &Path,
    release: &paper_provider::PaperRelease,
) -> Result<RuntimeUpdateStatus, String> {
    let manifest = read_manifest(workspace)?;
    let current_paper = manifest.get("paperBuild").and_then(Value::as_u64);
    let world_version = manifest
        .get("worldManagerVersion")
        .and_then(Value::as_str)
        .map(str::to_string);
    let utilities_version = manifest
        .get("utilitiesManagerVersion")
        .and_then(Value::as_str)
        .map(str::to_string);
    let core_update_available = world_version.as_deref() != Some(core_modules::CORE_VERSION)
        || utilities_version.as_deref() != Some(core_modules::CORE_VERSION);
    let current_core_version = if world_version == utilities_version {
        world_version
    } else {
        None
    };

    Ok(RuntimeUpdateStatus {
        current_paper_build: current_paper,
        latest_paper_build: release.build,
        paper_update_available: current_paper.map(|build| build != release.build).unwrap_or(true),
        current_core_version,
        bundled_core_version: core_modules::CORE_VERSION.into(),
        core_update_available,
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

fn read_manifest(workspace: &Path) -> Result<Value, String> {
    let path = manifest_path(workspace);
    let text = fs::read_to_string(&path).map_err(|e| e.to_string())?;
    serde_json::from_str(&text).map_err(|e| e.to_string())
}

fn update_manifest_field(workspace: &Path, key: &str, value: Value) -> Result<(), String> {
    let path = manifest_path(workspace);
    let mut manifest = read_manifest(workspace)?;
    manifest[key] = value;
    write_json_atomic(&path, &manifest)
}

fn manifest_path(workspace: &Path) -> PathBuf {
    workspace
        .join("tools")
        .join("lazybuilder")
        .join("config")
        .join("workspace.json")
}

fn write_json_atomic(path: &Path, value: &Value) -> Result<(), String> {
    let temporary = PathBuf::from(format!("{}.tmp", path.display()));
    let backup = PathBuf::from(format!("{}.previous", path.display()));
    fs::write(
        &temporary,
        serde_json::to_string_pretty(value).map_err(|e| e.to_string())?,
    )
    .map_err(|e| e.to_string())?;
    if path.exists() {
        let _ = fs::remove_file(&backup);
        fs::rename(path, &backup).map_err(|e| e.to_string())?;
        match fs::rename(&temporary, path) {
            Ok(()) => {
                let _ = fs::remove_file(backup);
                Ok(())
            }
            Err(error) => {
                let _ = fs::rename(&backup, path);
                Err(error.to_string())
            }
        }
    } else {
        fs::rename(temporary, path).map_err(|e| e.to_string())
    }
}
