use crate::engine::{core_modules, java_runtime, paper_provider, workspace_registry};
use serde_json::Value;
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Clone, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ProvisionResult {
    pub java_path: String,
    pub paper_build: u64,
    pub core_version: String,
    pub status: workspace_registry::ProvisioningStatus,
}

pub fn provision_active(resource_dir: Option<&Path>) -> Result<ProvisionResult, String> {
    let workspace = workspace_registry::active_workspace()?;
    crate::engine::paths::ensure_runtime_layout()?;

    let java = java_runtime::ensure_managed_java()?;
    ensure_server_manager_config(&workspace, &java)?;
    let paper_build = resolve_or_provision_paper(&workspace)?;
    core_modules::sync(&workspace, resource_dir)?;
    ensure_server_properties(&workspace)?;
    update_workspace_manifest(&workspace, paper_build, core_modules::CORE_VERSION)?;

    Ok(ProvisionResult {
        java_path: java.display().to_string(),
        paper_build,
        core_version: core_modules::CORE_VERSION.into(),
        status: workspace_registry::provisioning_status()?,
    })
}

fn resolve_or_provision_paper(workspace: &Path) -> Result<u64, String> {
    let paper = workspace.join("server").join("paper.jar");
    let manifest = workspace.join("tools").join("lazybuilder").join("config").join("workspace.json");
    if paper.is_file() && manifest.is_file() {
        let text = fs::read_to_string(&manifest).map_err(|e| e.to_string())?;
        let value: Value = serde_json::from_str(&text).map_err(|e| e.to_string())?;
        if let Some(build) = value.get("paperBuild").and_then(Value::as_u64) {
            return Ok(build);
        }
    }
    paper_provider::ensure_for_workspace(workspace)
}

fn ensure_server_properties(workspace: &Path) -> Result<(), String> {
    let path = workspace.join("server").join("server.properties");
    if path.is_file() { return Ok(()); }
    let text = "# Managed baseline created by LazyBuilder\nserver-port=25565\nonline-mode=true\nenable-command-block=true\nspawn-protection=0\nview-distance=10\nsimulation-distance=10\n";
    fs::write(path, text).map_err(|e| e.to_string())
}

fn ensure_server_manager_config(workspace: &Path, java: &Path) -> Result<(), String> {
    let path = workspace.join("tools").join("lazybuilder").join("config").join("server-manager.json");
    if path.is_file() {
        let text = fs::read_to_string(&path).map_err(|e| e.to_string())?;
        let mut value: Value = serde_json::from_str(&text).map_err(|e| e.to_string())?;
        let current = value.get("javaPath").and_then(Value::as_str).unwrap_or("");
        if current.trim().is_empty() || !Path::new(current).is_file() {
            value["javaPath"] = Value::String(java.display().to_string());
            write_json_atomic(&path, &value)?;
        }
        return Ok(());
    }

    let value = serde_json::json!({
        "javaPath": java.display().to_string(),
        "serverDirectory": "server",
        "paperJar": "paper.jar",
        "minMemoryMb": 1024,
        "maxMemoryMb": 4096,
        "gracefulStopTimeoutSeconds": 30,
        "startupTimeoutSeconds": 90
    });
    write_json_atomic(&path, &value)
}

fn update_workspace_manifest(workspace: &Path, paper_build: u64, core_version: &str) -> Result<(), String> {
    let path = workspace.join("tools").join("lazybuilder").join("config").join("workspace.json");
    let text = fs::read_to_string(&path).map_err(|e| e.to_string())?;
    let mut value: Value = serde_json::from_str(&text).map_err(|e| e.to_string())?;
    value["paperBuild"] = Value::from(paper_build);
    value["worldManagerVersion"] = Value::String(core_version.into());
    value["utilitiesManagerVersion"] = Value::String(core_version.into());
    write_json_atomic(&path, &value)
}

fn write_json_atomic(path: &Path, value: &Value) -> Result<(), String> {
    if let Some(parent) = path.parent() { fs::create_dir_all(parent).map_err(|e| e.to_string())?; }
    let temporary = PathBuf::from(format!("{}.tmp", path.display()));
    let backup = PathBuf::from(format!("{}.previous", path.display()));
    fs::write(&temporary, serde_json::to_string_pretty(value).map_err(|e| e.to_string())?).map_err(|e| e.to_string())?;
    if path.exists() {
        let _ = fs::remove_file(&backup);
        fs::rename(path, &backup).map_err(|e| e.to_string())?;
        match fs::rename(&temporary, path) {
            Ok(()) => { let _ = fs::remove_file(backup); Ok(()) }
            Err(error) => { let _ = fs::rename(&backup, path); Err(error.to_string()) }
        }
    } else {
        fs::rename(temporary, path).map_err(|e| e.to_string())
    }
}
