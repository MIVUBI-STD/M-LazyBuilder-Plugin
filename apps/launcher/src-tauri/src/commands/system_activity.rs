use crate::commands::error::{CommandError, CommandResult};
use crate::engine::operations::{OperationRegistry, OperationSnapshot};
use crate::engine::server_runtime_registry::ServerRuntimeRegistry;
use crate::engine::workspace_registry;
use crate::engine::world_manager::{self, WorldTaskSnapshot};
use serde::Serialize;
use tauri::State;

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SystemActivitySnapshot {
    pub launcher_operations: Vec<OperationSnapshot>,
    pub world_tasks: Vec<WorldTaskSnapshot>,
    pub world_tasks_available: bool,
    pub warnings: Vec<String>,
}

#[tauri::command]
pub async fn system_activity_snapshot(
    runtimes: State<'_, ServerRuntimeRegistry>,
    operations: State<'_, OperationRegistry>,
) -> CommandResult<SystemActivitySnapshot> {
    let launcher_operations = operations.list().map_err(CommandError::from)?;
    let Some(active) = workspace_registry::current().map_err(CommandError::from)? else {
        return Ok(SystemActivitySnapshot {
            launcher_operations,
            world_tasks: Vec::new(),
            world_tasks_available: false,
            warnings: Vec::new(),
        });
    };

    let runtime_online = runtimes
        .summaries()
        .map_err(CommandError::from)?
        .iter()
        .any(|runtime| runtime.workspace_id == active.id && matches!(runtime.state.as_str(), "Online" | "Detached"));

    if !runtime_online {
        return Ok(SystemActivitySnapshot {
            launcher_operations,
            world_tasks: Vec::new(),
            world_tasks_available: false,
            warnings: Vec::new(),
        });
    }

    let world_result = tauri::async_runtime::spawn_blocking(world_manager::list_world_tasks)
        .await
        .map_err(|error| CommandError::new("TASK_FAILED", format!("World activity query failed: {error}")))?;

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
