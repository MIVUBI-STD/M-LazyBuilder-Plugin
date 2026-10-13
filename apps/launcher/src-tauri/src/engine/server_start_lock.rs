use crate::engine::persistence;
use serde::Deserialize;
use std::fs::{self, File, OpenOptions, TryLockError};
use std::io::{Read, Seek, SeekFrom};
use std::path::{Path, PathBuf};
use sysinfo::{Pid, System};

const START_IN_PROGRESS_MESSAGE: &str = "A LazyBuilder server lifecycle change is currently in progress. Wait for it to finish before changing the active server or starting, stopping, or recovering another server.";
const MAX_LEGACY_MARKER_BYTES: u64 = 4096;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct LegacyStartLockMarker {
    launcher_pid: u32,
    process_start_time: u64,
}

/// One OS-backed exclusive lock for server lifecycle and active selection.
///
/// Keep the underlying file in place. Removing a locked pathname would allow a
/// second process to create and lock a different file at the same path. The
/// operating system releases the lock automatically when its handle closes,
/// including after an unclean process exit.
pub struct ServerStartLease {
    _file: File,
}

impl ServerStartLease {
    pub fn acquire() -> Result<Self, String> {
        Self::acquire_at(&lock_path()?)
    }

    fn acquire_at(path: &Path) -> Result<Self, String> {
        let parent = path.parent().ok_or_else(|| "Server lifecycle lock has no parent directory".to_string())?;
        fs::create_dir_all(parent)
            .map_err(|error| format!("Could not prepare server lifecycle lock directory: {error}"))?;
        persistence::safe_path::ensure_real_directory(parent, "server lifecycle lock directory")?;
        if persistence::safe_path::entry_exists(path, "server lifecycle lock")? {
            persistence::safe_path::ensure_regular_file(path, "server lifecycle lock")?;
        }

        let mut file = OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .open(path)
            .map_err(|error| format!("Could not open server lifecycle lock: {error}"))?;

        match file.try_lock() {
            Ok(()) => {}
            Err(TryLockError::WouldBlock) => return Err(START_IN_PROGRESS_MESSAGE.into()),
            Err(TryLockError::Error(error)) => {
                return Err(format!("Could not acquire operating-system server lifecycle lock: {error}"));
            }
        }

        // A running pre-migration Launcher may still use the old PID marker
        // instead of the OS lock. Refuse that transition rather than silently
        // allowing two versions to mutate the same workspace concurrently.
        if legacy_owner_is_alive(&mut file)? {
            return Err(START_IN_PROGRESS_MESSAGE.into());
        }

        // Clear the old marker only while this process holds exclusive OS
        // ownership. Future leases never treat their own old PID as active.
        file.set_len(0)
            .map_err(|error| format!("Could not reset legacy server lifecycle marker: {error}"))?;
        file.sync_all()
            .map_err(|error| format!("Could not flush server lifecycle lock metadata: {error}"))?;
        Ok(Self { _file: file })
    }
}

