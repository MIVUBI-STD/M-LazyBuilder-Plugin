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

/// Read-only cross-workspace safety guard.
///
/// ServerManager remains the lifecycle/process authority. This helper only prevents the
/// Server Library from activating or creating another workspace while a Paper process
/// belonging to a different registered LazyBuilder workspace is still alive after an
/// abnormal launcher exit.
pub fn ensure_no_running_paper_outside_active() -> Result<(), String> {
    let active_id = workspace_registry::current()?.map(|entry| entry.id);
    let servers = workspace_registry::list()?;
    let mut system = System::new_all();

    for server in servers {
        if active_id.as_deref() == Some(server.id.as_str()) {
            continue;
        }

        let root = PathBuf::from(&server.path);
        let marker_path = process_marker_path(&root);
        let Some(marker) = read_marker(&marker_path) else {
            continue;
        };

        let pid = Pid::from_u32(marker.pid);
        system.refresh_process(pid);
        let Some(process) = system.process(pid) else {
            continue;
        };

        if marker.process_start_time != 0 && process.start_time() != marker.process_start_time {
            continue;
        }

        if !looks_like_workspace_paper(process, &root) {
            continue;
        }

        return Err(format!(
            "{} is still running in the background. Reopen that server and stop it before opening, creating, or adopting another server.",
            server.name
        ));
    }

    Ok(())
}

fn process_marker_path(workspace: &Path) -> PathBuf {
    workspace
        .join("tools")
        .join("lazybuilder")
        .join("cache")
        .join("server-process.json")
}

fn read_marker(path: &Path) -> Option<ProcessMarker> {
    let text = fs::read_to_string(path).ok()?;
    serde_json::from_str(&text).ok()
}

fn looks_like_workspace_paper(process: &sysinfo::Process, workspace: &Path) -> bool {
    let name = process.name().to_ascii_lowercase();
    if !name.contains("java") {
        return false;
    }

    let command = process.cmd().join(" ").to_ascii_lowercase();
    let worlds = workspace
        .join("world-system")
        .join("worlds")
        .display()
        .to_string()
        .to_ascii_lowercase();

    command.contains("-jar")
        && command.contains("--universe")
        && command.contains("nogui")
        && command.contains(&worlds)
}

#[cfg(test)]
mod tests {
    use super::process_marker_path;
    use std::path::Path;

    #[test]
    fn marker_path_stays_workspace_local() {
        assert_eq!(
            process_marker_path(Path::new("C:/BuildServer")),
            Path::new("C:/BuildServer/tools/lazybuilder/cache/server-process.json")
        );
    }
}
