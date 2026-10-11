use crate::engine::{core_modules, java_runtime, paper_provider, persistence, runtime_updates, server_config, workspace_registry};
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::Path;
#[cfg(test)]
use std::path::PathBuf;

#[derive(Clone, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ProvisionResult {
    pub java_path: String,
    pub paper_build: Option<u64>,
    pub core_version: String,
    pub status: workspace_registry::ProvisioningStatus,
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
        if persistence::metadata_entry_exists(&manifest, "workspace manifest")? {
            // WorkspaceRegistry is the sole schema-validation and recovery owner.
            return Ok(workspace_registry::manifest(workspace)?.paper_build.map(u64::from));
        }
        return Ok(None);
    }

    paper_provider::ensure_for_workspace(workspace).map(Some)
}

fn ensure_server_properties(workspace: &Path) -> Result<(), String> {
    let path = workspace.join("server").join("server.properties");
    if persistence::metadata_entry_exists(&path, "server.properties")? {
        // Never accept a user-controlled symlink, reparse point or directory
        // as a valid server.properties. In particular, do not follow it.
        persistence::safe_path::ensure_regular_file(&path, "server.properties")?;
        return Ok(());
    }
    let text = "# Managed baseline created by LazyBuilder\nserver-port=25565\nonline-mode=true\nenable-command-block=true\nspawn-protection=0\nview-distance=10\nsimulation-distance=10\n";
    let mut file = OpenOptions::new().write(true).create_new(true).open(&path)
        .map_err(|e| format!("Could not create new server.properties without replacing data: {e}"))?;
    file.write_all(text.as_bytes()).map_err(|e| e.to_string())?;
    file.sync_all().map_err(|e| e.to_string())
}

fn update_paper_manifest_if_known(workspace: &Path, paper_build: Option<u64>) -> Result<(), String> {
    let Some(build) = paper_build else {
        // Unknown adopted Paper builds must not modify user metadata.
        return Ok(());
    };
    let current = workspace_registry::manifest(workspace)?;
    if current.paper_build.map(u64::from) == Some(build) {
        return Ok(());
    }
    // One canonical manifest writer; no separate atomic/rollback implementation.
    workspace_registry::update_paper_build(workspace, build)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn valid_manifest(paper_build: Option<u32>) -> String {
        serde_json::json!({
            "schemaVersion": 1,
            "workspaceId": "test-workspace",
            "name": "Test Server",
            "minecraftVersion": "1.21.4",
            "serverPlatform": "paper",
            "paperBuild": paper_build,
            "worldManagerVersion": null,
            "utilitiesManagerVersion": null,
            "createdUnixSeconds": 1,
            "lastOpenedUnixSeconds": 1
        }).to_string()
    }

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
            valid_manifest(None),
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
        fs::write(&manifest, valid_manifest(Some(123))).unwrap();

        update_paper_manifest_if_known(&workspace, Some(123)).unwrap();
        let first = fs::read_to_string(&manifest).unwrap();
        update_paper_manifest_if_known(&workspace, Some(123)).unwrap();
        let second = fs::read_to_string(&manifest).unwrap();
        assert_eq!(first, second);

        let _ = fs::remove_dir_all(workspace);
    }

    #[test]
    fn paper_build_metadata_keeps_existing_workspace_identity() {
        let workspace = test_root("paper-build-preserves-identity");
        let config = workspace.join("tools").join("lazybuilder").join("config");
        fs::create_dir_all(&config).unwrap();
        let manifest = config.join("workspace.json");
        fs::write(&manifest, valid_manifest(None)).unwrap();
        update_paper_manifest_if_known(&workspace, Some(123)).unwrap();
        let persisted: serde_json::Value =
            serde_json::from_str(&fs::read_to_string(&manifest).unwrap()).unwrap();
        assert_eq!(persisted["paperBuild"], 123);
        assert_eq!(persisted["workspaceId"], "test-workspace");
        assert_eq!(persisted["name"], "Test Server");
        let _ = fs::remove_dir_all(workspace);
    }

    #[test]
    fn existing_server_properties_are_never_overwritten() {
        let workspace = test_root("properties-preserved");
        let server = workspace.join("server");
        fs::create_dir_all(&server).unwrap();
        let path = server.join("server.properties");
        fs::write(&path, "custom=user-owned\n").unwrap();
        ensure_server_properties(&workspace).unwrap();
        assert_eq!(fs::read_to_string(&path).unwrap(), "custom=user-owned\n");
        let _ = fs::remove_dir_all(workspace);
    }

    #[cfg(unix)]
    #[test]
    fn symbolic_link_server_properties_is_rejected_without_touching_target() {
        use std::os::unix::fs::symlink;
        let workspace = test_root("properties-symlink-reject");
        let server = workspace.join("server");
        fs::create_dir_all(&server).unwrap();
        let outside = workspace.join("important.txt");
        fs::write(&outside, "PRIVATE DATA").unwrap();
        symlink(&outside, server.join("server.properties")).unwrap();
        assert!(ensure_server_properties(&workspace).is_err());
        assert_eq!(fs::read_to_string(&outside).unwrap(), "PRIVATE DATA");
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
