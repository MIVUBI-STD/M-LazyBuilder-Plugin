use crate::engine::server_manager::ServerManagerState;
use crate::engine::workspace_registry::{self, WorkspaceEntry};
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};

/// In-memory owner for Launcher-attached Paper controllers.
///
/// The durable workspace library and process markers remain their existing
/// authorities. This registry owns only live Launcher controller handles so
/// switching the selected workspace cannot discard another server's stdin or
/// runtime state.
pub struct ServerRuntimeRegistry {
    runtimes: Mutex<HashMap<String, Arc<ServerManagerState>>>,
}

impl Default for ServerRuntimeRegistry {
    fn default() -> Self {
        Self { runtimes: Mutex::new(HashMap::new()) }
    }
}

impl ServerRuntimeRegistry {
    pub fn active_runtime(&self) -> Result<(WorkspaceEntry, Arc<ServerManagerState>), String> {
        let active = workspace_registry::current()?
            .ok_or_else(|| "No LazyBuilder server workspace is active.".to_string())?;
        let runtime = self.runtime_for(&active)?;
        Ok((active, runtime))
    }

    pub fn runtime_for_id(&self, workspace_id: &str) -> Result<Arc<ServerManagerState>, String> {
        let workspace = workspace_registry::get(workspace_id)?;
        self.runtime_for(&workspace)
    }

    pub fn runtime_if_present(&self, workspace_id: &str) -> Result<Option<Arc<ServerManagerState>>, String> {
        let runtimes = self.runtimes.lock().map_err(|_| "server runtime registry lock poisoned".to_string())?;
        Ok(runtimes.get(workspace_id).cloned())
    }

    pub fn remove(&self, workspace_id: &str) -> Result<(), String> {
        let mut runtimes = self.runtimes.lock().map_err(|_| "server runtime registry lock poisoned".to_string())?;
        runtimes.remove(workspace_id);
        Ok(())
    }

    pub fn attached_runtime_count(&self) -> Result<usize, String> {
        let runtimes = self.runtimes.lock().map_err(|_| "server runtime registry lock poisoned".to_string())?;
        Ok(runtimes.len())
    }

    fn runtime_for(&self, workspace: &WorkspaceEntry) -> Result<Arc<ServerManagerState>, String> {
        let mut runtimes = self.runtimes.lock().map_err(|_| "server runtime registry lock poisoned".to_string())?;
        if let Some(runtime) = runtimes.get(&workspace.id) {
            return Ok(Arc::clone(runtime));
        }

        let root = PathBuf::from(&workspace.path);
        let runtime = Arc::new(ServerManagerState::for_workspace(root));
        runtimes.insert(workspace.id.clone(), Arc::clone(&runtime));
        Ok(runtime)
    }
}

#[cfg(test)]
mod tests {
    use super::ServerRuntimeRegistry;

    #[test]
    fn empty_registry_starts_without_attached_runtimes() {
        let registry = ServerRuntimeRegistry::default();
        assert_eq!(registry.attached_runtime_count().unwrap(), 0);
    }
}
