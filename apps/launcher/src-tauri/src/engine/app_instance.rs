use serde::{Deserialize, Serialize};
use std::fs::{self, OpenOptions};
use std::io::{ErrorKind, Write};
use std::path::{Path, PathBuf};
use sysinfo::{Pid, System};

const MAX_ACQUIRE_ATTEMPTS: u8 = 8;

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct InstanceMarker {
    launcher_pid: u32,
    process_start_time: u64,
}

#[derive(Clone, Debug)]
pub struct InstanceStartupState {
    pub previous_session_unclean: bool,
}

pub struct LauncherInstanceLease {
    path: PathBuf,
    marker: InstanceMarker,
}

pub fn acquire() -> Result<(LauncherInstanceLease, InstanceStartupState), String> {
    let path = instance_path()?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| format!("Could not prepare Launcher instance state: {error}"))?;
    }

    let marker = current_launcher_marker()?;
    let mut previous_session_unclean = false;

    for attempt in 0..MAX_ACQUIRE_ATTEMPTS {
        match try_create(&path, &marker) {
            Ok(()) => {
                return Ok((
                    LauncherInstanceLease { path, marker },
                    InstanceStartupState { previous_session_unclean },
                ));
            }
            Err(error) if error.kind() == ErrorKind::AlreadyExists => {
                if instance_owner_is_alive(&path)? {
                    return Err("LazyBuilder is already running. Use the existing Launcher window instead of opening a second instance.".into());
                }

                previous_session_unclean = true;
                let quarantine = stale_instance_path(&path, marker.launcher_pid, attempt);
                let _ = fs::remove_file(&quarantine);
                match fs::rename(&path, &quarantine) {
                    Ok(()) => {
                        let _ = fs::remove_file(&quarantine);
                        continue;
                    }
                    Err(rename_error) if rename_error.kind() == ErrorKind::NotFound => continue,
                    Err(rename_error) => {
                        if instance_owner_is_alive(&path)? {
                            return Err("LazyBuilder is already running. Use the existing Launcher window instead of opening a second instance.".into());
                        }
                        return Err(format!("Could not retire stale Launcher instance state: {rename_error}"));
                    }
                }
            }
            Err(error) => return Err(format!("Could not acquire Launcher instance authority: {error}")),
        }
    }

    Err("Could not acquire Launcher instance authority after repeated concurrent changes. Close other LazyBuilder windows and try again.".into())
}

impl Drop for LauncherInstanceLease {
    fn drop(&mut self) {
        let Ok(text) = fs::read_to_string(&self.path) else { return; };
        let Ok(existing) = serde_json::from_str::<InstanceMarker>(&text) else { return; };
        if existing.launcher_pid == self.marker.launcher_pid
            && existing.process_start_time == self.marker.process_start_time
        {
            let _ = fs::remove_file(&self.path);
        }
    }
}

fn try_create(path: &Path, marker: &InstanceMarker) -> Result<(), std::io::Error> {
    let mut file = OpenOptions::new().create_new(true).write(true).open(path)?;
    let result = (|| {
        let text = serde_json::to_string(marker).map_err(std::io::Error::other)?;
        file.write_all(text.as_bytes())?;
        file.sync_all()
    })();
    if result.is_err() {
        drop(file);
        let _ = fs::remove_file(path);
    }
    result
}

fn current_launcher_marker() -> Result<InstanceMarker, String> {
    let launcher_pid = std::process::id();
    let pid = Pid::from_u32(launcher_pid);
    let mut system = System::new_all();
    system.refresh_process(pid);
    let process_start_time = system
        .process(pid)
        .map(|process| process.start_time())
        .ok_or_else(|| "Could not inspect the LazyBuilder process for single-instance coordination.".to_string())?;
    Ok(InstanceMarker { launcher_pid, process_start_time })
}

fn instance_owner_is_alive(path: &Path) -> Result<bool, String> {
    let text = match fs::read_to_string(path) {
        Ok(value) => value,
        Err(error) if error.kind() == ErrorKind::NotFound => return Ok(false),
        Err(error) => return Err(format!("Could not inspect Launcher instance state: {error}")),
    };
    let marker = match serde_json::from_str::<InstanceMarker>(&text) {
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

fn instance_path() -> Result<PathBuf, String> {
    let base = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())?;
    Ok(base.join("LazyBuilder").join("launcher-instance.json"))
}

fn stale_instance_path(path: &Path, launcher_pid: u32, attempt: u8) -> PathBuf {
    let parent = path.parent().map(PathBuf::from).unwrap_or_default();
    parent.join(format!("launcher-instance.stale.{launcher_pid}.{attempt}.json"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn instance_marker_round_trips() {
        let marker = InstanceMarker { launcher_pid: 42, process_start_time: 1234 };
        let encoded = serde_json::to_string(&marker).expect("serialize marker");
        let decoded: InstanceMarker = serde_json::from_str(&encoded).expect("deserialize marker");
        assert_eq!(decoded.launcher_pid, 42);
        assert_eq!(decoded.process_start_time, 1234);
    }

    #[test]
    fn stale_instance_quarantine_is_unique_per_attempt() {
        let path = Path::new("C:/Temp/LazyBuilder/launcher-instance.json");
        assert_ne!(stale_instance_path(path, 42, 0), stale_instance_path(path, 42, 1));
    }
}
