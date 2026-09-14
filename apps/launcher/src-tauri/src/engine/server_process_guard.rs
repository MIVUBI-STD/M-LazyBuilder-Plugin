use crate::engine::workspace_registry;
use serde::Deserialize;
use std::fs;
use std::path::{Path, PathBuf};
use sysinfo::{Pid, System};

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ProcessMarker {
    pid: u32,
    #[serde(default)]
    process_start_time: u64,
}

/// Read-only cross-workspace safety guard. ServerManager remains process authority.
pub fn ensure_no_running_paper_except(allowed_workspace_id: Option<&str>) -> Result<(), String> {
    let active_id = workspace_registry::current()?.map(|entry| entry.id);
    let servers = workspace_registry::list()?;
    let mut system = System::new_all();

    for server in servers {
        if active_id.as_deref() == Some(server.id.as_str()) || allowed_workspace_id == Some(server.id.as_str()) { continue; }
        let root = PathBuf::from(&server.path);
        let marker_path = process_marker_path(&root);
        let Some(marker) = read_marker(&marker_path) else { continue; };
        let pid = Pid::from_u32(marker.pid);
        system.refresh_process(pid);
        let Some(process) = system.process(pid) else { continue; };
        if marker.process_start_time != 0 && process.start_time() != marker.process_start_time { continue; }
        if !looks_like_workspace_paper(process, &root) { continue; }
        return Err(format!("{} is still running in the background. Open that server and stop it before opening, creating, or adopting another server.", server.name));
    }
    Ok(())
}

/// Best-effort guard for a plain Paper server before adoption. A server started outside
/// LazyBuilder has no LazyBuilder process marker, so inspect live Java process working
/// directories and command lines before any files are moved.
pub fn ensure_root_not_running(root: &Path) -> Result<(), String> {
    let canonical = root.canonicalize().map_err(|error| format!("Could not resolve server location for process safety check: {error}"))?;
    let canonical_text = canonical.to_string_lossy().to_ascii_lowercase().replace('/', "\\");
    let mut system = System::new_all();
    system.refresh_all();

    for process in system.processes().values() {
        let name = process.name().to_ascii_lowercase();
        if !name.contains("java") { continue; }
        let cwd_match = process.cwd().is_some_and(|cwd| {
            cwd.to_string_lossy().to_ascii_lowercase().replace('/', "\\") == canonical_text
        });
        let command = process.cmd().join(" ").to_ascii_lowercase().replace('/', "\\");
        let command_match = command.contains(&canonical_text);
        if cwd_match || command_match {
            return Err("A Java/Minecraft server process is currently using the selected server folder. Stop that server before adoption so LazyBuilder does not move live runtime files.".into());
        }
    }
    Ok(())
}

fn process_marker_path(workspace: &Path) -> PathBuf {
    workspace.join("tools").join("lazybuilder").join("cache").join("server-process.json")
}
fn read_marker(path: &Path) -> Option<ProcessMarker> { let text = fs::read_to_string(path).ok()?; serde_json::from_str(&text).ok() }
fn looks_like_workspace_paper(process: &sysinfo::Process, workspace: &Path) -> bool {
    let name = process.name().to_ascii_lowercase(); if !name.contains("java") { return false; }
    let command = process.cmd().join(" ").to_ascii_lowercase();
    let worlds = workspace.join("world-system").join("worlds").display().to_string().to_ascii_lowercase();
    command.contains("-jar") && command.contains("--universe") && command.contains("nogui") && command.contains(&worlds)
}

#[cfg(test)]
mod tests {
    use super::process_marker_path;
    use std::path::Path;
    #[test]
    fn marker_path_stays_workspace_local() {
        assert_eq!(process_marker_path(Path::new("C:/BuildServer")), Path::new("C:/BuildServer/tools/lazybuilder/cache/server-process.json"));
    }
}
