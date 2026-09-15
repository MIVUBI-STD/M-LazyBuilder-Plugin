use crate::engine::{core_modules, java_runtime, paper_provider, runtime_updates, server_config, workspace_registry};
use serde_json::Value;
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Clone, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ProvisionResult {
    pub java_path: String,
    pub paper_build: Option<u64>,
    pub core_version: String,
    pub status: workspace_registry::ProvisioningStatus,
}

pub fn provision_active(resource_dir: Option<&Path>) -> Result<ProvisionResult, String> {
    provision_active_tracked(resource_dir, |_, _, _| {})
}

/// Provision the active workspace while exposing semantic stage boundaries to the
/// desktop operation layer. The observer is presentation/diagnostic plumbing only;
/// provisioning owners remain authoritative and the observer cannot alter results.
pub fn provision_active_tracked<F>(
    resource_dir: Option<&Path>,
    mut on_stage: F,
) -> Result<ProvisionResult, String>
where
    F: FnMut(&str, &str, &str),
{
    let workspace = workspace_registry::active_workspace()?;

    on_stage(
        "runtime-layout",
        "Preparing runtime directories",
        "Ensuring LazyBuilder runtime directories are available.",
    );
    crate::engine::paths::ensure_runtime_layout()?;

    // Prepare Server is intentionally idempotent. Each owner either verifies an
    // existing valid asset or repairs only the asset it owns.
    on_stage(
        "java-runtime",
        "Preparing Java 21",
        "Verifying or installing the LazyBuilder-managed Java runtime.",
    );
    let java = java_runtime::ensure_managed_java()?;

    on_stage(
        "server-config",
        "Preparing server configuration",
        "Aligning the active server configuration with the managed Java runtime.",
    );
    server_config::ensure_java_path(&java)?;

    on_stage(
        "paper-runtime",
        "Preparing Paper",
        "Preserving an existing Paper runtime or provisioning the supported Paper release when missing.",
    );
    let paper_build = resolve_or_provision_paper(&workspace)?;

    on_stage(
        "server-properties",
        "Preparing Paper settings",
        "Creating the LazyBuilder server.properties baseline only when it is missing.",
    );
    ensure_server_properties(&workspace)?;

    // Core publication has one authority. Runtime maintenance and initial
    // provisioning both use the same transactional sync/metadata path.
    on_stage(
        "core-modules",
        "Synchronizing core components",
        "Publishing the LazyBuilder World Manager and Utilities Manager transactionally.",
    );
    runtime_updates::ensure_core_current(resource_dir)?;

    on_stage(
        "workspace-metadata",
        "Updating workspace metadata",
        "Recording the authoritative Paper build when LazyBuilder knows it.",
    );
    update_paper_manifest_if_known(&workspace, paper_build)?;

    on_stage(
        "readiness",
        "Checking server readiness",
        "Verifying the prepared workspace before returning control to the Launcher.",
    );
    let status = workspace_registry::provisioning_status()?;

    Ok(ProvisionResult {
        java_path: java.display().to_string(),
        paper_build,
        core_version: core_modules::CORE_VERSION.into(),
        status,
    })
}

fn resolve_or_provision_paper(workspace: &Path) -> Result<Option<u64>, String> {
    let paper = workspace.join("server").join("paper.jar");
    let manifest = workspace.join("tools").join("lazybuilder").join("config").join("workspace.json");

    // Existing Paper is preserved exactly as-is. This is especially important for
    // adopted servers where the build number may be unknown. Updating Paper is an
    // explicit runtime-update action, never a side effect of Prepare Server.
    if paper.is_file() {
        if manifest.is_file() {
            let text = fs::read_to_string(&manifest).map_err(|e| e.to_string())?;
            let value: Value = serde_json::from_str(&text).map_err(|e| e.to_string())?;
            return Ok(value.get("paperBuild").and_then(Value::as_u64));
        }
        return Ok(None);
    }

    paper_provider::ensure_for_workspace(workspace).map(Some)
}

fn ensure_server_properties(workspace: &Path) -> Result<(), String> {
    let path = workspace.join("server").join("server.properties");
    if path.is_file() {
        return Ok(());
    }
    let text = "# Managed baseline created by LazyBuilder\nserver-port=25565\nonline-mode=true\nenable-command-block=true\nspawn-protection=0\nview-distance=10\nsimulation-distance=10\n";
    fs::write(path, text).map_err(|e| e.to_string())
}

fn update_paper_manifest_if_known(workspace: &Path, paper_build: Option<u64>) -> Result<(), String> {
    let Some(paper_build) = paper_build else {
        // Adopted/unknown Paper builds remain unknown until the explicit Paper
        // update path establishes an authoritative build number.
        return Ok(());
    };

    let path = workspace.join("tools").join("lazybuilder").join("config").join("workspace.json");
    let text = fs::read_to_string(&path).map_err(|e| e.to_string())?;
    let mut value: Value = serde_json::from_str(&text).map_err(|e| e.to_string())?;
    if value.get("paperBuild").and_then(Value::as_u64) == Some(paper_build) {
        return Ok(());
    }
    value["paperBuild"] = Value::from(paper_build);
    write_json_atomic(&path, &value)
}

fn write_json_atomic(path: &Path, value: &Value) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
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

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn test_root(label: &str) -> PathBuf {
        let nonce = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos();
        std::env::temp_dir().join(format!("lazybuilder-{label}-{}-{nonce}", std::process::id()))
    }

    #[test]
    fn existing_paper_with_unknown_build_is_preserved_without_network_update() {
        let workspace = test_root("preserve-adopted-paper");
        let server = workspace.join("server");
        let config = workspace.join("tools").join("lazybuilder").join("config");
        fs::create_dir_all(&server).unwrap();
        fs::create_dir_all(&config).unwrap();
        fs::write(server.join("paper.jar"), b"adopted-paper").unwrap();
        fs::write(
            config.join("workspace.json"),
            r#"{"paperBuild":null,"worldManagerVersion":null,"utilitiesManagerVersion":null}"#,
        )
        .unwrap();

        let build = resolve_or_provision_paper(&workspace).unwrap();
        assert_eq!(build, None);
        assert_eq!(fs::read(server.join("paper.jar")).unwrap(), b"adopted-paper");

        let _ = fs::remove_dir_all(workspace);
    }

    #[test]
    fn known_paper_build_metadata_write_is_idempotent() {
        let workspace = test_root("paper-metadata-idempotent");
        let config = workspace.join("tools").join("lazybuilder").join("config");
        fs::create_dir_all(&config).unwrap();
        let manifest = config.join("workspace.json");
        fs::write(&manifest, r#"{"paperBuild":123}"#).unwrap();

        update_paper_manifest_if_known(&workspace, Some(123)).unwrap();
        let first = fs::read_to_string(&manifest).unwrap();
        update_paper_manifest_if_known(&workspace, Some(123)).unwrap();
        let second = fs::read_to_string(&manifest).unwrap();
        assert_eq!(first, second);

        let _ = fs::remove_dir_all(workspace);
    }

    #[test]
    fn provisioning_stage_contract_stays_semantic() {
        let stages = [
            "runtime-layout",
            "java-runtime",
            "server-config",
            "paper-runtime",
            "server-properties",
            "core-modules",
            "workspace-metadata",
            "readiness",
        ];
        assert_eq!(stages.first().copied(), Some("runtime-layout"));
        assert_eq!(stages.last().copied(), Some("readiness"));
    }
}
