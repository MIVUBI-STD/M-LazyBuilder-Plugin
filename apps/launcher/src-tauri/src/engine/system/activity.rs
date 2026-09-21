use crate::engine::operations::{OperationRegistry, OperationSnapshot};
use crate::engine::server_runtime_registry::ServerRuntimeRegistry;
use crate::engine::workspace_registry;
use crate::engine::world_manager::{self, WorldTaskSnapshot};
use serde::Serialize;

use super::context::SystemContext;

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SystemActivitySnapshot {
    pub launcher_operations: Vec<OperationSnapshot>,
    pub world_tasks: Vec<WorldTaskSnapshot>,
    pub world_tasks_available: bool,
    pub warnings: Vec<String>,
}

#[derive(Default)]
pub struct SystemActivityService;

impl SystemActivityService {
    pub async fn snapshot(
        &self,
        runtimes: &ServerRuntimeRegistry,
        operations: &OperationRegistry,
    ) -> Result<SystemActivitySnapshot, String> {
        let launcher_operations = operations.list()?;
        let workspace = workspace_registry::current()?;
        let runtime_summaries = runtimes.summaries()?;
        let context = SystemContext { workspace: workspace.as_ref(), runtimes: &runtime_summaries };

        if !context.world_bridge_may_be_available() {
            return Ok(SystemActivitySnapshot {
                launcher_operations,
                world_tasks: Vec::new(),
                world_tasks_available: false,
                warnings: Vec::new(),
            });
        }

        let world_result = tauri::async_runtime::spawn_blocking(world_manager::list_world_tasks)
            .await
            .map_err(|error| format!("World activity query failed: {error}"))?;

        match world_result {
            Ok(world_tasks) => Ok(SystemActivitySnapshot {
                launcher_operations,
                world_tasks,
                world_tasks_available: true,
                warnings: Vec::new(),
            }),
            Err(error) => Ok(SystemActivitySnapshot {
                launcher_operations,
                world_tasks: Vec::new(),
                world_tasks_available: false,
                warnings: vec![format!("World activity is temporarily unavailable: {error}")],
            }),
        }
    }
}
