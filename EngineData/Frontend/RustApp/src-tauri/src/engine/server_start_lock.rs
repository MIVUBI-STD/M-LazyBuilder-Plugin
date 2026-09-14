use serde::{Deserialize, Serialize};
use std::fs::{self, OpenOptions};
use std::io::{ErrorKind, Write};
use std::path::PathBuf;
use sysinfo::{Pid, System};

const MAX_ACQUIRE_ATTEMPTS: u8 = 8;

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
        for attempt in 0..MAX_ACQUIRE_ATTEMPTS {
            match try_create(&path, &marker) {
                Ok(()) => return Ok(Self { path, marker }),
                Err(error) if error.kind() == ErrorKind::AlreadyExists => {
                    if lock_owner_is_alive(&path)? {
                        return Err("Another LazyBuilder window is currently starting a server. Wait for that start to finish before starting another server.".into());
                    }

                    // Never delete a stale-looking lock in place. Another launcher may
                    // have replaced it after our liveness check. Atomically renaming the
                    // exact pathname means only one contender can retire the stale file;
                    // everyone else retries and observes the newly created owner.
                    let quarantine = stale_lock_path(&path, marker.launcher_pid, attempt);
                    let _ = fs::remove_file(&quarantine);
                    match fs::rename(&path, &quarantine) {
                        Ok(()) => {
                            let _ = fs::remove_file(&quarantine);
                            continue;
                        }
                        Err(rename_error) if rename_error.kind() == ErrorKind::NotFound => continue,
                        Err(rename_error) => {
                            if lock_owner_is_alive(&path)? {
                                return Err("Another LazyBuilder window is currently starting a server. Wait for that start to finish before starting another server.".into());
                            }
                            return Err(format!("Could not retire stale server-start lock: {rename_error}"));
                        }
                    }
                }
                Err(error) => return Err(format!("Could not acquire server-start lock: {error}")),
            }
        }

        Err("Could not acquire the server-start lock after repeated concurrent changes. Try starting the server again.".into())
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
    let result = (|| {
        let text = serde_json::to_string(marker).map_err(std::io::Error::other)?;
        file.write_all(text.as_bytes())?;
        file.sync_data()
    })();
    if result.is_err() {
        drop(file);
        let _ = fs::remove_file(path);
    }
    result
}

fn stale_lock_path(path: &PathBuf, launcher_pid: u32, attempt: u8) -> PathBuf {
    let parent = path.parent().map(PathBuf::from).unwrap_or_default();
    parent.join(format!("server-start.stale.{launcher_pid}.{attempt}"))
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
    use super::{stale_lock_path, StartLockMarker};
    use std::path::PathBuf;

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

    #[test]
    fn stale_lock_quarantine_is_unique_per_attempt() {
        let path = PathBuf::from("C:/Temp/LazyBuilder/server-start.lock");
        assert_ne!(stale_lock_path(&path, 42, 0), stale_lock_path(&path, 42, 1));
    }
}
