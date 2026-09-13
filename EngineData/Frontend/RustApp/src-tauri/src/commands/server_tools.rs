use crate::engine::{paths, process_identity};
use serde::{Deserialize, Serialize};
use std::fs::{self, File};
use std::io::{Read, Seek, SeekFrom};
use std::path::PathBuf;
use std::thread;
use std::time::{Duration, Instant};
use sysinfo::{Pid, System};

const MAX_LOG_TAIL_BYTES: u64 = 256 * 1024;
const MAX_LOG_LINES: usize = 300;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ProcessMarker {
    pid: u32,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DetachedRecoveryResult {
    pub pid: u32,
    pub stopped: bool,
    pub message: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerLogTail {
    pub path: String,
    pub content: String,
    pub truncated: bool,
}

#[tauri::command]
pub fn server_log_tail(path: String) -> Result<ServerLogTail, String> {
    let workspace = canonical_or_normalized(paths::workspace_root()?);
    let requested = if path.trim().is_empty() {
        workspace.join("server").join("logs").join("latest.log")
    } else {
        PathBuf::from(path.trim())
    };
    let requested = canonical_or_normalized(requested);

    let is_log = requested
        .extension()
        .and_then(|value| value.to_str())
        .map(|value| value.eq_ignore_ascii_case("log")) == Some(true);
    let is_logs_child = requested
        .parent()
        .and_then(|parent| parent.file_name())
        .and_then(|value| value.to_str())
        .map(|value| value.eq_ignore_ascii_case("logs")) == Some(true);
    if !requested.starts_with(&workspace) || !is_log || !is_logs_child {
        return Err("Refusing to read a log outside a LazyBuilder workspace server log directory.".into());
    }
    if !requested.is_file() {
        return Ok(ServerLogTail {
            path: requested.display().to_string(),
            content: String::new(),
            truncated: false,
        });
    }

    let mut file = File::open(&requested).map_err(|error| error.to_string())?;
    let length = file.metadata().map_err(|error| error.to_string())?.len();
    let start = length.saturating_sub(MAX_LOG_TAIL_BYTES);
    file.seek(SeekFrom::Start(start)).map_err(|error| error.to_string())?;
    let mut bytes = Vec::with_capacity((length - start) as usize);
    file.read_to_end(&mut bytes).map_err(|error| error.to_string())?;
    let text = String::from_utf8_lossy(&bytes);
    let mut lines = text.lines().collect::<Vec<_>>();
    let truncated_by_lines = lines.len() > MAX_LOG_LINES;
    if truncated_by_lines {
        lines = lines.split_off(lines.len() - MAX_LOG_LINES);
    }

    Ok(ServerLogTail {
        path: requested.display().to_string(),
        content: lines.join("\n"),
        truncated: start > 0 || truncated_by_lines,
    })
}

#[tauri::command]
pub fn server_recover_detached() -> Result<DetachedRecoveryResult, String> {
    // Clear stale PID markers first using the persisted PID + process start-time
    // identity. Recovery must never terminate a process based only on a reused PID.
    process_identity::sanitize_before_start()?;

    let marker_path = paths::lazybuilder_cache_dir()?.join("server-process.json");
    if !marker_path.is_file() {
        return Err("No detached LazyBuilder Paper process marker exists.".into());
    }
    let marker_text = fs::read_to_string(&marker_path).map_err(|error| error.to_string())?;
    let marker: ProcessMarker = serde_json::from_str(&marker_text)
        .map_err(|_| "The managed Paper process marker is invalid.".to_string())?;

    let pid = Pid::from_u32(marker.pid);
    let mut system = System::new_all();
    system.refresh_process(pid);
    let Some(process) = system.process(pid) else {
        fs::remove_file(&marker_path).map_err(|error| error.to_string())?;
        clear_process_identity();
        return Ok(DetachedRecoveryResult {
            pid: marker.pid,
            stopped: true,
            message: "The previous Paper process is no longer running. Its stale recovery marker was cleared.".into(),
        });
    };

    let process_name = process.name().to_ascii_lowercase();
    let command = process.cmd().join(" ");
    let command_lower = command.to_ascii_lowercase();
    let worlds = paths::worlds_dir()?.display().to_string().to_ascii_lowercase();
    let looks_like_managed_paper = process_name.contains("java")
        && command_lower.contains("-jar")
        && command_lower.contains("--universe")
        && command_lower.contains("nogui")
        && command_lower.contains(&worlds);

    if !looks_like_managed_paper {
        return Err(format!(
            "PID {} no longer matches the LazyBuilder-managed Paper command line. Recovery was blocked to avoid terminating an unrelated process.",
            marker.pid
        ));
    }

    if !process.kill() {
        return Err(format!("Windows refused to terminate detached Paper PID {}.", marker.pid));
    }

    let deadline = Instant::now() + Duration::from_secs(10);
    while Instant::now() < deadline {
        thread::sleep(Duration::from_millis(100));
        system.refresh_process(pid);
        if system.process(pid).is_none() {
            fs::remove_file(&marker_path).map_err(|error| error.to_string())?;
            clear_process_identity();
            return Ok(DetachedRecoveryResult {
                pid: marker.pid,
                stopped: true,
                message: format!("Detached Paper PID {} was terminated and the controller is ready to start a new managed instance.", marker.pid),
            });
        }
    }

    Err(format!("Detached Paper PID {} did not exit after the recovery termination request.", marker.pid))
}

fn clear_process_identity() {
    if let Ok(path) = paths::lazybuilder_cache_dir() {
        let _ = fs::remove_file(path.join("server-process-identity.json"));
    }
}

fn canonical_or_normalized(path: PathBuf) -> PathBuf {
    path.canonicalize().unwrap_or_else(|_| {
        if path.is_absolute() {
            path
        } else {
            std::env::current_dir().map(|root| root.join(&path)).unwrap_or(path)
        }
    })
}
