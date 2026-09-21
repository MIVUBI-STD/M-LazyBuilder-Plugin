use crate::engine::operations::{OperationRegistry, OperationSnapshot};
use crate::engine::server_health::{self, ServerReadinessSnapshot};
use crate::engine::server_runtime_registry::{ServerRuntimeRegistry, ServerRuntimeSummary};
use crate::engine::workspace_registry::{self, WorkspaceEntry};
use serde::Serialize;

#[derive(Default)]
pub struct SystemKernel;

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum SystemReadiness {
    NoWorkspace,
    NeedsAttention,
    Ready,
    Busy,
    Degraded,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CapabilityStatus {
    pub key: String,
    pub available: bool,
    pub reason: String,
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

impl SystemKernel {
    pub fn snapshot(
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

        let capabilities = project_capabilities(
            workspace.as_ref(),
            server_health.as_ref(),
            &runtimes,
            &active_operations,
        );
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

fn project_capabilities(
    workspace: Option<&WorkspaceEntry>,
    health: Option<&ServerReadinessSnapshot>,
    runtimes: &[ServerRuntimeSummary],
    operations: &[OperationSnapshot],
) -> Vec<CapabilityStatus> {
    let mut result = Vec::with_capacity(6);
    let active_id = workspace.map(|entry| entry.id.as_str());
    let workspace_busy = active_id.is_some_and(|id| {
        operations.iter().any(|entry| resource_targets_workspace(&entry.resource, id))
    });
    let runtime_state = active_id.and_then(|id| {
        runtimes.iter().find(|runtime| runtime.workspace_id == id).map(|runtime| runtime.state.as_str())
    });
    let server_running = matches!(runtime_state, Some("Starting" | "Online" | "Stopping" | "Detached"))
        || health.is_some_and(|value| value.running);

    result.push(capability(
        "workspace.manage",
        !workspace_busy,
        if workspace_busy { "A workspace operation is active." } else { "Workspace library is available." },
    ));

    let has_workspace = workspace.is_some();
    result.push(capability(
        "server.inspect",
        has_workspace,
        if has_workspace { "An active workspace is selected." } else { "Open a workspace first." },
    ));

    let server_ready = health.is_some_and(|value| value.ready);
    result.push(capability(
        "server.start",
        has_workspace && server_ready && !server_running && !workspace_busy,
        if !has_workspace {
            "Open a workspace first."
        } else if workspace_busy {
            "Wait for the active workspace operation to finish."
        } else if server_running {
            "The server is already running or transitioning."
        } else if !server_ready {
            "Resolve server readiness checks first."
        } else {
            "Server start requirements are satisfied."
        },
    ));

    result.push(capability(
        "server.stop",
        has_workspace && server_running,
        if server_running { "A server runtime is active." } else { "No active server runtime." },
    ));

    result.push(capability(
        "client.sync",
        has_workspace && !workspace_busy,
        if !has_workspace {
            "Open a workspace first."
        } else if workspace_busy {
            "Wait for the active workspace operation to finish."
        } else {
            "Client Setup may inspect or synchronize the selected profile."
        },
    ));

    result.push(capability(
        "diagnostics.export",
        true,
        "Launcher diagnostics are always available.",
    ));

    result
}

fn capability(key: &str, available: bool, reason: &str) -> CapabilityStatus {
    CapabilityStatus { key: key.into(), available, reason: reason.into() }
}

fn resource_targets_workspace(resource: &str, workspace_id: &str) -> bool {
    resource == "workspace-library" || resource == format!("workspace:{workspace_id}")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn workspace_resource_matching_is_explicit() {
        assert!(resource_targets_workspace("workspace-library", "abc"));
        assert!(resource_targets_workspace("workspace:abc", "abc"));
        assert!(!resource_targets_workspace("workspace:def", "abc"));
        assert!(!resource_targets_workspace("plugin:abc", "abc"));
    }

    #[test]
    fn capability_keys_are_stable_and_unique() {
        let capabilities = project_capabilities(None, None, &[], &[]);
        let mut keys = capabilities.iter().map(|entry| entry.key.as_str()).collect::<Vec<_>>();
        let original_len = keys.len();
        keys.sort_unstable();
        keys.dedup();
        assert_eq!(keys.len(), original_len);
        assert!(keys.contains(&"diagnostics.export"));
        assert!(keys.contains(&"server.start"));
    }
}
