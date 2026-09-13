use crate::engine::{java_runtime, paper_performance, runtime_updates, startup_guard, workspace_registry};
use crate::engine::server_manager::{DetachedRecoveryResult, ServerManagerState, ServerPreflight, ServerSnapshot};
use tauri::{AppHandle, Manager, State};

#[tauri::command]
pub fn server_preflight(state: State<'_, ServerManagerState>) -> ServerPreflight {
    state.preflight()
}

#[tauri::command]
pub fn server_snapshot(state: State<'_, ServerManagerState>) -> Result<ServerSnapshot, String> {
    state.snapshot()
}

#[tauri::command]
pub fn server_start(app: AppHandle, state: State<'_, ServerManagerState>) -> Result<(), String> {
    ensure_provisioned()?;
    ensure_bundled_core(&app)?;
    startup_guard::ensure_memory_headroom()?;
    let performance = paper_performance::apply_before_managed_start()?;
    let _performance_summary = (performance.changed, performance.message);
    state.start()
}

#[tauri::command]
pub fn server_stop(state: State<'_, ServerManagerState>) -> Result<(), String> {
    stop_with_recovery(&state)
}

#[tauri::command]
pub fn server_restart(app: AppHandle, state: State<'_, ServerManagerState>) -> Result<(), String> {
    stop_with_recovery(&state)?;
    ensure_provisioned()?;
    ensure_bundled_core(&app)?;
    startup_guard::ensure_memory_headroom()?;
    let performance = paper_performance::apply_before_managed_start()?;
    let _performance_summary = (performance.changed, performance.message);
    state.start()
}

#[tauri::command]
pub fn server_recover_detached(state: State<'_, ServerManagerState>) -> Result<DetachedRecoveryResult, String> {
    state.recover_detached()
}

fn ensure_bundled_core(app: &AppHandle) -> Result<(), String> {
    let resource_dir = app.path().resource_dir().ok();
    runtime_updates::ensure_core_current(resource_dir.as_deref())
}

fn stop_with_recovery(state: &ServerManagerState) -> Result<(), String> {
    match state.stop() {
        Ok(()) => Ok(()),
        Err(stop_error) => {
            // A failed write/flush to Paper stdin can leave the owned child alive after
            // ServerManagerState has already entered Stopping. Only in that proven
            // state do we fall back to the same ServerManager recovery owner.
            let snapshot = state.snapshot().map_err(|snapshot_error| {
                format!("{stop_error}; additionally failed to inspect stop recovery state: {snapshot_error}")
            })?;
            if snapshot.state != "Stopping" || snapshot.pid.is_none() {
                return Err(stop_error);
            }

            match state.recover_detached() {
                Ok(result) if result.stopped => {
                    // Reap the still-owned Child handle after the external termination.
                    let _ = state.snapshot();
                    Ok(())
                }
                Ok(result) => Err(format!(
                    "{stop_error}; stop recovery did not terminate PID {}: {}",
                    result.pid, result.message
                )),
                Err(recovery_error) => Err(format!("{stop_error}; stop recovery also failed: {recovery_error}")),
            }
        }
    }
}

fn ensure_provisioned() -> Result<(), String> {
    if !java_runtime::managed_java_path()?.is_file() {
        return Err("Managed Java 21 is not ready. Use Prepare Server first.".into());
    }
    let status = workspace_registry::provisioning_status()?;
    if !status.workspace_created || !status.paper_ready || !status.core_modules_ready || !status.config_ready {
        return Err(format!("Server provisioning is incomplete: {}. Use Prepare Server first.", status.next_step));
    }
    if !status.eula_accepted {
        return Err("Minecraft EULA has not been accepted for this server.".into());
    }
    Ok(())
}
