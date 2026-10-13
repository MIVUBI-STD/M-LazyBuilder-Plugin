use crate::engine::persistence;
use serde::{Deserialize, Serialize};
use std::fs::{self, File, OpenOptions, TryLockError};
use std::io::{Read, Seek, SeekFrom};
use std::path::{Path, PathBuf};
use sysinfo::{Pid, System};

const MAX_INSTANCE_MARKER_BYTES: u64 = 4096;
const OS_LOCK_LEASE_VERSION: u32 = 2;

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct InstanceMarker {
    launcher_pid: u32,
    process_start_time: u64,
    #[serde(default)]
    lease_version: u32,
}

#[derive(Clone, Debug)]
pub struct InstanceStartupState {
    pub previous_session_unclean: bool,
}

/// An OS-exclusive single-instance lease. Keep the pathname stable and let
/// closing this handle release ownership, even following an abrupt exit.
pub struct LauncherInstanceLease {
    file: File,
}

pub fn acquire() -> Result<(LauncherInstanceLease, InstanceStartupState), String> {
    acquire_at(&instance_path()?)
}

fn acquire_at(path: &Path) -> Result<(LauncherInstanceLease, InstanceStartupState), String> {
    let parent = path.parent().ok_or_else(|| "Launcher instance state has no parent directory".to_string())?;
    fs::create_dir_all(parent)
        .map_err(|error| format!("Could not prepare Launcher instance state: {error}"))?;
    persistence::safe_path::ensure_real_directory(parent, "Launcher instance directory")?;
    if persistence::safe_path::entry_exists(path, "Launcher instance state")? {
        persistence::safe_path::ensure_regular_file(path, "Launcher instance state")?;
    }

    let mut file = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .open(path)
        .map_err(|error| format!("Could not open Launcher instance state: {error}"))?;
    match file.try_lock() {
        Ok(()) => {}
        Err(TryLockError::WouldBlock) => {
            return Err("LazyBuilder is already running. Use the existing Launcher window instead of opening a second instance.".into());
        }
        Err(TryLockError::Error(error)) => {
            return Err(format!("Could not acquire exclusive Launcher instance lock: {error}"));
        }
    }

    let previous = read_instance_marker(&mut file)?;
    if let Some(marker) = &previous {
        if marker.lease_version > OS_LOCK_LEASE_VERSION {
            return Err("Launcher instance marker belongs to a newer unsupported version; it was preserved for recovery.".into());
        }
        // Older Launchers used only a PID marker. An active older process
        // does not participate in the OS lock and must still be respected.
        if marker.lease_version < OS_LOCK_LEASE_VERSION && instance_owner_is_alive(marker) {
            return Err("LazyBuilder is already running under the previous Launcher instance protocol.".into());
        }
    }
    let marker = current_launcher_marker()?;
    file.set_len(0)
        .map_err(|error| format!("Could not reset Launcher instance marker: {error}"))?;
    file.seek(SeekFrom::Start(0))
        .map_err(|error| format!("Could not seek Launcher instance marker: {error}"))?;
    serde_json::to_writer(&mut file, &marker)
        .map_err(|error| format!("Could not serialize Launcher instance marker: {error}"))?;
    file.sync_all()
        .map_err(|error| format!("Could not flush Launcher instance marker: {error}"))?;

    Ok((
        LauncherInstanceLease { file },
        InstanceStartupState { previous_session_unclean: previous.is_some() },
    ))
}

impl Drop for LauncherInstanceLease {
    fn drop(&mut self) {
        // Do not unlink the locked pathname. A second process could otherwise
        // open a different file at the same path while this one is still live.
        // Truncation marks a normal exit; the OS releases the lock on close.
        if self.file.set_len(0).is_ok() {
            let _ = self.file.sync_all();
        }
    }
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
    Ok(InstanceMarker {
        launcher_pid,
        process_start_time,
        lease_version: OS_LOCK_LEASE_VERSION,
    })
}

fn instance_owner_is_alive(marker: &InstanceMarker) -> bool {
    let pid = Pid::from_u32(marker.launcher_pid);
    let mut system = System::new_all();
    system.refresh_process(pid);
    system
        .process(pid)
        .map(|process| process.start_time() == marker.process_start_time)
        .unwrap_or(false)
}

