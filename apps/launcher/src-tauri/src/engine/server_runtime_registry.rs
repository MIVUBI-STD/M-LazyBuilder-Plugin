use crate::engine::server_manager::ServerManagerState;
use crate::engine::server_process_guard::{self, MAX_CONCURRENT_SERVERS};
use crate::engine::workspace_registry::{self, WorkspaceEntry};
use serde::Serialize;
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use sysinfo::{Pid, System};

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerRuntimeSummary {
    pub workspace_id: String,
    pub workspace_name: String,
    pub state: String,
    pub pid: Option<u32>,
    pub paper_port: Option<u16>,
    pub used_memory_bytes: u64,
    pub max_memory_bytes: u64,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerRuntimeFleet {
    pub runtimes: Vec<ServerRuntimeSummary>,
    pub active_count: usize,
    pub capacity: usize,
    pub managed_used_memory_bytes: u64,
    pub total_memory_bytes: u64,
    pub available_memory_bytes: u64,
}

struct RuntimeEntry {
    workspace_name: String,
    controller: Arc<ServerManagerState>,
    paper_port: Option<u16>,
}

/// In-memory owner for Launcher-attached Paper controllers.
///
/// The durable workspace library and process markers remain their existing
/// authorities. This registry owns only live Launcher controller handles so
/// switching the selected workspace cannot discard another server's stdin or
/// runtime state.
pub struct ServerRuntimeRegistry {
    runtimes: Mutex<HashMap<String, RuntimeEntry>>,
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
        Ok(runtimes.get(workspace_id).map(|entry| Arc::clone(&entry.controller)))
    }

    pub fn set_paper_port(&self, workspace_id: &str, port: u16) -> Result<(), String> {
        let mut runtimes = self.runtimes.lock().map_err(|_| "server runtime registry lock poisoned".to_string())?;
        let entry = runtimes.get_mut(workspace_id)
            .ok_or_else(|| "Server runtime disappeared while publishing its connection port.".to_string())?;
        entry.paper_port = Some(port);
        Ok(())
    }

    pub fn active_paper_port(&self) -> Result<Option<u16>, String> {
        let Some(active) = workspace_registry::current()? else { return Ok(None); };
        let runtimes = self.runtimes.lock().map_err(|_| "server runtime registry lock poisoned".to_string())?;
        Ok(runtimes.get(&active.id).and_then(|entry| entry.paper_port))
    }

    pub fn fleet(&self) -> Result<ServerRuntimeFleet, String> {
        let entries = {
            let runtimes = self.runtimes.lock().map_err(|_| "server runtime registry lock poisoned".to_string())?;
            runtimes.iter().map(|(id, entry)| {
                (id.clone(), entry.workspace_name.clone(), Arc::clone(&entry.controller), entry.paper_port)
            }).collect::<Vec<_>>()
        };

        let mut summaries = Vec::with_capacity(entries.len());
        for (workspace_id, workspace_name, controller, paper_port) in entries {
            let snapshot = controller.snapshot()?;
            summaries.push(ServerRuntimeSummary {
                workspace_id,
                workspace_name,
                state: snapshot.state,
                pid: snapshot.pid,
                paper_port,
                used_memory_bytes: snapshot.used_memory_bytes,
                max_memory_bytes: snapshot.max_memory_bytes,
            });
        }

        let detached = server_process_guard::running_registered_papers()?;
        let mut system = System::new_all();
        system.refresh_memory();
        for process in detached {
            if summaries.iter().any(|entry| entry.workspace_id == process.workspace_id) {
                continue;
            }
            let pid = Pid::from_u32(process.pid);
            system.refresh_process(pid);
            let used_memory_bytes = system.process(pid).map(|value| value.memory()).unwrap_or(0);
            summaries.push(ServerRuntimeSummary {
                workspace_id: process.workspace_id,
                workspace_name: process.workspace_name,
                state: "Detached".into(),
                pid: Some(process.pid),
                paper_port: None,
                used_memory_bytes,
                max_memory_bytes: 0,
            });
        }

        summaries.sort_by(|left, right| {
            left.workspace_name
                .to_ascii_lowercase()
                .cmp(&right.workspace_name.to_ascii_lowercase())
                .then_with(|| left.workspace_id.cmp(&right.workspace_id))
        });

        let active_count = summaries.iter().filter(|entry| is_active_state(&entry.state)).count();
        let managed_used_memory_bytes = summaries.iter().map(|entry| entry.used_memory_bytes).sum();
        Ok(ServerRuntimeFleet {
            runtimes: summaries,
            active_count,
            capacity: MAX_CONCURRENT_SERVERS,
            managed_used_memory_bytes,
            total_memory_bytes: system.total_memory(),
            available_memory_bytes: system.available_memory(),
        })
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
        if let Some(entry) = runtimes.get(&workspace.id) {
            return Ok(Arc::clone(&entry.controller));
        }

        let root = PathBuf::from(&workspace.path);
        let runtime = Arc::new(ServerManagerState::for_workspace(root));
        runtimes.insert(workspace.id.clone(), RuntimeEntry {
            workspace_name: workspace.name.clone(),
            controller: Arc::clone(&runtime),
            paper_port: None,
        });
        Ok(runtime)
    }
}

fn is_active_state(state: &str) -> bool {
    matches!(state, "Starting" | "Online" | "Stopping" | "Detached")
}

#[cfg(test)]
mod tests {
    use super::{is_active_state, ServerRuntimeRegistry};

    #[test]
    fn empty_registry_starts_without_attached_runtimes() {
        let registry = ServerRuntimeRegistry::default();
        assert_eq!(registry.attached_runtime_count().unwrap(), 0);
    }

    #[test]
    fn fleet_capacity_counts_only_live_runtime_states() {
        for state in ["Starting", "Online", "Stopping", "Detached"] {
            assert!(is_active_state(state));
        }
        for state in ["Offline", "Crashed"] {
            assert!(!is_active_state(state));
        }
    }
}
