use crate::commands::error::{CommandError, CommandResult, RecoveryAction};
use crate::engine::{java_runtime, paths, runtime_updates, server_config, server_process_guard, server_start_lock::ServerStartLease, startup_guard, workspace_registry, world_manager};
use crate::engine::operations::OperationRegistry;
use crate::engine::server_manager::{DetachedRecoveryResult, ServerManagerState, ServerPreflight, ServerSnapshot};
use crate::engine::server_runtime_registry::{ServerRuntimeRegistry, ServerRuntimeSummary};
use std::fs;
use std::net::TcpListener;
use std::path::PathBuf;
use std::sync::Arc;
use tauri::{AppHandle, Manager, State};

const DEFAULT_PAPER_PORT: u16 = 25565;
const PAPER_PORT_SCAN_LIMIT: u16 = 128;

#[tauri::command]
pub async fn server_preflight(app: AppHandle) -> Result<ServerPreflight, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let registry = app.state::<ServerRuntimeRegistry>();
        let (_, state) = registry.active_runtime()?;
        Ok(state.preflight())
    })
    .await
    .map_err(|error| format!("Server preflight task failed: {error}"))?
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ActiveServerRuntimeStatus {
    pub snapshot: ServerSnapshot,
    pub connection_port: Option<u16>,
}

#[tauri::command]
pub fn server_runtime_status(
    registry: State<'_, ServerRuntimeRegistry>,
) -> Result<ActiveServerRuntimeStatus, String> {
    let (_, state) = registry.active_runtime()?;
    Ok(ActiveServerRuntimeStatus {
        snapshot: state.snapshot()?,
        connection_port: registry.active_paper_port()?,
    })
}

#[tauri::command]
pub fn server_snapshot(
    registry: State<'_, ServerRuntimeRegistry>,
    workspace_id: Option<String>,
) -> Result<ServerSnapshot, String> {
    let (_, state) = resolve_runtime(&registry, workspace_id.as_deref())?;
    state.snapshot()
}

#[tauri::command]
pub fn server_runtime_list(registry: State<'_, ServerRuntimeRegistry>) -> Result<Vec<ServerRuntimeSummary>, String> {
    registry.summaries()
}

#[tauri::command]
pub fn server_connection_port(registry: State<'_, ServerRuntimeRegistry>) -> Result<Option<u16>, String> {
    registry.active_paper_port()
}

#[tauri::command]
pub async fn server_console_command(
    app: AppHandle,
    command: String,
    workspace_id: Option<String>,
) -> CommandResult<()> {
    let command = normalize_console_command(&command)?;
    tauri::async_runtime::spawn_blocking(move || {
        let registry = app.state::<ServerRuntimeRegistry>();
        let (_, state) = resolve_runtime(&registry, workspace_id.as_deref()).map_err(CommandError::runtime)?;
        let snapshot = state.snapshot().map_err(CommandError::runtime)?;
        match snapshot.state.as_str() {
            "Online" => {}
            "Detached" => {
                return Err(CommandError::recoverable_action(
                    "SERVER_CONSOLE_DETACHED",
                    "This Paper process is still running, but LazyBuilder no longer owns its console input after the previous launcher session ended.",
                    RecoveryAction::StopServer,
                ));
            }
            current => {
                return Err(CommandError::recoverable_action(
                    "SERVER_CONSOLE_UNAVAILABLE",
                    format!("Server console is available only while Paper is Online; current state is {current}."),
                    RecoveryAction::StartServer,
                ));
            }
        }

        state.send_console_command(&command).map_err(|error| {
            CommandError::recoverable_action(
                "SERVER_COMMAND_WRITE_FAILED",
                error,
                RecoveryAction::RetryOperation,
            )
        })
    })
    .await
    .map_err(|error| CommandError::new("SERVER_COMMAND_TASK_FAILED", format!("Server command task failed: {error}")))?
}

