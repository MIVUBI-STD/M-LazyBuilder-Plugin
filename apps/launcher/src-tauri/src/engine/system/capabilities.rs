use crate::engine::operations::OperationSnapshot;
use crate::engine::server_health::ServerReadinessSnapshot;
use crate::engine::server_runtime_registry::ServerRuntimeSummary;
use crate::engine::workspace_registry::WorkspaceEntry;
use serde::Serialize;

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CapabilityStatus {
    pub key: String,
    pub available: bool,
    pub reason: String,
}

pub fn local_capabilities(
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

    result.push(capability("diagnostics.export", true, "Launcher diagnostics are always available."));
    result
}

pub fn world_capability(available: bool, reason: &str) -> CapabilityStatus {
    capability("world.manage", available, reason)
}

fn capability(key: &str, available: bool, reason: &str) -> CapabilityStatus {
    CapabilityStatus { key: key.into(), available, reason: reason.into() }
}

pub fn resource_targets_workspace(resource: &str, workspace_id: &str) -> bool {
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
    fn local_capability_keys_are_unique() {
        let capabilities = local_capabilities(None, None, &[], &[]);
        let mut keys = capabilities.iter().map(|entry| entry.key.as_str()).collect::<Vec<_>>();
        let original_len = keys.len();
        keys.sort_unstable();
        keys.dedup();
        assert_eq!(keys.len(), original_len);
    }
}
