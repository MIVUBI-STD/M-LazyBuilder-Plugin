use crate::engine::server_manager::{ServerManagerState, ServerPreflight, ServerSnapshot};
use tauri::State;

#[tauri::command]
pub fn server_preflight(state: State<'_, ServerManagerState>) -> ServerPreflight {
    state.preflight()
}

#[tauri::command]
pub fn server_snapshot(state: State<'_, ServerManagerState>) -> Result<ServerSnapshot, String> {
    state.snapshot()
}

#[tauri::command]
pub fn server_start(state: State<'_, ServerManagerState>) -> Result<(), String> {
    state.start()
}

#[tauri::command]
pub fn server_stop(state: State<'_, ServerManagerState>) -> Result<(), String> {
    state.stop()
}

#[tauri::command]
pub fn server_restart(state: State<'_, ServerManagerState>) -> Result<(), String> {
    state.restart()
}