#[tauri::command]
pub async fn server_start(app: AppHandle) -> CommandResult<()> {
    tauri::async_runtime::spawn_blocking(move || {
        let _lease = ServerStartLease::acquire()?;
        let registry = app.state::<ServerRuntimeRegistry>();
        let (active, state) = registry.active_runtime()?;
        let snapshot = state.snapshot()?;
        match snapshot.state.as_str() {
            "Starting" | "Online" => return Ok(()),
            "Detached" => return Err("This server still has a detached Paper process. Stop or recover that process before starting it again.".into()),
            "Stopping" => return Err("This server is still stopping. Wait until it is Offline before starting it again.".into()),
            _ => {}
        }
        let paper_port = prepare_managed_start(&app, &active.id)?;
        state.start(paper_port)?;
        registry.set_paper_port(&active.id, paper_port)?;
        Ok(())
    })
    .await
    .map_err(|error| CommandError::new("SERVER_START_TASK_FAILED", format!("Server start task failed: {error}")))?
    .map_err(classify_server_start_error)
}

#[tauri::command]
pub async fn server_stop(app: AppHandle, workspace_id: Option<String>) -> CommandResult<()> {
    tauri::async_runtime::spawn_blocking(move || {
        let _lease = ServerStartLease::acquire()?;
        let registry = app.state::<ServerRuntimeRegistry>();
        let (target_id, state) = resolve_runtime(&registry, workspace_id.as_deref())?;
        stop_with_recovery(&state)?;
        registry.remove(&target_id)
    })
    .await
    .map_err(|error| CommandError::new("SERVER_STOP_TASK_FAILED", format!("Server stop task failed: {error}")))?
    .map_err(classify_server_stop_error)
}

#[tauri::command]
pub async fn server_restart(app: AppHandle) -> CommandResult<()> {
    tauri::async_runtime::spawn_blocking(move || {
        let _lease = ServerStartLease::acquire()?;
        let registry = app.state::<ServerRuntimeRegistry>();
        let (active, state) = registry.active_runtime()?;
        stop_with_recovery(&state)?;
        let paper_port = prepare_managed_start(&app, &active.id)?;
        state.start(paper_port)?;
        registry.set_paper_port(&active.id, paper_port)
    })
    .await
    .map_err(|error| CommandError::new("SERVER_RESTART_TASK_FAILED", format!("Server restart task failed: {error}")))?
    .map_err(classify_server_start_error)
}

#[tauri::command]
pub async fn server_recover_detached(
    app: AppHandle,
    workspace_id: Option<String>,
) -> CommandResult<DetachedRecoveryResult> {
    tauri::async_runtime::spawn_blocking(move || {
        let _lease = ServerStartLease::acquire()?;
        let registry = app.state::<ServerRuntimeRegistry>();
        let (target_id, state) = resolve_runtime(&registry, workspace_id.as_deref())?;
        let result = state.recover_detached()?;
        if result.stopped {
            let _ = registry.remove(&target_id);
        }
        Ok(result)
    })
    .await
    .map_err(|error| CommandError::new("DETACHED_RECOVERY_TASK_FAILED", format!("Detached server recovery task failed: {error}")))?
    .map_err(classify_detached_recovery_error)
}

fn is_lifecycle_lease_busy(lower: &str) -> bool {
    lower.contains("server lifecycle change is currently in progress")
        || lower.contains("server lifecycle lock after repeated concurrent changes")
}

fn classify_server_start_error(message: String) -> CommandError {
    let lower = message.to_ascii_lowercase();
    if lower.contains("detached paper process") {
        return CommandError::recoverable_action("SERVER_DETACHED", message, RecoveryAction::StopServer);
    }
    if lower.contains("still stopping") {
        return CommandError::recoverable_action("SERVER_STOPPING", message, RecoveryAction::WaitForServerStop);
    }
    if lower.contains("server location is currently unavailable") {
        return CommandError::recoverable_action("WORKSPACE_UNAVAILABLE", message, RecoveryAction::LocateWorkspace);
    }
    if lower.contains("minecraft eula") || lower.contains("eula has not been accepted") {
        return CommandError::recoverable_action("EULA_REQUIRED", message, RecoveryAction::AcceptEula);
    }
    if lower.contains("prepare server") || lower.contains("provisioning is incomplete") || lower.contains("managed java") {
        return CommandError::recoverable_action("SERVER_NOT_READY", message, RecoveryAction::RepairServer);
    }
    if lower.contains("operation is still changing this server") {
        return CommandError::recoverable_action("OPERATION_BUSY", message, RecoveryAction::OpenActivity);
    }
    if is_lifecycle_lease_busy(&lower) {
        return CommandError::recoverable_action("SERVER_START_BUSY", message, RecoveryAction::WaitForServerStart);
    }
    if lower.contains("at most 3 paper servers") {
        return CommandError::recoverable_action("SERVER_CAPACITY_REACHED", message, RecoveryAction::StopServer);
    }
    if lower.contains("not enough available ram to start another paper server safely") {
        return CommandError::recoverable_action("SERVER_MEMORY_PRESSURE", message, RecoveryAction::RetryOperation);
    }
    if lower.contains("could not find a free paper listen port") {
        return CommandError::recoverable_action("SERVER_PORT_UNAVAILABLE", message, RecoveryAction::RetryOperation);
    }
    CommandError::recoverable_action("SERVER_START_FAILED", message, RecoveryAction::RetryOperation)
}

