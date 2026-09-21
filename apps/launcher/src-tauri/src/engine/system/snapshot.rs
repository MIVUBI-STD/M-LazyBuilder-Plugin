use crate::engine::operations::{OperationRegistry, OperationSnapshot};
use crate::engine::server_health::{self, ServerReadinessSnapshot};
use crate::engine::server_runtime_registry::{ServerRuntimeRegistry, ServerRuntimeSummary};
use crate::engine::workspace_registry::{self, WorkspaceEntry};
use crate::engine::world_manager;
use serde::Serialize;

use super::capabilities::{local_capabilities, resource_targets_workspace, world_capability, CapabilityStatus};
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
        if workspace.is_none() {
            capabilities.push(world_capability(false, "Open a workspace first."));
        } else if !context.world_bridge_may_be_available() {
            capabilities.push(world_capability(false, "Start the Paper server first."));
        } else {
            let status = tauri::async_runtime::spawn_blocking(world_manager::bridge_status)
                .await
                .map_err(|error| format!("World capability query failed: {error}"))?;
            match status {
                Ok(status) => {
                    let required = ["world.list", "world.tasks"];
                    let available = required
                        .iter()
                        .all(|required| status.capabilities.iter().any(|value| value == required));
                    capabilities.push(world_capability(
                        available,
                        if available {
                            "World Manager advertised the required world-control capabilities."
                        } else {
                            "World Manager is connected but does not advertise the required world-control capabilities."
                        },
                    ));
                }
                Err(error) => {
                    warnings.push(format!("World capability handshake is unavailable: {error}"));
                    capabilities.push(world_capability(false, "World Manager capability handshake is unavailable."));
                }
            }
        }

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
