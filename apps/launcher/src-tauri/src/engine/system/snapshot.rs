use crate::engine::operations::{OperationRegistry, OperationSnapshot};
use crate::engine::server_health::{self, ServerReadinessSnapshot};
use crate::engine::server_runtime_registry::{ServerRuntimeRegistry, ServerRuntimeSummary};
use crate::engine::workspace_registry::{self, WorkspaceEntry};
use serde::Serialize;

use super::capabilities::{append_world_capability, local_capabilities, resource_targets_workspace, CapabilityStatus};
use super::context::SystemContext;

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum SystemReadiness {
    NoWorkspace,
    NeedsAttention,
    Ready,
    Busy,
    Degraded,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SystemSnapshot {
    pub readiness: SystemReadiness,
    pub workspace: Option<WorkspaceEntry>,
    pub recent_workspaces: Vec<WorkspaceEntry>,
    pub server_health: Option<ServerReadinessSnapshot>,
    pub runtimes: Vec<ServerRuntimeSummary>,
    pub active_operations: Vec<OperationSnapshot>,
    pub capabilities: Vec<CapabilityStatus>,
    pub warnings: Vec<String>,
}

#[derive(Default)]
pub struct SystemSnapshotService;

impl SystemSnapshotService {
    pub async fn snapshot(
        &self,
        runtimes: &ServerRuntimeRegistry,
        operations: &OperationRegistry,
    ) -> Result<SystemSnapshot, String> {
        let workspace = workspace_registry::current()?;
        let recent_workspaces = workspace_registry::list()?;
        let runtimes = runtimes.summaries()?;
        let active_operations = operations
            .list()?
            .into_iter()
            .filter(|entry| !entry.state.is_terminal())
            .collect::<Vec<_>>();

        let mut warnings = Vec::new();
        let server_health = match workspace.as_ref() {
            Some(entry) => match server_health::inspect(&entry.id) {
                Ok(snapshot) => Some(snapshot),
                Err(error) => {
                    warnings.push(format!("Server readiness could not be inspected: {error}"));
                    None
                }
            },
            None => None,
        };

        let mut capabilities = local_capabilities(
            workspace.as_ref(),
            server_health.as_ref(),
            &runtimes,
            &active_operations,
        );

        let context = SystemContext { workspace: workspace.as_ref(), runtimes: &runtimes };
        append_world_capability(&mut capabilities, &mut warnings, &context).await?;

        let readiness = project_readiness(
            workspace.as_ref(),
            server_health.as_ref(),
            &runtimes,
            &active_operations,
            &warnings,
        );

        Ok(SystemSnapshot {
            readiness,
            workspace,
            recent_workspaces,
            server_health,
            runtimes,
            active_operations,
            capabilities,
            warnings,
        })
    }
}

fn project_readiness(
    workspace: Option<&WorkspaceEntry>,
    health: Option<&ServerReadinessSnapshot>,
    runtimes: &[ServerRuntimeSummary],
    operations: &[OperationSnapshot],
    warnings: &[String],
) -> SystemReadiness {
    let Some(workspace) = workspace else {
        return SystemReadiness::NoWorkspace;
    };
    if !warnings.is_empty() {
        return SystemReadiness::Degraded;
    }
    if operations.iter().any(|entry| resource_targets_workspace(&entry.resource, &workspace.id)) {
        return SystemReadiness::Busy;
    }
    if runtimes.iter().any(|runtime| {
        runtime.workspace_id == workspace.id
            && matches!(runtime.state.as_str(), "Starting" | "Stopping" | "Detached")
    }) {
        return SystemReadiness::Busy;
    }
    match health {
        Some(value) if value.ready => SystemReadiness::Ready,
        Some(_) => SystemReadiness::NeedsAttention,
        None => SystemReadiness::Degraded,
    }
}
