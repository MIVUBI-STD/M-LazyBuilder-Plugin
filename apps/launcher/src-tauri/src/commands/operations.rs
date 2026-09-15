use crate::engine::operations::{OperationRegistry, OperationSnapshot};
use tauri::State;

#[tauri::command]
pub fn launcher_operation_list(
    operations: State<'_, OperationRegistry>,
) -> Result<Vec<OperationSnapshot>, String> {
    operations.list()
}

#[tauri::command]
pub fn launcher_operation(
    operations: State<'_, OperationRegistry>,
    id: String,
) -> Result<OperationSnapshot, String> {
    operations.get(&id)
}

#[tauri::command]
pub fn launcher_operation_cancel(
    operations: State<'_, OperationRegistry>,
    id: String,
) -> Result<OperationSnapshot, String> {
    operations.request_cancel(&id)
}
