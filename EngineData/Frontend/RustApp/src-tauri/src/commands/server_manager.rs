use crate::engine::{java_runtime, paper_performance, runtime_updates, server_process_guard, server_start_lock::ServerStartLease, startup_guard, workspace_registry};
use crate::engine::server_manager::{DetachedRecoveryResult, ServerManagerState, ServerPreflight, ServerSnapshot};
use tauri::{AppHandle, Manager, State};

#[tauri::command]
pub async fn server_preflight(app: AppHandle) -> Result<ServerPreflight, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<ServerManagerState>();
        Ok(state.preflight())
    })
    .await
    .map_err(|error| format!("Server preflight task failed: {error}"))?
}

#[tauri::command]
pub fn server_snapshot(state: State<'_, ServerManagerState>) -> Result<ServerSnapshot, String> {
    state.snapshot()
}

#[tauri::command]
pub async fn server_start(app: AppHandle) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<ServerManagerState>();
        let _lease = ServerStartLease::acquire()?;
        prepare_managed_start(&app)?;
        state.start()
    })
    .await
    .map_err(|error| format!("Server start task failed: {error}"))?
}

#[tauri::command]
pub async fn server_stop(app: AppHandle) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<ServerManagerState>();
        stop_with_recovery(&state)
    })
    .await
    .map_err(|error| format!("Server stop task failed: {error}"))?
}

#[tauri::command]
pub async fn server_restart(app: AppHandle) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<ServerManagerState>();
        stop_with_recovery(&state)?;
        let _lease = ServerStartLease::acquire()?;
        prepare_managed_start(&app)?;
        state.start()
    })
    .await
    .map_err(|error| format!("Server restart task failed: {error}"))?
}

#[tauri::command]
pub async fn server_recover_detached(app: AppHandle) -> Result<DetachedRecoveryResult, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<ServerManagerState>();
        state.recover_detached()
    })
    .await
    .map_err(|error| format!("Detached server recovery task failed: {error}"))?
}

/// One canonical preparation path for both Start and Restart.
///
/// Core-module synchronization intentionally happens before the final provisioning
/// check. This allows a valid prepared workspace with a missing/stale bundled core
/// JAR to self-heal at start time instead of being rejected before the sync owner
/// gets a chance to repair it.
fn prepare_managed_start(app: &AppHandle) -> Result<(), String> {
    let active = workspace_registry::current()?
        .ok_or_else(|| "No LazyBuilder server workspace is active.".to_string())?;
    server_process_guard::ensure_no_running_paper_except(Some(&active.id))?;
    ensure_base_provisioned()?;
    ensure_bundled_core(app)?;
    ensure_provisioned()?;
    startup_guard::ensure_memory_headroom()?;
    let performance = paper_performance::apply_before_managed_start()?;
    let _performance_summary = (performance.changed, performance.message);
    Ok(())
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

/// Validate the pieces that must already exist before start-time self-healing.
/// Core modules are excluded here because they are owned by ensure_bundled_core().
fn ensure_base_provisioned() -> Result<(), String> {
    let status = workspace_registry::provisioning_status()?;
    if !status.java_ready || !java_runtime::managed_java_ready() {
        return Err("Managed Java 21 is not ready. Use Prepare Server first.".into());
    }
    if !status.workspace_created || !status.paper_ready || !status.config_ready {
        return Err(format!("Server provisioning is incomplete: {}. Use Prepare Server first.", status.next_step));
    }
    if !status.eula_accepted {
        return Err("Minecraft EULA has not been accepted for this server.".into());
    }
    Ok(())
}

fn ensure_provisioned() -> Result<(), String> {
    let status = workspace_registry::provisioning_status()?;
    if !status.java_ready || !java_runtime::managed_java_ready() {
        return Err("Managed Java 21 is not ready. Use Prepare Server first.".into());
    }
    if !status.workspace_created || !status.paper_ready || !status.core_modules_ready || !status.config_ready {
        return Err(format!("Server provisioning is incomplete: {}. Use Prepare Server first.", status.next_step));
    }
    if !status.eula_accepted {
        return Err("Minecraft EULA has not been accepted for this server.".into());
    }
    Ok(())
}