fn legacy_owner_is_alive(file: &mut File) -> Result<bool, String> {
    let size = file.metadata()
        .map_err(|error| format!("Could not inspect previous server lifecycle lock: {error}"))?
        .len();
    if size == 0 {
        return Ok(false);
    }
    if size > MAX_LEGACY_MARKER_BYTES {
        return Err("Previous server lifecycle lock is oversized. It was preserved for explicit recovery.".into());
    }
    file.seek(SeekFrom::Start(0))
        .map_err(|error| format!("Could not seek previous server lifecycle lock: {error}"))?;
    let mut text = String::new();
    file.read_to_string(&mut text)
        .map_err(|error| format!("Could not read previous server lifecycle lock: {error}"))?;
    if text.trim().is_empty() {
        return Ok(false);
    }
    let marker: LegacyStartLockMarker = serde_json::from_str(&text)
        .map_err(|_| "Previous server lifecycle lock is incomplete or malformed; it was preserved for explicit recovery.".to_string())?;
    if marker.launcher_pid == 0 || marker.process_start_time == 0 {
        return Err("Previous server lifecycle lock has an invalid process identity; it was preserved for explicit recovery.".into());
    }
    let pid = Pid::from_u32(marker.launcher_pid);
    let mut system = System::new_all();
    system.refresh_process(pid);
    Ok(system.process(pid)
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
    use super::*;
    use std::sync::atomic::{AtomicU64, Ordering};

    static NEXT_TEST_ID: AtomicU64 = AtomicU64::new(1);

    fn test_path(label: &str) -> PathBuf {
        let id = NEXT_TEST_ID.fetch_add(1, Ordering::Relaxed);
        std::env::temp_dir().join(format!(
            "lazybuilder-server-lifecycle-{label}-{}-{id}",
            std::process::id()
        )).join("server-start.lock")
    }

    // These probes execute in a different OS process, not merely a second
    // handle in the same Rust test process. The filter is unique and the
    // parent also verifies that exactly one child test actually ran.
    #[test]
    fn child_server_lifecycle_lock_probe() {
        let Ok(expected) = std::env::var("LAZYBUILDER_TEST_SERVER_LEASE_STATE") else { return; };
        let path = PathBuf::from(std::env::var("LAZYBUILDER_TEST_SERVER_LEASE_PATH").unwrap());
        match expected.as_str() {
            "blocked" => {
                let error = match ServerStartLease::acquire_at(&path) {
                    Ok(_) => panic!("second process obtained a held lifecycle lock"),
                    Err(error) => error,
                };
                assert!(error.contains(START_IN_PROGRESS_MESSAGE), "{error}");
            }
            "available" => {
                let lease = ServerStartLease::acquire_at(&path).unwrap();
                drop(lease);
            }
            "exit_without_drop" => {
                let _lease = ServerStartLease::acquire_at(&path).unwrap();
                // std::process::exit skips Rust Drop; only the OS closes the
                // locked file descriptor. This also works on Windows CI.
                std::process::exit(0);
            }
            _ => panic!("unknown child lock probe state"),
        }
    }

    fn run_server_lifecycle_child(path: &Path, state: &str) {
        let output = std::process::Command::new(std::env::current_exe().unwrap())
            .arg("child_server_lifecycle_lock_probe")
            .arg("--nocapture")
            .env("LAZYBUILDER_TEST_SERVER_LEASE_PATH", path)
            .env("LAZYBUILDER_TEST_SERVER_LEASE_STATE", state)
            .output().unwrap();
        let stdout = String::from_utf8_lossy(&output.stdout);
        let stderr = String::from_utf8_lossy(&output.stderr);
        assert!(output.status.success(), "child probe failed: {stdout}\n{stderr}");
        assert!(stdout.contains("1 passed"), "child lock probe was not executed: {stdout}\n{stderr}");
    }

    #[test]
    fn lifecycle_lock_is_exclusive_between_processes_and_reusable_after_drop() {
        let path = test_path("cross-process");
        let lease = ServerStartLease::acquire_at(&path).unwrap();
        run_server_lifecycle_child(&path, "blocked");
        drop(lease);
        run_server_lifecycle_child(&path, "available");
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn lifecycle_lock_releases_after_child_exits_without_rust_drop() {
        let path = test_path("ungraceful-process-exit");
        let output = std::process::Command::new(std::env::current_exe().unwrap())
            .arg("child_server_lifecycle_lock_probe")
            .arg("--nocapture")
            .env("LAZYBUILDER_TEST_SERVER_LEASE_PATH", &path)
            .env("LAZYBUILDER_TEST_SERVER_LEASE_STATE", "exit_without_drop")
            .output().unwrap();
        assert!(output.status.success(), "child exit test failed: {:?}", output);
        assert!(path.exists(), "child did not create the lock file");
        let next = ServerStartLease::acquire_at(&path).unwrap();
        drop(next);
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn exclusive_lock_releases_on_drop_without_deleting_the_lock_file() {
        let path = test_path("exclusive");
        let first = ServerStartLease::acquire_at(&path).unwrap();
        assert!(ServerStartLease::acquire_at(&path).is_err());
        drop(first);
        assert!(path.exists(), "the locked pathname must not be unlinked");
        let next = ServerStartLease::acquire_at(&path).unwrap();
        drop(next);
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn malformed_legacy_marker_is_preserved_instead_of_treated_as_stale() {
        let path = test_path("malformed");
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(&path, "partial legacy marker").unwrap();
        let error = match ServerStartLease::acquire_at(&path) {
            Ok(_) => panic!("malformed marker must fail closed"),
            Err(error) => error,
        };
        assert!(error.contains("preserved"));
        assert_eq!(fs::read_to_string(&path).unwrap(), "partial legacy marker");
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn stale_legacy_pid_migrates_under_exclusive_os_lock() {
        let path = test_path("stale");
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(&path, r#"{"launcherPid":4294967295,"processStartTime":1}"#).unwrap();
        let lease = ServerStartLease::acquire_at(&path).unwrap();
        assert_eq!(fs::metadata(&path).unwrap().len(), 0);
        drop(lease);
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn live_legacy_pid_must_not_be_superseded() {
        let path = test_path("legacy-live");
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        let pid = Pid::from_u32(std::process::id());
        let mut system = System::new_all();
        system.refresh_process(pid);
        let started = system.process(pid).unwrap().start_time();
        let previous = format!(
            "{{\"launcherPid\":{},\"processStartTime\":{started}}}",
            std::process::id()
        );
        fs::write(&path, &previous).unwrap();
        assert!(ServerStartLease::acquire_at(&path).is_err());
        assert_eq!(fs::read_to_string(&path).unwrap(), previous);
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }
}
