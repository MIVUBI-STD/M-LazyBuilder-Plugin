use crate::engine::{diagnostics, server_manager::ServerManagerState, workspace_registry};
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;
use tauri::{AppHandle, Manager};

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DiagnosticSummary {
    pub launcher_version: String,
    pub launcher_log_path: String,
    pub workspace_name: Option<String>,
    pub workspace_path: Option<String>,
    pub minecraft_version: Option<String>,
    pub server_platform: Option<String>,
    pub paper_build: Option<u32>,
    pub server_state: String,
    pub pid: Option<u32>,
    pub java_version: String,
    pub max_memory_mb: u64,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct WorkspaceManifestView {
    minecraft_version: String,
    server_platform: String,
    paper_build: Option<u32>,
}

#[tauri::command]
pub async fn diagnostics_summary(app: AppHandle) -> Result<DiagnosticSummary, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let server = app.state::<ServerManagerState>();
        let workspace = workspace_registry::current().ok().flatten();
        let manifest = workspace.as_ref().and_then(read_manifest);
        let snapshot = server.snapshot().ok();
        let preflight = server.preflight();
        let log_path = diagnostics::launcher_log_path()
            .map(|path| path.display().to_string())
            .unwrap_or_default();

        Ok(DiagnosticSummary {
            launcher_version: env!("CARGO_PKG_VERSION").to_string(),
            launcher_log_path: log_path,
            workspace_name: workspace.as_ref().map(|value| value.name.clone()),
            workspace_path: workspace.as_ref().map(|value| value.path.clone()),
            minecraft_version: manifest.as_ref().map(|value| value.minecraft_version.clone()),
            server_platform: manifest.as_ref().map(|value| value.server_platform.clone()),
            paper_build: manifest.as_ref().and_then(|value| value.paper_build),
            server_state: snapshot.as_ref().map(|value| value.state.clone()).unwrap_or_else(|| "Unavailable".into()),
            pid: snapshot.as_ref().and_then(|value| value.pid),
            java_version: preflight.java_version,
            max_memory_mb: snapshot.as_ref().map(|value| value.max_memory_bytes / 1024 / 1024).unwrap_or_default(),
        })
    })
    .await
    .map_err(|error| format!("Diagnostics task failed: {error}"))?
}

fn read_manifest(workspace: &workspace_registry::WorkspaceEntry) -> Option<WorkspaceManifestView> {
    let path = PathBuf::from(&workspace.path)
        .join("tools")
        .join("lazybuilder")
        .join("config")
        .join("workspace.json");
    let text = fs::read_to_string(path).ok()?;
    serde_json::from_str(&text).ok()
}
