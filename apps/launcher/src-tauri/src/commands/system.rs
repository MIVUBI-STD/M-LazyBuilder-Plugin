use crate::commands::error::{CommandError, CommandResult};
use crate::engine::operations::OperationRegistry;
use crate::engine::server_runtime_registry::ServerRuntimeRegistry;
use crate::engine::system_kernel::{CapabilityStatus, SystemKernel, SystemSnapshot};
use crate::engine::world_manager;
use tauri::State;

#[tauri::command]
pub async fn system_snapshot(
    kernel: State<'_, SystemKernel>,
    runtimes: State<'_, ServerRuntimeRegistry>,
    operations: State<'_, OperationRegistry>,
) -> CommandResult<SystemSnapshot> {
    let mut snapshot = kernel.snapshot(&runtimes, &operations).map_err(CommandError::from)?;
    let Some(active) = snapshot.workspace.as_ref() else {
        snapshot.capabilities.push(world_capability(false, "Open a workspace first."));
        return Ok(snapshot);
    };

    let runtime_online = snapshot.runtimes.iter().any(|runtime| {
        runtime.workspace_id == active.id && matches!(runtime.state.as_str(), "Online" | "Detached")
    });
    if !runtime_online {
        snapshot.capabilities.push(world_capability(false, "Start the Paper server first."));
        return Ok(snapshot);
    }

    let status = tauri::async_runtime::spawn_blocking(world_manager::bridge_status)
        .await
        .map_err(|error| CommandError::new("TASK_FAILED", format!("World capability query failed: {error}")))?;

    match status {
        Ok(status) => {
            let required = ["world.list", "world.tasks"];
            let available = required.iter().all(|required| status.capabilities.iter().any(|value| value == required));
            snapshot.capabilities.push(world_capability(
                available,
                if available {
                    "World Manager advertised the required world-control capabilities."
                } else {
                    "World Manager is connected but does not advertise the required world-control capabilities."
                },
            ));
        }
        Err(error) => {
            snapshot.warnings.push(format!("World capability handshake is unavailable: {error}"));
            snapshot.capabilities.push(world_capability(false, "World Manager capability handshake is unavailable."));
        }
    }
    Ok(snapshot)
}

fn world_capability(available: bool, reason: &str) -> CapabilityStatus {
    CapabilityStatus { key: "world.manage".into(), available, reason: reason.into() }
}
