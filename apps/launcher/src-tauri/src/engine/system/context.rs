use crate::engine::server_runtime_registry::ServerRuntimeSummary;
use crate::engine::workspace_registry::WorkspaceEntry;

pub struct SystemContext<'a> {
    pub workspace: Option<&'a WorkspaceEntry>,
    pub runtimes: &'a [ServerRuntimeSummary],
}

impl<'a> SystemContext<'a> {
    pub fn active_runtime_state(&self) -> Option<&str> {
        let workspace_id = self.workspace.map(|entry| entry.id.as_str())?;
        self.runtimes
            .iter()
            .find(|runtime| runtime.workspace_id == workspace_id)
            .map(|runtime| runtime.state.as_str())
    }

    pub fn world_bridge_may_be_available(&self) -> bool {
        matches!(self.active_runtime_state(), Some("Online" | "Detached"))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn no_workspace_has_no_active_runtime() {
        let context = SystemContext { workspace: None, runtimes: &[] };
        assert_eq!(context.active_runtime_state(), None);
        assert!(!context.world_bridge_may_be_available());
    }
}
