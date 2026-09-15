use crate::commands::error::{CommandError, CommandResult};
use crate::engine::server_health::{self, ServerHealthSnapshot};

#[tauri::command]
pub fn launcher_server_health(id: String) -> CommandResult<ServerHealthSnapshot> {
    server_health::inspect(&id).map_err(CommandError::from)
}
