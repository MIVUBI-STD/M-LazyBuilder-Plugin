use crate::commands::error::{CommandError, CommandResult};
use crate::engine::operations::{OperationError, OperationRegistry};
use crate::engine::server_manager::ServerManagerState;
use crate::engine::startup::StartupReport;
use crate::engine::{diagnostics, support_bundle, workspace_registry};
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
    tauri::async_runtime::spawn_blocking(move || Ok(collect_summary(&app)))
        .await
        .map_err(|error| format!("Diagnostics task failed: {error}"))?
}

#[tauri::command]
pub async fn diagnostics_export_support_bundle(app: AppHandle) -> CommandResult<Option<String>> {
    let destination = rfd::FileDialog::new()
        .set_title("Export LazyBuilder support bundle")
        .add_filter("ZIP archive", &["zip"])
        .set_file_name("LazyBuilder-Support.zip")
        .save_file();
    let Some(destination) = destination else { return Ok(None); };

    let operation = app
        .state::<OperationRegistry>()
        .begin_exclusive("export-support-bundle", "launcher:support-bundle", false)
        .map_err(|error| CommandError::new("OPERATION_BUSY", error))?;
    let operation_id = operation.id.clone();
    let join_operation_id = operation.id.clone();
    let task_app = app.clone();

    let task = tauri::async_runtime::spawn_blocking(move || {
        let operations = task_app.state::<OperationRegistry>();
        let _ = operations.set_phase(
            &operation_id,
            "collecting",
            "Collecting diagnostics",
            "Collecting bounded Launcher metadata, startup state, operation history, and Launcher logs.",
            None,
        );

        let summary = collect_summary(&task_app);
        let operation_snapshots = operations.list().map_err(CommandError::from)?;
        let startup = task_app.state::<StartupReport>().inner().clone();
        let sensitive_paths: Vec<String> = summary.workspace_path.clone().into_iter().collect();

        let _ = operations.set_phase(
            &operation_id,
            "writing",
            "Writing support bundle",
            "Writing the sanitized diagnostic archive to the selected location.",
            None,
        );

        match support_bundle::write_bundle(
            &destination,
            &summary,
            &operation_snapshots,
            &startup,
            &sensitive_paths,
        ) {
            Ok(path) => {
                let _ = operations.succeed(&operation_id, "Support bundle exported");
                Ok(path.display().to_string())
            }
            Err(message) => {
                let error = CommandError::recoverable("SUPPORT_BUNDLE_FAILED", message, "Choose another location");
                let _ = operations.fail(
                    &operation_id,
                    OperationError {
                        code: error.code.to_string(),
                        message: error.message.clone(),
                        details: error.details.clone(),
                        recoverable: true,
                    },
                );
                Err(error)
            }
        }
    });

    match task.await {
        Ok(result) => result.map(Some),
        Err(error) => {
            let _ = app.state::<OperationRegistry>().fail(
                &join_operation_id,
                OperationError {
                    code: "TASK_FAILED".into(),
                    message: "Support bundle export ended unexpectedly".into(),
                    details: error.to_string(),
                    recoverable: true,
                },
            );
            Err(CommandError::new("TASK_FAILED", format!("Support bundle task failed: {error}")))
        }
    }
}

fn collect_summary(app: &AppHandle) -> DiagnosticSummary {
    let server = app.state::<ServerManagerState>();
    let workspace = workspace_registry::current().ok().flatten();
    let manifest = workspace.as_ref().and_then(read_manifest);
    let snapshot = server.snapshot().ok();
    let preflight = server.preflight();
    let log_path = diagnostics::launcher_log_path()
        .map(|path| path.display().to_string())
        .unwrap_or_default();

    DiagnosticSummary {
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
    }
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
