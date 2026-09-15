use crate::engine::{java_runtime, server_process_guard, workspace_registry};
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ServerHealthState { Ready, NeedsAttention, Unavailable, Busy }

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerHealthCheck {
    pub key: String,
    pub ready: bool,
    pub summary: String,
    pub details: String,
    pub repairable: bool,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerHealthSnapshot {
    pub workspace_id: String,
    pub workspace_name: String,
    pub state: ServerHealthState,
    pub ready: bool,
    pub running: bool,
    pub checks: Vec<ServerHealthCheck>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ManifestIdentity { workspace_id: String }

pub fn inspect(workspace_id: &str) -> Result<ServerHealthSnapshot, String> {
    let entry = workspace_registry::get(workspace_id)?;
    let root = PathBuf::from(&entry.path);
    let mut checks = Vec::new();

    if !root.is_dir() {
        checks.push(check("workspace-location", false, "Server location unavailable", &entry.path, false));
        return Ok(ServerHealthSnapshot { workspace_id: entry.id, workspace_name: entry.name, state: ServerHealthState::Unavailable, ready: false, running: false, checks });
    }
    checks.push(check("workspace-location", true, "Server location available", &entry.path, false));

    let manifest_path = root.join("tools").join("lazybuilder").join("config").join("workspace.json");
    let manifest_ready = fs::read_to_string(&manifest_path)
        .ok()
        .and_then(|text| serde_json::from_str::<ManifestIdentity>(&text).ok())
        .is_some_and(|manifest| manifest.workspace_id == entry.id);
    checks.push(check(
        "workspace-manifest",
        manifest_ready,
        if manifest_ready { "Workspace identity valid" } else { "Workspace identity needs attention" },
        &manifest_path.display().to_string(),
        false,
    ));

    let config_dir = root.join("tools").join("lazybuilder").join("config");
    let config_ready = config_dir.is_dir();
    checks.push(check("workspace-config", config_ready, if config_ready { "Workspace configuration ready" } else { "Workspace configuration missing" }, &config_dir.display().to_string(), true));

    let paper = root.join("server").join("paper.jar");
    let paper_ready = paper.is_file();
    checks.push(check("paper-runtime", paper_ready, if paper_ready { "Paper runtime ready" } else { "Paper runtime missing" }, &paper.display().to_string(), true));

    let java_ready = java_runtime::managed_java_ready();
    checks.push(check("java-runtime", java_ready, if java_ready { "Java runtime ready" } else { "Java runtime missing" }, "LazyBuilder managed Java 21 runtime", true));

    let plugins = root.join("server").join("plugins");
    let core_modules_ready = contains_plugin_prefix(&plugins, "World-Manager-")?
        && contains_plugin_prefix(&plugins, "Utilities-Manager-")?;
    checks.push(check("core-modules", core_modules_ready, if core_modules_ready { "Core modules ready" } else { "Core modules missing" }, &plugins.display().to_string(), true));

    let eula_path = root.join("server").join("eula.txt");
    let eula_ready = read_eula(&eula_path)?;
    checks.push(check("minecraft-eula", eula_ready, if eula_ready { "Minecraft EULA accepted" } else { "Minecraft EULA requires acceptance" }, &eula_path.display().to_string(), false));

    let running = server_process_guard::workspace_has_running_paper(&entry.id)?;
    checks.push(check("process-state", true, if running { "Server is running" } else { "Server is offline" }, if running { "A validated LazyBuilder Paper process owns this workspace." } else { "No validated LazyBuilder Paper process owns this workspace." }, false));

    let required_ready = manifest_ready && config_ready && paper_ready && java_ready && core_modules_ready && eula_ready;
    let state = if running { ServerHealthState::Busy } else if required_ready { ServerHealthState::Ready } else { ServerHealthState::NeedsAttention };
    Ok(ServerHealthSnapshot { workspace_id: entry.id, workspace_name: entry.name, state, ready: required_ready, running, checks })
}

fn contains_plugin_prefix(directory: &Path, prefix: &str) -> Result<bool, String> {
    if !directory.is_dir() { return Ok(false); }
    for entry in fs::read_dir(directory).map_err(|error| error.to_string())? {
        let entry = entry.map_err(|error| error.to_string())?;
        if !entry.file_type().map_err(|error| error.to_string())?.is_file() { continue; }
        let name = entry.file_name().to_string_lossy().to_string();
        if name.starts_with(prefix) && name.to_ascii_lowercase().ends_with(".jar") { return Ok(true); }
    }
    Ok(false)
}

fn read_eula(path: &Path) -> Result<bool, String> {
    if !path.is_file() { return Ok(false); }
    let text = fs::read_to_string(path).map_err(|error| error.to_string())?;
    Ok(text.lines().any(|line| line.trim().eq_ignore_ascii_case("eula=true")))
}

fn check(key: &str, ready: bool, summary: &str, details: &str, repairable: bool) -> ServerHealthCheck {
    ServerHealthCheck { key: key.into(), ready, summary: summary.into(), details: details.into(), repairable }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn health_checks_keep_repairability_explicit() {
        let item = check("paper-runtime", false, "missing", "paper.jar", true);
        assert!(item.repairable);
        assert!(!item.ready);
        let identity = check("workspace-manifest", false, "invalid", "workspace.json", false);
        assert!(!identity.repairable);
    }
}
