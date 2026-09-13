use crate::engine::{paper_performance, startup_guard};
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
    startup_guard::ensure_memory_headroom()?;
    let _ = paper_performance::apply_before_managed_start()?;
    state.start()
}

#[tauri::command]
pub fn server_stop(state: State<'_, ServerManagerState>) -> Result<(), String> {
    state.stop()
}

#[tauri::command]
pub fn server_restart(state: State<'_, ServerManagerState>) -> Result<(), String> {
    state.stop()?;
    startup_guard::ensure_memory_headroom()?;
    let _ = paper_performance::apply_before_managed_start()?;
    state.start()
}
