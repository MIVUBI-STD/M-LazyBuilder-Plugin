use crate::engine::{java_runtime, runtime_updates, server_process_guard, server_start_lock::ServerStartLease, startup_guard, workspace_registry};
use crate::engine::operations::OperationRegistry;
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
/// Core synchronization may repair only LazyBuilder-owned bundled modules.
/// Server behavior/performance configuration is never mutated as a side effect of Start.
fn prepare_managed_start(app: &AppHandle) -> Result<(), String> {
    let active = workspace_registry::current()?
        .ok_or_else(|| "No LazyBuilder server workspace is active.".to_string())?;
    let resource = format!("workspace:{}", active.id);
    if app.state::<OperationRegistry>().has_active_for_resource(&resource)? {
        return Err("A Launcher operation is still changing this server. Wait for it to finish before starting Paper.".into());
    }
    server_process_guard::ensure_no_running_paper_except(Some(&active.id))?;
    ensure_base_provisioned()?;
    ensure_bundled_core(app)?;
    ensure_provisioned()?;
    startup_guard::ensure_memory_headroom()?;
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
            let snapshot = state.snapshot().map_err(|snapshot_error| {
                format!("{stop_error}; additionally failed to inspect stop recovery state: {snapshot_error}")
            })?;

            // A controller-owned process may still be winding down (Stopping), while a
            // process surviving a launcher restart/crash is represented as Detached.
            // Both states are safe recovery candidates because recover_detached() also
            // validates PID, process start time and the LazyBuilder Paper command line
            // before terminating anything.
            if !is_recoverable_stop_snapshot(&snapshot) {
                return Err(stop_error);
            }

            match state.recover_detached() {
                Ok(result) if result.stopped => {
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

fn is_recoverable_stop_snapshot(snapshot: &ServerSnapshot) -> bool {
    matches!(snapshot.state.as_str(), "Stopping" | "Detached") && snapshot.pid.is_some()
}

/// Validate the pieces that must already exist before start-time core self-healing.
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

#[cfg(test)]
mod tests {
    use super::is_recoverable_stop_snapshot;
    use crate::engine::server_manager::ServerSnapshot;

    fn snapshot(state: &str, pid: Option<u32>) -> ServerSnapshot {
        ServerSnapshot {
            state: state.into(),
            health: "Warning".into(),
            cpu_load_percent: 0.0,
            used_memory_bytes: 0,
            max_memory_bytes: 0,
            pid,
            log_path: String::new(),
        }
    }

    #[test]
    fn detached_process_is_recoverable_for_stop_and_restart() {
        assert!(is_recoverable_stop_snapshot(&snapshot("Detached", Some(42))));
    }

    #[test]
    fn stopping_process_is_recoverable() {
        assert!(is_recoverable_stop_snapshot(&snapshot("Stopping", Some(42))));
    }

    #[test]
    fn non_recovery_states_or_missing_pid_are_rejected() {
        assert!(!is_recoverable_stop_snapshot(&snapshot("Offline", Some(42))));
        assert!(!is_recoverable_stop_snapshot(&snapshot("Detached", None)));
    }
}
