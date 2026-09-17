use crate::commands::error::{CommandError, CommandResult, RecoveryAction};
use crate::engine::operations::{OperationError, OperationRegistry};
use crate::engine::server_runtime_registry::ServerRuntimeRegistry;
use crate::engine::startup::StartupReport;
use crate::engine::{diagnostics, support_bundle, workspace_registry};
use serde::Serialize;
use std::path::Path;
use tauri::{AppHandle, Manager};

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DiagnosticSummary {
    pub launcher_version: String,
    pub build_commit: String,
    pub build_channel: String,
    pub build_target: String,
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
        .map_err(|error| CommandError::recoverable_action("OPERATION_BUSY", error, RecoveryAction::OpenActivity))?;
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
        let mut sensitive_paths: Vec<String> = workspace_registry::list()
            .unwrap_or_default()
            .into_iter()
            .map(|entry| entry.path)
            .filter(|path| !path.trim().is_empty())
            .collect();
        if let Some(active_path) = summary.workspace_path.clone() {
            if !sensitive_paths.iter().any(|path| path.eq_ignore_ascii_case(&active_path)) {
                sensitive_paths.push(active_path);
            }
        }

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
                let error = CommandError::recoverable_action(
                    "SUPPORT_BUNDLE_FAILED",
                    message,
                    RecoveryAction::ChooseLocation,
                );
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
    let workspace = workspace_registry::current().ok().flatten();
    let manifest = workspace
        .as_ref()
        .and_then(|entry| workspace_registry::manifest(Path::new(&entry.path)).ok());

    let registry = app.state::<ServerRuntimeRegistry>();
    let runtime = registry.active_runtime().ok().map(|(_, state)| state);
    let snapshot = runtime.as_ref().and_then(|state| state.snapshot().ok());
    let preflight = runtime.as_ref().map(|state| state.preflight());
    let log_path = diagnostics::launcher_log_path()
        .map(|path| path.display().to_string())
        .unwrap_or_default();
    let build_commit = option_env!("GITHUB_SHA")
        .or(option_env!("LAZYBUILDER_BUILD_COMMIT"))
        .unwrap_or("local")
        .to_string();
    let build_target = format!("{}-{}", std::env::consts::OS, std::env::consts::ARCH);

    DiagnosticSummary {
        launcher_version: env!("CARGO_PKG_VERSION").to_string(),
        build_commit,
        build_channel: "stable".into(),
        build_target,
        launcher_log_path: log_path,
        workspace_name: workspace.as_ref().map(|value| value.name.clone()),
        workspace_path: workspace.as_ref().map(|value| value.path.clone()),
        minecraft_version: manifest.as_ref().map(|value| value.minecraft_version.clone()),
        server_platform: manifest.as_ref().map(|value| value.server_platform.clone()),
        paper_build: manifest.as_ref().and_then(|value| value.paper_build),
        server_state: snapshot.as_ref().map(|value| value.state.clone()).unwrap_or_else(|| "Unavailable".into()),
        pid: snapshot.as_ref().and_then(|value| value.pid),
        java_version: preflight.as_ref().map(|value| value.java_version.clone()).unwrap_or_default(),
        max_memory_mb: snapshot.as_ref().map(|value| value.max_memory_bytes / 1024 / 1024).unwrap_or_default(),
    }
}
