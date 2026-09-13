use crate::engine::{paper_performance, process_identity, startup_guard};
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
    process_identity::sanitize_before_start()?;
    startup_guard::ensure_memory_headroom()?;
    let _ = paper_performance::apply_before_managed_start()?;
    state.start()?;
    // Paper is already running at this point. Identity metadata is a recovery aid and
    // must never turn a successful spawn into a false startup failure in the UI.
    let _ = process_identity::record_after_start();
    Ok(())
}

#[tauri::command]
pub fn server_stop(state: State<'_, ServerManagerState>) -> Result<(), String> {
    state.stop()
}

#[tauri::command]
pub fn server_restart(state: State<'_, ServerManagerState>) -> Result<(), String> {
    state.stop()?;
    process_identity::sanitize_before_start()?;
    startup_guard::ensure_memory_headroom()?;
    let _ = paper_performance::apply_before_managed_start()?;
    state.start()?;
    let _ = process_identity::record_after_start();
    Ok(())
}