fn classify_server_stop_error(message: String) -> CommandError {
    let lower = message.to_ascii_lowercase();
    if is_lifecycle_lease_busy(&lower) {
        return CommandError::recoverable_action("SERVER_START_BUSY", message, RecoveryAction::WaitForServerStart);
    }
    CommandError::recoverable_action("SERVER_STOP_FAILED", message, RecoveryAction::RetryOperation)
}

fn classify_detached_recovery_error(message: String) -> CommandError {
    let lower = message.to_ascii_lowercase();
    if is_lifecycle_lease_busy(&lower) {
        return CommandError::recoverable_action("SERVER_START_BUSY", message, RecoveryAction::WaitForServerStart);
    }
    CommandError::recoverable_action("DETACHED_RECOVERY_FAILED", message, RecoveryAction::RetryOperation)
}

fn resolve_runtime(
    registry: &ServerRuntimeRegistry,
    workspace_id: Option<&str>,
) -> Result<(String, Arc<ServerManagerState>), String> {
    if let Some(workspace_id) = workspace_id {
        let id = workspace_id.trim();
        if id.is_empty() {
            return Err("Workspace id is required for targeted server control.".into());
        }
        return Ok((id.to_string(), registry.runtime_for_id(id)?));
    }

    let (active, state) = registry.active_runtime()?;
    Ok((active.id, state))
}

fn normalize_console_command(raw: &str) -> CommandResult<String> {
    let mut command = raw.trim();
    if let Some(without_slash) = command.strip_prefix('/') {
        command = without_slash.trim_start();
    }

    if command.is_empty() {
        return Err(CommandError::recoverable_action(
            "SERVER_COMMAND_EMPTY",
            "Enter a Paper command before sending.",
            RecoveryAction::EditCommand,
        ));
    }
    if command.len() > 4096 {
        return Err(CommandError::recoverable_action(
            "SERVER_COMMAND_TOO_LONG",
            "Server commands are limited to 4096 characters per submission.",
            RecoveryAction::EditCommand,
        ));
    }
    if command.contains('\n') || command.contains('\r') {
        return Err(CommandError::recoverable_action(
            "SERVER_COMMAND_MULTILINE",
            "Send one server command at a time.",
            RecoveryAction::EditCommand,
        ));
    }

    let label = command.split_whitespace().next().unwrap_or_default();
    let base_label = label.rsplit(':').next().unwrap_or(label).to_ascii_lowercase();
    if matches!(base_label.as_str(), "stop" | "restart") {
        return Err(CommandError::recoverable_action(
            "SERVER_COMMAND_LIFECYCLE_BLOCKED",
            format!("The '{base_label}' lifecycle command must use LazyBuilder's server controls so process state and recovery remain correct."),
            RecoveryAction::UseServerControls,
        ));
    }

    Ok(command.to_string())
}

/// One canonical preparation path for both Start and Restart.
/// Core synchronization may repair only LazyBuilder-owned bundled modules.
/// Server behavior/performance configuration is never mutated as a side effect of Start.
fn prepare_managed_start(app: &AppHandle, workspace_id: &str) -> Result<u16, String> {
    let active = workspace_registry::current()?
        .ok_or_else(|| "No LazyBuilder server workspace is active.".to_string())?;
    if active.id != workspace_id {
        return Err("Server start target changed while preparing the runtime. Re-open the server and retry.".into());
    }
    let active_path = PathBuf::from(&active.path);
    if !active_path.is_dir() {
        return Err(format!("Server location is currently unavailable: {}", active_path.display()));
    }
    let resource = format!("workspace:{}", active.id);
    if app.state::<OperationRegistry>().has_active_for_resource(&resource)? {
        return Err("A Launcher operation is still changing this server. Wait for it to finish before starting Paper.".into());
    }
    server_process_guard::ensure_concurrent_server_capacity(&active.id)?;
    ensure_base_provisioned()?;
    ensure_bundled_core(app)?;
    ensure_provisioned()?;
    world_manager::prepare_control_options_for_start()?;
    let active_runtime_count = app.state::<ServerRuntimeRegistry>().active_count()?;
    startup_guard::ensure_memory_headroom(active_runtime_count)?;
    select_paper_port(configured_paper_port(&active.path)?)
}

