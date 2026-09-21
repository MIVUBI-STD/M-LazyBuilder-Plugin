use crate::commands::error::{CommandError, CommandResult};
use crate::engine::operations::OperationRegistry;
use crate::engine::server_runtime_registry::ServerRuntimeRegistry;
use crate::engine::system::{SystemActivityService, SystemActivitySnapshot};
use tauri::State;

#[tauri::command]
pub async fn system_activity_snapshot(
    service: State<'_, SystemActivityService>,
    runtimes: State<'_, ServerRuntimeRegistry>,
    operations: State<'_, OperationRegistry>,
) -> CommandResult<SystemActivitySnapshot> {
    service.snapshot(&runtimes, &operations).await.map_err(CommandError::from)
}
