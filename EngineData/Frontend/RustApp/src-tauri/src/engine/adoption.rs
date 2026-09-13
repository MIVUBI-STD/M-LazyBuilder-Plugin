use crate::engine::workspace_registry::{self, WorkspaceEntry};
use serde::Serialize;
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdoptionPlan {
    pub root: String,
    pub name: String,
    pub paper_jar: String,
    pub worlds: Vec<String>,
    pub server_entries: Vec<String>,
    pub preserved_entries: Vec<String>,
    pub warnings: Vec<String>,
}

pub fn analyze(root: &Path) -> Result<AdoptionPlan, String> {
    let root = root
        .canonicalize()
        .map_err(|error| format!("Could not resolve existing server: {error}"))?;
    if !root.is_dir() {
        return Err("Selected existing server is not a directory".into());
    }
    if root.join("tools").join("lazybuilder").join("config").join("workspace.json").is_file() {
        return Err("This server is already a LazyBuilder workspace. Use Open Existing LazyBuilder Server instead.".into());
    }
    if root.join("server").is_dir() && root.join("world-system").is_dir() {
        return Err("This folder already looks like a LazyBuilder workspace. Open it instead of adopting it again.".into());
    }

    let paper = detect_paper_jar(&root)?;
    let worlds = detect_worlds(&root)?;
    let server_entries = detect_server_entries(&root, &paper, &worlds)?;
    if !root.join("server.properties").is_file() && worlds.is_empty() {
        return Err("The selected folder does not look like an initialized Paper server.".into());
    }

    let recognized = server_entries.iter().chain(worlds.iter()).cloned().collect::<std::collections::HashSet<_>>();
    let preserved_entries = fs::read_dir(&root)
        .map_err(|error| error.to_string())?
        .filter_map(Result::ok)
        .filter_map(|entry| entry.file_name().to_str().map(str::to_string))
        .filter(|name| !recognized.contains(name))
        .collect::<Vec<_>>();

    let name = root.file_name().and_then(|value| value.to_str()).unwrap_or("Imported Server").to_string();
    let mut warnings = vec![
        "Stop the existing Minecraft server before adoption. LazyBuilder will not terminate an independently launched Paper process.".into(),
        "Only recognized Paper runtime entries and detected world folders will be moved. Unknown files are preserved in place.".into(),
    ];
    if !preserved_entries.is_empty() {
        warnings.push(format!("{} unrecognized root item(s) will remain untouched.", preserved_entries.len()));
    }

    Ok(AdoptionPlan {
        root: root.display().to_string(),
        name,
        paper_jar: paper,
        worlds,
        server_entries,
        preserved_entries,
        warnings,
    })
}

pub fn execute(root: &Path, requested_name: Option<&str>) -> Result<WorkspaceEntry, String> {
    let plan = analyze(root)?;
    let root = PathBuf::from(&plan.root);
    let server = root.join("server");
    let worlds_root = root.join("world-system").join("worlds");
    fs::create_dir_all(&server).map_err(|error| error.to_string())?;
    fs::create_dir_all(&worlds_root).map_err(|error| error.to_string())?;

    // Preflight every destination before moving anything, so an existing canonical
    // destination can never be overwritten by adoption.
    for world in &plan.worlds {
        ensure_destination_free(&worlds_root.join(world))?;
    }
    for entry in &plan.server_entries {
        let destination = if entry == &plan.paper_jar { server.join("paper.jar") } else { server.join(entry) };
        ensure_destination_free(&destination)?;
    }

    let mut moved: Vec<(PathBuf, PathBuf)> = Vec::new();
    let migration = (|| -> Result<(), String> {
        for world in &plan.worlds {
            move_recorded(&root.join(world), &worlds_root.join(world), &mut moved)?;
        }
        for entry in &plan.server_entries {
            let destination = if entry == &plan.paper_jar { server.join("paper.jar") } else { server.join(entry) };
            move_recorded(&root.join(entry), &destination, &mut moved)?;
        }
        Ok(())
    })();

    if let Err(error) = migration {
        rollback(&mut moved);
        return Err(format!("Server adoption failed and moved entries were rolled back: {error}"));
    }

    // workspace_registry::open is the single authority that creates canonical
    // LazyBuilder metadata and registers/activates the workspace.
    let entry = match workspace_registry::open(&root) {
        Ok(entry) => entry,
        Err(error) => {
            rollback(&mut moved);
            return Err(format!("Server files were restored because LazyBuilder registration failed: {error}"));
        }
    };

    // A user-provided display name is intentionally registry/UI metadata only for v1;
    // filesystem names are never changed during adoption.
    let _ = requested_name;
    Ok(entry)
}

