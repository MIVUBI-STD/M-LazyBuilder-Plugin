use crate::engine::paths;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;
use sysinfo::{Pid, System};

#[derive(Deserialize)]
struct ProcessMarker {
    pid: u32,
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ProcessIdentity {
    pid: u32,
    process_start_time: u64,
}

pub fn sanitize_before_start() -> Result<(), String> {
    let marker_path = marker_path()?;
    let identity_path = identity_path()?;

    if !marker_path.is_file() {
        let _ = fs::remove_file(identity_path);
        return Ok(());
    }

    let marker = read_marker(&marker_path)?;
    let Some(identity) = read_identity(&identity_path)? else {
        return Ok(());
    };
    if identity.pid != marker.pid {
        remove_marker_pair(&marker_path, &identity_path);
        return Ok(());
    }

    let pid = Pid::from_u32(marker.pid);
    let mut system = System::new_all();
    system.refresh_process(pid);
    let Some(process) = system.process(pid) else {
        remove_marker_pair(&marker_path, &identity_path);
        return Ok(());
    };

    if process.start_time() != identity.process_start_time {
        remove_marker_pair(&marker_path, &identity_path);
    }
    Ok(())
}

pub fn record_after_start() -> Result<(), String> {
    let marker_path = marker_path()?;
    if !marker_path.is_file() {
        return Ok(());
    }
    let marker = read_marker(&marker_path)?;
    let pid = Pid::from_u32(marker.pid);
    let mut system = System::new_all();
    system.refresh_process(pid);
    let Some(process) = system.process(pid) else {
        return Ok(());
    };

    let identity = ProcessIdentity {
        pid: marker.pid,
        process_start_time: process.start_time(),
    };
    let path = identity_path()?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    let temporary = path.with_extension("json.tmp");
    let text = serde_json::to_string_pretty(&identity).map_err(|error| error.to_string())?;
    fs::write(&temporary, text).map_err(|error| error.to_string())?;
    let _ = fs::remove_file(&path);
    fs::rename(temporary, path).map_err(|error| error.to_string())
}

fn read_marker(path: &PathBuf) -> Result<ProcessMarker, String> {
    let text = fs::read_to_string(path).map_err(|error| error.to_string())?;
    serde_json::from_str(&text).map_err(|error| error.to_string())
}

fn read_identity(path: &PathBuf) -> Result<Option<ProcessIdentity>, String> {
    if !path.is_file() {
        return Ok(None);
    }
    let text = fs::read_to_string(path).map_err(|error| error.to_string())?;
    match serde_json::from_str(&text) {
        Ok(value) => Ok(Some(value)),
        Err(_) => {
            let _ = fs::remove_file(path);
            Ok(None)
        }
    }
}

fn remove_marker_pair(marker: &PathBuf, identity: &PathBuf) {
    let _ = fs::remove_file(marker);
    let _ = fs::remove_file(identity);
}

fn marker_path() -> Result<PathBuf, String> {
    Ok(paths::lazybuilder_cache_dir()?.join("server-process.json"))
}

fn identity_path() -> Result<PathBuf, String> {
    Ok(paths::lazybuilder_cache_dir()?.join("server-process-identity.json"))
}