fn ensure_bundled_core(app: &AppHandle) -> Result<(), String> {
    let resource_dir = app.path().resource_dir().ok();
    runtime_updates::ensure_core_current(resource_dir.as_deref())
}

fn configured_paper_port(workspace_path: &str) -> Result<u16, String> {
    let options = server_config::load()?;
    let server_relative = paths::safe_relative_path(&options.server_directory, "serverDirectory")?;
    let properties_path = PathBuf::from(workspace_path).join(server_relative).join("server.properties");
    if !properties_path.is_file() {
        return Ok(DEFAULT_PAPER_PORT);
    }
    let text = fs::read_to_string(&properties_path)
        .map_err(|error| format!("Could not read {}: {error}", properties_path.display()))?;
    for line in text.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let Some((key, value)) = line.split_once('=') else { continue; };
        if key.trim() != "server-port" {
            continue;
        }
        let port = value.trim().parse::<u16>()
            .map_err(|_| format!("Invalid server-port value in {}", properties_path.display()))?;
        if port == 0 {
            return Err(format!("server-port must be between 1 and 65535 in {}", properties_path.display()));
        }
        return Ok(port);
    }
    Ok(DEFAULT_PAPER_PORT)
}

fn select_paper_port(preferred: u16) -> Result<u16, String> {
    if paper_port_available(preferred) {
        return Ok(preferred);
    }
    let mut candidate = preferred.saturating_add(1).max(1);
    for _ in 0..PAPER_PORT_SCAN_LIMIT {
        if paper_port_available(candidate) {
            return Ok(candidate);
        }
        candidate = candidate.checked_add(1).unwrap_or(DEFAULT_PAPER_PORT);
    }
    Err(format!(
        "LazyBuilder could not find a free Paper listen port after checking {PAPER_PORT_SCAN_LIMIT} candidates starting near {preferred}. Stop a conflicting server or local service and retry."
    ))
}

fn paper_port_available(port: u16) -> bool {
    TcpListener::bind(("0.0.0.0", port)).is_ok()
}

fn stop_with_recovery(state: &ServerManagerState) -> Result<(), String> {
    match state.stop() {
        Ok(()) => Ok(()),
        Err(stop_error) => {
            let snapshot = state.snapshot().map_err(|snapshot_error| {
                format!("{stop_error}; additionally failed to inspect stop recovery state: {snapshot_error}")
            })?;

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
    use super::{is_recoverable_stop_snapshot, normalize_console_command, paper_port_available, select_paper_port};
    use crate::engine::server_manager::ServerSnapshot;
    use std::net::TcpListener;

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

    #[test]
    fn occupied_paper_port_advances_to_next_available_port() {
        let listener = TcpListener::bind(("0.0.0.0", 0)).expect("bind ephemeral test port");
        let port = listener.local_addr().expect("local address").port();
        assert!(!paper_port_available(port));
        let selected = select_paper_port(port).expect("a later port should be available");
        assert_ne!(selected, port);
    }

    #[test]
    fn console_command_accepts_optional_slash_and_trims_input() {
        assert_eq!(normalize_console_command(" /say hello ").unwrap(), "say hello");
    }

    #[test]
    fn console_command_rejects_multiline_input() {
        let error = normalize_console_command("say one\nsay two").expect_err("multiline command must fail");
        assert_eq!(error.code, "SERVER_COMMAND_MULTILINE");
    }

    #[test]
    fn console_command_routes_lifecycle_actions_to_launcher_controls() {
        for value in ["stop", "/restart", "minecraft:stop"] {
            let error = normalize_console_command(value).expect_err("lifecycle command must fail");
            assert_eq!(error.code, "SERVER_COMMAND_LIFECYCLE_BLOCKED");
        }
    }
}