fn detect_paper_jar(root: &Path) -> Result<String, String> {
    let mut paper_candidates = Vec::new();
    let mut jar_candidates = Vec::new();
    for entry in fs::read_dir(root).map_err(|error| error.to_string())? {
        let entry = entry.map_err(|error| error.to_string())?;
        if !entry.file_type().map_err(|error| error.to_string())?.is_file() { continue; }
        let Some(name) = entry.file_name().to_str().map(str::to_string) else { continue; };
        if !name.to_ascii_lowercase().ends_with(".jar") { continue; }
        jar_candidates.push(name.clone());
        if name.to_ascii_lowercase().contains("paper") { paper_candidates.push(name); }
    }
    if paper_candidates.len() == 1 { return Ok(paper_candidates.remove(0)); }
    if paper_candidates.len() > 1 {
        return Err("Multiple Paper JAR candidates were found. Keep only the active Paper server JAR in the server root before adoption.".into());
    }
    if jar_candidates.len() == 1 { return Ok(jar_candidates.remove(0)); }
    Err("LazyBuilder could not identify a single Paper server JAR in the selected folder.".into())
}

fn detect_worlds(root: &Path) -> Result<Vec<String>, String> {
    let mut worlds = Vec::new();
    for entry in fs::read_dir(root).map_err(|error| error.to_string())? {
        let entry = entry.map_err(|error| error.to_string())?;
        if !entry.file_type().map_err(|error| error.to_string())?.is_dir() { continue; }
        let path = entry.path();
        if path.join("level.dat").is_file() {
            if let Some(name) = entry.file_name().to_str() { worlds.push(name.to_string()); }
        }
    }
    worlds.sort();
    Ok(worlds)
}

fn detect_server_entries(root: &Path, paper: &str, worlds: &[String]) -> Result<Vec<String>, String> {
    const KNOWN: &[&str] = &[
        "plugins", "logs", "config", "cache", "libraries", "versions",
        "server.properties", "eula.txt", "bukkit.yml", "spigot.yml", "commands.yml",
        "permissions.yml", "help.yml", "whitelist.json", "ops.json", "banned-ips.json",
        "banned-players.json", "usercache.json", "paper-global.yml", "paper-world-defaults.yml",
    ];
    let mut entries = vec![paper.to_string()];
    for name in KNOWN {
        let path = root.join(name);
        if path.exists() && !worlds.iter().any(|world| world == name) { entries.push((*name).to_string()); }
    }
    entries.sort();
    entries.dedup();
    Ok(entries)
}

fn ensure_destination_free(path: &Path) -> Result<(), String> {
    if path.exists() {
        return Err(format!("Adoption destination already exists and will not be overwritten: {}", path.display()));
    }
    Ok(())
}

fn move_recorded(source: &Path, destination: &Path, moved: &mut Vec<(PathBuf, PathBuf)>) -> Result<(), String> {
    if !source.exists() { return Ok(()); }
    if let Some(parent) = destination.parent() { fs::create_dir_all(parent).map_err(|error| error.to_string())?; }
    fs::rename(source, destination).map_err(|error| format!("Could not move {}: {error}", source.display()))?;
    moved.push((source.to_path_buf(), destination.to_path_buf()));
    Ok(())
}

fn rollback(moved: &mut Vec<(PathBuf, PathBuf)>) {
    for (source, destination) in moved.drain(..).rev() {
        if destination.exists() && !source.exists() {
            let _ = fs::rename(destination, source);
        }
    }
}
