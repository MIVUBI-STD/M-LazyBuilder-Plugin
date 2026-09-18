use crate::engine::{persistence, workspace_registry};
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use sysinfo::{Pid, System};

/// Canonical LazyBuilder Desktop concurrency ceiling.
///
/// This is a product/runtime safety limit, not a recommendation. The runtime
/// registry may own at most this many verified live Paper workspaces at once.
pub const MAX_CONCURRENT_SERVERS: usize = 3;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ProcessMarker {
    pid: u32,
    #[serde(default)]
    process_start_time: u64,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct StartupProcessReconciliation {
    pub running_servers: Vec<String>,
    pub stale_markers_cleared: u32,
    pub issues: Vec<String>,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RunningServerProcess {
    pub workspace_id: String,
    pub workspace_name: String,
    pub pid: u32,
}

pub fn reconcile_registered_process_markers() -> Result<StartupProcessReconciliation, String> {
    let servers = workspace_registry::list()?;
    let mut system = System::new_all();
    let mut result = StartupProcessReconciliation { running_servers: Vec::new(), stale_markers_cleared: 0, issues: Vec::new() };

    for server in servers {
        let root = PathBuf::from(&server.path);
        let marker_path = process_marker_path(&root);
        let marker = match read_marker(&marker_path) {
            Ok(Some(marker)) => marker,
            Ok(None) => continue,
            Err(error) => {
                result.issues.push(format!(
                    "Process marker for {} is unsafe or malformed and was preserved for recovery: {error}",
                    server.name
                ));
                continue;
            }
        };
        let pid = Pid::from_u32(marker.pid);
        system.refresh_process(pid);
        let stale = match system.process(pid) {
            None => true,
            Some(process) if marker.process_start_time != 0 && process.start_time() != marker.process_start_time => true,
            Some(process) if !looks_like_workspace_paper(process, &root) => true,
            Some(_) => false,
        };
        if stale {
            match persistence::safe_path::remove_regular_file_if_present(&marker_path, "managed Paper process marker") {
                Ok(()) => result.stale_markers_cleared = result.stale_markers_cleared.saturating_add(1),
                Err(error) => result.issues.push(format!("Could not clear stale process marker for {}: {error}", server.name)),
            }
        } else {
            result.running_servers.push(server.name);
        }
    }
    Ok(result)
}

/// Returns only registered workspaces whose process markers resolve to the
/// exact live Paper process for that workspace. Unreadable or malformed marker
/// evidence fails closed so capacity and mutation checks cannot silently ignore
/// a potentially live managed server.
pub fn running_registered_papers() -> Result<Vec<RunningServerProcess>, String> {
    let servers = workspace_registry::list()?;
    let mut system = System::new_all();
    let mut running = Vec::new();

    for server in servers {
        let root = PathBuf::from(&server.path);
        let Some(marker) = read_marker(&process_marker_path(&root))? else { continue; };
        let pid = Pid::from_u32(marker.pid);
        system.refresh_process(pid);
        let Some(process) = system.process(pid) else { continue; };
        if marker.process_start_time != 0 && process.start_time() != marker.process_start_time { continue; }
        if !looks_like_workspace_paper(process, &root) { continue; }
        running.push(RunningServerProcess {
            workspace_id: server.id,
            workspace_name: server.name,
            pid: marker.pid,
        });
    }

    Ok(running)
}

/// Enforces the three-server ceiling without treating an already-running target
/// as a fourth start. This does not itself grant multi-server ownership; callers
/// must still bind the target workspace to a runtime-registry entry before spawn.
pub fn ensure_concurrent_server_capacity(target_workspace_id: &str) -> Result<(), String> {
    let running = running_registered_papers()?;
    let target_already_running = running.iter().any(|entry| entry.workspace_id == target_workspace_id);
    ensure_capacity(running.len(), target_already_running)
}

fn ensure_capacity(running_count: usize, target_already_running: bool) -> Result<(), String> {
    if target_already_running || running_count < MAX_CONCURRENT_SERVERS {
        return Ok(());
    }
    Err(format!(
        "LazyBuilder can run at most {MAX_CONCURRENT_SERVERS} Paper servers at the same time. Stop one running server before starting another."
    ))
}

pub fn workspace_has_running_paper(workspace_id: &str) -> Result<bool, String> {
    let server = workspace_registry::get(workspace_id)?;
    let root = PathBuf::from(&server.path);
    let marker_path = process_marker_path(&root);
    let Some(marker) = read_marker(&marker_path)? else { return Ok(false); };
    let pid = Pid::from_u32(marker.pid);
    let mut system = System::new_all();
    system.refresh_process(pid);
    let Some(process) = system.process(pid) else { return Ok(false); };
    if marker.process_start_time != 0 && process.start_time() != marker.process_start_time { return Ok(false); }
    Ok(looks_like_workspace_paper(process, &root))
}

pub fn ensure_root_not_running(root: &Path) -> Result<(), String> {
    let canonical = root.canonicalize().map_err(|error| format!("Could not resolve server location for process safety check: {error}"))?;
    let canonical_text = canonical.to_string_lossy().to_ascii_lowercase().replace('/', "\\");
    let mut system = System::new_all();
    system.refresh_all();
    for process in system.processes().values() {
        let name = process.name().to_ascii_lowercase();
        if !name.contains("java") { continue; }
        let cwd_match = process.cwd().is_some_and(|cwd| cwd.to_string_lossy().to_ascii_lowercase().replace('/', "\\") == canonical_text);
        let command = process.cmd().join(" ").to_ascii_lowercase().replace('/', "\\");
        if cwd_match || command.contains(&canonical_text) {
            return Err("A Java/Minecraft server process is currently using the selected server folder. Stop that server before adoption so LazyBuilder does not move live runtime files.".into());
        }
    }
    Ok(())
}

fn process_marker_path(workspace: &Path) -> PathBuf { workspace.join("tools").join("lazybuilder").join("cache").join("server-process.json") }

fn read_marker(path: &Path) -> Result<Option<ProcessMarker>, String> {
    persistence::recover_atomic_file(path, "managed Paper process marker")?;
    if !persistence::metadata_entry_exists(path, "managed Paper process marker")? {
        return Ok(None);
    }
    let marker = persistence::read_json(path, "managed Paper process marker")
        .map_err(|error| format!(
            "Managed Paper process marker {} is unsafe or malformed: {error}",
            path.display()
        ))?;
    persistence::cleanup_recovery_files(path, "managed Paper process marker")?;
    Ok(Some(marker))
}

fn looks_like_workspace_paper(process: &sysinfo::Process, workspace: &Path) -> bool {
    let name = process.name().to_ascii_lowercase();
    if !name.contains("java") { return false; }
    let command = process.cmd().join(" ").to_ascii_lowercase();
    let worlds = workspace.join("world-system").join("worlds").display().to_string().to_ascii_lowercase();
    command.contains("-jar") && command.contains("--universe") && command.contains("nogui") && command.contains(&worlds)
}

#[cfg(test)]
mod tests {
    use super::{ensure_capacity, process_marker_path, MAX_CONCURRENT_SERVERS};
    use std::path::Path;

    #[test]
    fn marker_path_stays_workspace_local() {
        assert_eq!(process_marker_path(Path::new("C:/BuildServer")), Path::new("C:/BuildServer/tools/lazybuilder/cache/server-process.json"));
    }

    #[test]
    fn three_server_capacity_is_canonical() {
        assert_eq!(MAX_CONCURRENT_SERVERS, 3);
        assert!(ensure_capacity(0, false).is_ok());
        assert!(ensure_capacity(2, false).is_ok());
        assert!(ensure_capacity(3, true).is_ok());
        assert!(ensure_capacity(3, false).is_err());
        assert!(ensure_capacity(4, false).is_err());
    }
}
