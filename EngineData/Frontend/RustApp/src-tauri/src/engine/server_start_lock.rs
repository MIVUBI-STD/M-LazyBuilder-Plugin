use serde::{Deserialize, Serialize};
use std::fs::{self, OpenOptions};
use std::io::{ErrorKind, Write};
use std::path::PathBuf;
use sysinfo::{Pid, System};

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct StartLockMarker {
    launcher_pid: u32,
    process_start_time: u64,
}

pub struct ServerStartLease {
    path: PathBuf,
    marker: StartLockMarker,
}

impl ServerStartLease {
    pub fn acquire() -> Result<Self, String> {
        let path = lock_path()?;
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).map_err(|error| error.to_string())?;
        }

        let marker = current_launcher_marker()?;
        match try_create(&path, &marker) {
            Ok(()) => Ok(Self { path, marker }),
            Err(error) if error.kind() == ErrorKind::AlreadyExists => {
                if lock_owner_is_alive(&path)? {
                    return Err("Another LazyBuilder window is currently starting a server. Wait for that start to finish before starting another server.".into());
                }
                fs::remove_file(&path).map_err(|error| format!("Could not clear stale server-start lock: {error}"))?;
                try_create(&path, &marker).map_err(|error| format!("Could not acquire server-start lock: {error}"))?;
                Ok(Self { path, marker })
            }
            Err(error) => Err(format!("Could not acquire server-start lock: {error}")),
        }
    }
}

impl Drop for ServerStartLease {
    fn drop(&mut self) {
        let Ok(text) = fs::read_to_string(&self.path) else {
            return;
        };
        let Ok(existing) = serde_json::from_str::<StartLockMarker>(&text) else {
            return;
        };
        if existing.launcher_pid == self.marker.launcher_pid
            && existing.process_start_time == self.marker.process_start_time
        {
            let _ = fs::remove_file(&self.path);
        }
    }
}

fn try_create(path: &PathBuf, marker: &StartLockMarker) -> Result<(), std::io::Error> {
    let mut file = OpenOptions::new().create_new(true).write(true).open(path)?;
    let text = serde_json::to_string(marker).map_err(std::io::Error::other)?;
    file.write_all(text.as_bytes())?;
    file.sync_data()?;
    Ok(())
}

fn current_launcher_marker() -> Result<StartLockMarker, String> {
    let launcher_pid = std::process::id();
    let pid = Pid::from_u32(launcher_pid);
    let mut system = System::new_all();
    system.refresh_process(pid);
    let process_start_time = system
        .process(pid)
        .map(|process| process.start_time())
        .ok_or_else(|| "Could not inspect the LazyBuilder launcher process for start coordination.".to_string())?;
    Ok(StartLockMarker {
        launcher_pid,
        process_start_time,
    })
}

fn lock_owner_is_alive(path: &PathBuf) -> Result<bool, String> {
    let text = match fs::read_to_string(path) {
        Ok(value) => value,
        Err(error) if error.kind() == ErrorKind::NotFound => return Ok(false),
        Err(error) => return Err(error.to_string()),
    };
    let marker = match serde_json::from_str::<StartLockMarker>(&text) {
        Ok(value) => value,
        Err(_) => return Ok(false),
    };

    let pid = Pid::from_u32(marker.launcher_pid);
    let mut system = System::new_all();
    system.refresh_process(pid);
    Ok(system
        .process(pid)
        .map(|process| process.start_time() == marker.process_start_time)
        .unwrap_or(false))
}

fn lock_path() -> Result<PathBuf, String> {
    let base = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())?;
    Ok(base.join("LazyBuilder").join("server-start.lock"))
}

#[cfg(test)]
mod tests {
    use super::StartLockMarker;

    #[test]
    fn start_lock_marker_round_trips() {
        let marker = StartLockMarker {
            launcher_pid: 42,
            process_start_time: 1234,
        };
        let encoded = serde_json::to_string(&marker).expect("serialize marker");
        let decoded: StartLockMarker = serde_json::from_str(&encoded).expect("deserialize marker");
        assert_eq!(decoded.launcher_pid, 42);
        assert_eq!(decoded.process_start_time, 1234);
    }
}