fn read_instance_marker(file: &mut File) -> Result<Option<InstanceMarker>, String> {
    let size = file.metadata()
        .map_err(|error| format!("Could not inspect Launcher instance state: {error}"))?
        .len();
    if size == 0 {
        return Ok(None);
    }
    if size > MAX_INSTANCE_MARKER_BYTES {
        return Err(format!(
            "Launcher instance state exceeds the {} byte limit.",
            MAX_INSTANCE_MARKER_BYTES
        ));
    }
    file.seek(SeekFrom::Start(0))
        .map_err(|error| format!("Could not seek Launcher instance state: {error}"))?;
    let mut text = String::new();
    file.read_to_string(&mut text)
        .map_err(|error| format!("Could not read Launcher instance state: {error}"))?;
    if text.trim().is_empty() {
        return Ok(None);
    }
    let marker: InstanceMarker = serde_json::from_str(&text)
        .map_err(|_| "Launcher instance state is incomplete or malformed and was preserved for explicit recovery.".to_string())?;
    if marker.launcher_pid == 0 || marker.process_start_time == 0 {
        return Err("Launcher instance state has an invalid process identity and was preserved for explicit recovery.".into());
    }
    Ok(Some(marker))
}

fn instance_path() -> Result<PathBuf, String> {
    let base = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())?;
    Ok(base.join("LazyBuilder").join("launcher-instance.json"))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicU64, Ordering};

    static NEXT_TEST_ID: AtomicU64 = AtomicU64::new(1);

    fn temp_path(label: &str) -> PathBuf {
        let id = NEXT_TEST_ID.fetch_add(1, Ordering::Relaxed);
        std::env::temp_dir()
            .join(format!("lazybuilder-instance-{label}-{}-{id}", std::process::id()))
            .join("launcher-instance.json")
    }

    #[test]
    fn serializes_legacy_and_native_markers() {
        let legacy: InstanceMarker = serde_json::from_str(
            r#"{"launcherPid":42,"processStartTime":1234}"#
        ).unwrap();
        assert_eq!(legacy.lease_version, 0);
        let current = InstanceMarker { launcher_pid: 42, process_start_time: 1234, lease_version: OS_LOCK_LEASE_VERSION };
        let decoded: InstanceMarker = serde_json::from_str(&serde_json::to_string(&current).unwrap()).unwrap();
        assert_eq!(decoded.lease_version, OS_LOCK_LEASE_VERSION);
    }

    #[test]
    fn exclusive_instance_lock_is_reusable_after_clean_drop() {
        let path = temp_path("exclusive");
        let (lease, status) = acquire_at(&path).unwrap();
        assert!(!status.previous_session_unclean);
        assert!(acquire_at(&path).is_err());
        drop(lease);
        assert!(path.exists(), "the locked pathname must remain stable");
        let (again, status) = acquire_at(&path).unwrap();
        assert!(!status.previous_session_unclean);
        drop(again);
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn leftover_native_marker_is_reported_as_unclean() {
        let path = temp_path("unclean");
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        let marker = current_launcher_marker().unwrap();
        fs::write(&path, serde_json::to_vec(&marker).unwrap()).unwrap();
        let (lease, startup) = acquire_at(&path).unwrap();
        assert!(startup.previous_session_unclean);
        drop(lease);
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn legacy_live_process_cannot_be_superseded() {
        let path = temp_path("legacy-live");
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        let current = current_launcher_marker().unwrap();
        let legacy = InstanceMarker { lease_version: 0, ..current };
        fs::write(&path, serde_json::to_vec(&legacy).unwrap()).unwrap();
        assert!(acquire_at(&path).is_err());
        assert_eq!(fs::read_to_string(&path).unwrap(), serde_json::to_string(&legacy).unwrap());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn newer_unknown_instance_marker_is_preserved_for_recovery() {
        let path = temp_path("newer-schema");
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        let mut marker = current_launcher_marker().unwrap();
        marker.lease_version = OS_LOCK_LEASE_VERSION + 1;
        let original = serde_json::to_string(&marker).unwrap();
        fs::write(&path, &original).unwrap();
        let error = match acquire_at(&path) {
            Ok(_) => panic!("unknown future marker must fail closed"),
            Err(error) => error,
        };
        assert!(error.contains("newer unsupported"));
        assert_eq!(fs::read_to_string(&path).unwrap(), original);
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn malformed_marker_preserves_evidence() {
        let path = temp_path("malformed");
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(&path, "incomplete").unwrap();
        let error = match acquire_at(&path) {
            Ok(_) => panic!("incomplete marker must not silently be retired"),
            Err(error) => error,
        };
        assert!(error.contains("preserved"));
        assert_eq!(fs::read_to_string(&path).unwrap(), "incomplete");
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn oversized_instance_marker_is_rejected_before_parse() {
        let path = temp_path("oversized");
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        let file = File::create(&path).unwrap();
        file.set_len(MAX_INSTANCE_MARKER_BYTES + 1).unwrap();
        drop(file);
        assert!(acquire_at(&path).is_err());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }
}
