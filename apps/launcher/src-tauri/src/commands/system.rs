use crate::commands::error::{CommandError, CommandResult};
use crate::engine::operations::OperationRegistry;
use crate::engine::server_runtime_registry::ServerRuntimeRegistry;
use crate::engine::system_kernel::{SystemKernel, SystemSnapshot};
use tauri::State;

#[tauri::command]
pub fn system_snapshot(
    kernel: State<'_, SystemKernel>,
    runtimes: State<'_, ServerRuntimeRegistry>,
    operations: State<'_, OperationRegistry>,
) -> CommandResult<SystemSnapshot> {
    kernel.snapshot(&runtimes, &operations).map_err(CommandError::from)
}
