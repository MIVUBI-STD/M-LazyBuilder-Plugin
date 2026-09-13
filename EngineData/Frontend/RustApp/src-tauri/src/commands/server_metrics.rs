use crate::engine::paths;
use serde::{Deserialize, Serialize};
use std::fs;
use std::sync::Mutex;
use sysinfo::{Pid, System};
use tauri::State;

#[derive(Default)]
pub struct ServerMetricsState {
    system: Mutex<System>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ProcessMarker {
    pid: u32,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerProcessMetrics {
    pub available: bool,
    pub pid: Option<u32>,
    pub cpu_percent: f32,
    pub process_memory_bytes: u64,
    pub disk_read_bytes: u64,
    pub disk_write_bytes: u64,
}

#[tauri::command]
pub fn server_process_metrics(state: State<'_, ServerMetricsState>) -> Result<ServerProcessMetrics, String> {
    let marker_path = paths::lazybuilder_cache_dir()?.join("server-process.json");
    if !marker_path.is_file() {
        return Ok(ServerProcessMetrics {
            available: false,
            pid: None,
            cpu_percent: 0.0,
            process_memory_bytes: 0,
            disk_read_bytes: 0,
            disk_write_bytes: 0,
        });
    }

    let marker_text = fs::read_to_string(&marker_path).map_err(|error| error.to_string())?;
    let marker: ProcessMarker = match serde_json::from_str(&marker_text) {
        Ok(value) => value,
        Err(_) => {
            return Ok(ServerProcessMetrics {
                available: false,
                pid: None,
                cpu_percent: 0.0,
                process_memory_bytes: 0,
                disk_read_bytes: 0,
                disk_write_bytes: 0,
            });
        }
    };

    let pid = Pid::from_u32(marker.pid);
    let mut system = state.system.lock().map_err(|_| "server metrics lock poisoned".to_string())?;
    system.refresh_process(pid);
    let Some(process) = system.process(pid) else {
        return Ok(ServerProcessMetrics {
            available: false,
            pid: Some(marker.pid),
            cpu_percent: 0.0,
            process_memory_bytes: 0,
            disk_read_bytes: 0,
            disk_write_bytes: 0,
        });
    };

    let disk = process.disk_usage();
    Ok(ServerProcessMetrics {
        available: true,
        pid: Some(marker.pid),
        cpu_percent: process.cpu_usage(),
        process_memory_bytes: process.memory(),
        disk_read_bytes: disk.read_bytes,
        disk_write_bytes: disk.written_bytes,
    })
}
