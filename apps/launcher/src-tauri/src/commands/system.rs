use crate::commands::error::{CommandError, CommandResult};
use crate::engine::operations::OperationRegistry;
use crate::engine::server_runtime_registry::ServerRuntimeRegistry;
use crate::engine::system::{SystemSnapshot, SystemSnapshotService};
use tauri::State;

#[tauri::command]
pub async fn system_snapshot(
    service: State<'_, SystemSnapshotService>,
    runtimes: State<'_, ServerRuntimeRegistry>,
    operations: State<'_, OperationRegistry>,
) -> CommandResult<SystemSnapshot> {
    service.snapshot(&runtimes, &operations).await.map_err(CommandError::from)
}
