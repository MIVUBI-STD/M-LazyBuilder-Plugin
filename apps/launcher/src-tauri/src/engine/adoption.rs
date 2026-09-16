use crate::engine::workspace_registry::{self, WorkspaceEntry};
use serde::{Deserialize, Serialize};
use std::collections::HashSet;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
#[cfg(windows)]
use std::os::windows::fs::MetadataExt;

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdoptionPlan {
    pub root: String,
    pub name: String,
    pub paper_jar: String,
    pub worlds: Vec<String>,
    pub server_entries: Vec<String>,
    pub legacy_plugins_to_disable: Vec<String>,
    pub preserved_entries: Vec<String>,
    pub warnings: Vec<String>,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdoptionRecoveryReport {
    pub recovered: u32,
    pub completed: u32,
    pub rolled_back: u32,
    pub issues: Vec<String>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct AdoptionMove {
    source: String,
    destination: String,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PendingAdoption {
    root: String,
    requested_name: Option<String>,
    created_layout: Vec<String>,
    moves: Vec<AdoptionMove>,
}

pub fn analyze(root: &Path) -> Result<AdoptionPlan, String> {
    reject_unsafe_root(root)?;
    let root = root.canonicalize().map_err(|error| format!("Could not resolve existing server: {error}"))?;
    if !root.is_dir() { return Err("Selected existing server is not a directory".into()); }
    if root.join("tools").join("lazybuilder").join("config").join("workspace.json").is_file() {
        return Err("This server is already a LazyBuilder workspace. Use Open Existing LazyBuilder Server instead.".into());
    }
    if root.join("server").is_dir() && root.join("world-system").is_dir() {
        return Err("This folder already looks like a LazyBuilder workspace. Open it instead of adopting it again.".into());
    }

    let paper = detect_paper_jar(&root)?;
    let worlds = detect_worlds(&root)?;
    let server_entries = detect_server_entries(&root, &paper, &worlds)?;
    let legacy_plugins_to_disable = detect_replaced_plugins(&root.join("plugins"))?;
    if !root.join("server.properties").is_file() && worlds.is_empty() {
        return Err("The selected folder does not look like an initialized Paper server.".into());
    }

    let recognized = server_entries.iter().chain(worlds.iter()).cloned().collect::<HashSet<_>>();
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
    if !legacy_plugins_to_disable.is_empty() {
        warnings.push(format!("{} legacy plugin(s) replaced by LazyBuilder will be moved to tools/lazybuilder/disabled-plugins instead of deleted.", legacy_plugins_to_disable.len()));
    }
    if !preserved_entries.is_empty() { warnings.push(format!("{} unrecognized root item(s) will remain untouched.", preserved_entries.len())); }

    Ok(AdoptionPlan { root: root.display().to_string(), name, paper_jar: paper, worlds, server_entries, legacy_plugins_to_disable, preserved_entries, warnings })
}

pub fn execute(root: &Path, requested_name: Option<&str>) -> Result<WorkspaceEntry, String> {
    let plan = analyze(root)?;
    let root = PathBuf::from(&plan.root);
    let created_layout = workspace_paths(&root).into_iter().filter(|path| !path.exists()).collect::<Vec<_>>();
    let server = root.join("server");
    let worlds_root = root.join("world-system").join("worlds");
    let disabled_plugins = root.join("tools").join("lazybuilder").join("disabled-plugins");

    for world in &plan.worlds { ensure_destination_free(&worlds_root.join(world))?; }
    for entry in &plan.server_entries {
        let destination = if entry == &plan.paper_jar { server.join("paper.jar") } else { server.join(entry) };
        ensure_destination_free(&destination)?;
    }
    for plugin in &plan.legacy_plugins_to_disable { ensure_destination_free(&disabled_plugins.join(plugin))?; }

    let intent = PendingAdoption {
        root: root.display().to_string(),
        requested_name: requested_name.map(str::to_string),
        created_layout: created_layout.iter().map(|path| path.display().to_string()).collect(),
        moves: planned_moves(&root, &plan),
    };
    validate_intent_paths(&intent)?;
    reject_adoption_source_trees(&intent)?;
    add_pending_adoption(intent.clone())?;

    if let Err(error) = prepare_layout(&server, &worlds_root, &disabled_plugins) {
        return fail_and_rollback(&intent, format!("Could not prepare LazyBuilder adoption layout: {error}"));
    }

    for movement in &intent.moves {
        let source = PathBuf::from(&movement.source);
        let destination = PathBuf::from(&movement.destination);
        if let Err(error) = move_one(&source, &destination) {
            return fail_and_rollback(&intent, format!("Server adoption move failed: {error}"));
        }
    }

    match workspace_registry::open_with_display_name(&root, requested_name) {
        Ok(entry) => {
            let _ = clear_pending_adoption(&intent.root);
            Ok(entry)
        }
        Err(error) => fail_and_rollback(&intent, format!("LazyBuilder registration failed after moving server files: {error}")),
    }
}

pub fn recover_pending_adoptions() -> Result<AdoptionRecoveryReport, String> {
    let pending = load_pending_adoptions()?;
    if pending.is_empty() {
        return Ok(AdoptionRecoveryReport { recovered: 0, completed: 0, rolled_back: 0, issues: Vec::new() });
    }

    let mut remaining = Vec::new();
    let mut report = AdoptionRecoveryReport { recovered: 0, completed: 0, rolled_back: 0, issues: Vec::new() };

    for intent in pending {
        if let Err(error) = validate_intent_paths(&intent) {
            report.issues.push(format!("Adoption recovery has unsafe recorded paths for {}: {error}. Nothing was changed.", intent.root));
            remaining.push(intent);
            continue;
        }
        let root = PathBuf::from(&intent.root);
        if let Err(error) = reject_existing_reparse_points(&root, &intent) {
            report.issues.push(format!("Adoption recovery for {} was blocked by filesystem safety validation: {error}", intent.root));
            remaining.push(intent);
            continue;
        }

        let manifest = root.join("tools").join("lazybuilder").join("config").join("workspace.json");
        if manifest.is_file() {
            match workspace_registry::open_with_display_name(&root, intent.requested_name.as_deref()) {
                Ok(_) => {
                    let _ = workspace_registry::deactivate();
                    report.recovered = report.recovered.saturating_add(1);
                    report.completed = report.completed.saturating_add(1);
                }
                Err(error) => {
                    report.issues.push(format!("Adoption recovery found committed workspace metadata at {} but could not finish registration: {error}", intent.root));
                    remaining.push(intent);
                }
            }
            continue;
        }

        match rollback_intent(&intent) {
            Ok(()) => {
                cleanup_failed_adoption(&root, &intent.created_layout.iter().map(PathBuf::from).collect::<Vec<_>>());
                report.recovered = report.recovered.saturating_add(1);
                report.rolled_back = report.rolled_back.saturating_add(1);
            }
            Err(error) => {
                report.issues.push(format!("Adoption recovery for {} is incomplete: {error}. Existing files were preserved.", intent.root));
                remaining.push(intent);
            }
        }
    }

    save_pending_adoptions(&remaining)?;
    Ok(report)
}

fn planned_moves(root: &Path, plan: &AdoptionPlan) -> Vec<AdoptionMove> {
    let server = root.join("server");
    let worlds_root = root.join("world-system").join("worlds");
    let disabled_plugins = root.join("tools").join("lazybuilder").join("disabled-plugins");
    let mut result = Vec::new();
    for world in &plan.worlds {
        result.push(AdoptionMove { source: root.join(world).display().to_string(), destination: worlds_root.join(world).display().to_string() });
    }
    for entry in &plan.server_entries {
        let destination = if entry == &plan.paper_jar { server.join("paper.jar") } else { server.join(entry) };
        result.push(AdoptionMove { source: root.join(entry).display().to_string(), destination: destination.display().to_string() });
    }
    for plugin in &plan.legacy_plugins_to_disable {
        result.push(AdoptionMove { source: server.join("plugins").join(plugin).display().to_string(), destination: disabled_plugins.join(plugin).display().to_string() });
    }
    result
}

fn prepare_layout(server: &Path, worlds_root: &Path, disabled_plugins: &Path) -> Result<(), String> {
    fs::create_dir_all(server).map_err(|error| error.to_string())?;
    fs::create_dir_all(worlds_root).map_err(|error| error.to_string())?;
    fs::create_dir_all(disabled_plugins).map_err(|error| error.to_string())?;
    Ok(())
}

fn fail_and_rollback(intent: &PendingAdoption, message: String) -> Result<WorkspaceEntry, String> {
    let root = PathBuf::from(&intent.root);
    match rollback_intent(intent) {
        Ok(()) => {
            cleanup_failed_adoption(&root, &intent.created_layout.iter().map(PathBuf::from).collect::<Vec<_>>());
            match clear_pending_adoption(&intent.root) {
                Ok(()) => Err(format!("{message}. Moved entries were restored.")),
                Err(clear_error) => Err(format!("ADOPTION_RECOVERY_REQUIRED: {message}. Files were rolled back, but recovery intent cleanup failed: {clear_error}")),
            }
        }
        Err(rollback_error) => Err(format!("ADOPTION_RECOVERY_REQUIRED: {message}. Automatic rollback was incomplete: {rollback_error}")),
    }
}

fn rollback_intent(intent: &PendingAdoption) -> Result<(), String> {
    let mut errors = Vec::new();
    for movement in intent.moves.iter().rev() {
        let source = PathBuf::from(&movement.source);
        let destination = PathBuf::from(&movement.destination);
        match (source.exists(), destination.exists()) {
            (true, false) => {}
            (false, true) => {
                if let Some(parent) = source.parent() {
                    if let Err(error) = fs::create_dir_all(parent) {
                        errors.push(format!("Could not recreate {}: {error}", parent.display()));
                        continue;
                    }
                }
                if let Err(error) = fs::rename(&destination, &source) {
                    errors.push(format!("Could not restore {} from {}: {error}", source.display(), destination.display()));
                }
            }
            (true, true) => errors.push(format!("Both source and destination exist for {}", source.display())),
            (false, false) if nested_source_not_materialized(intent, movement) => {}
            (false, false) => errors.push(format!("Both source and destination are missing for {}", source.display())),
        }
    }
    if errors.is_empty() { Ok(()) } else { Err(errors.join("; ")) }
}

fn nested_source_not_materialized(intent: &PendingAdoption, movement: &AdoptionMove) -> bool {
    let source = PathBuf::from(&movement.source);
    intent.moves.iter().any(|parent_move| {
        if std::ptr::eq(parent_move, movement) { return false; }
        let parent_source = PathBuf::from(&parent_move.source);
        let parent_destination = PathBuf::from(&parent_move.destination);
        source.starts_with(&parent_destination)
            && parent_source.exists()
            && !parent_destination.exists()
    })
}

fn validate_intent_paths(intent: &PendingAdoption) -> Result<(), String> {
    let root = PathBuf::from(&intent.root);
    if !root.is_absolute() { return Err("adoption root is not absolute".into()); }
    for movement in &intent.moves {
        let source = PathBuf::from(&movement.source);
        let destination = PathBuf::from(&movement.destination);
        if !source.starts_with(&root) || !destination.starts_with(&root) || source == root || destination == root {
            return Err(format!("move escapes adoption root: {} -> {}", source.display(), destination.display()));
        }
    }
    for directory in &intent.created_layout {
        let path = PathBuf::from(directory);
        if !path.starts_with(&root) || path == root { return Err(format!("created layout path escapes adoption root: {}", path.display())); }
    }
    Ok(())
}

fn reject_unsafe_root(root: &Path) -> Result<(), String> {
    let metadata = fs::symlink_metadata(root).map_err(|error| format!("Could not inspect existing server: {error}"))?;
    if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
        return Err("LazyBuilder refuses to adopt a symbolic link or Windows reparse-point server root".into());
    }
    Ok(())
}

fn reject_adoption_source_trees(intent: &PendingAdoption) -> Result<(), String> {
    let mut checked = Vec::<PathBuf>::new();
    for movement in &intent.moves {
        let source = PathBuf::from(&movement.source);
        if !source.exists() { continue; }
        // A parent source tree already covers descendants (for example the root
        // plugins directory covers legacy plugin JARs that will later move again).
        if checked.iter().any(|parent| source.starts_with(parent)) { continue; }
        reject_tree_links(&source)?;
        checked.push(source);
    }
    Ok(())
}

fn reject_tree_links(path: &Path) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path)
        .map_err(|error| format!("Could not inspect adoption source {}: {error}", path.display()))?;
    if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
        return Err(format!("Adoption source contains a symbolic link or Windows reparse point: {}", path.display()));
    }
    if metadata.file_type().is_dir() {
        for item in fs::read_dir(path).map_err(|error| format!("Could not inspect adoption source directory {}: {error}", path.display()))? {
            let item = item.map_err(|error| error.to_string())?;
            reject_tree_links(&item.path())?;
        }
    }
    Ok(())
}

fn reject_existing_reparse_points(root: &Path, intent: &PendingAdoption) -> Result<(), String> {
    if root.exists() { reject_unsafe_root(root)?; }
    for movement in &intent.moves {
        for path in [PathBuf::from(&movement.source), PathBuf::from(&movement.destination)] {
            if !path.exists() { continue; }
            let metadata = fs::symlink_metadata(&path).map_err(|error| error.to_string())?;
            if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
                return Err(format!("unsafe link or reparse point: {}", path.display()));
            }
        }
    }
    Ok(())
}

fn pending_adoptions_path() -> Result<PathBuf, String> {
    let base = std::env::var_os("LOCALAPPDATA").map(PathBuf::from).or_else(|| std::env::var_os("APPDATA").map(PathBuf::from)).ok_or_else(|| "Windows application data directory is unavailable".to_string())?;
    Ok(base.join("LazyBuilder").join("pending-adoptions.json"))
}

fn load_pending_adoptions() -> Result<Vec<PendingAdoption>, String> {
    let path = pending_adoptions_path()?;
    if !path.is_file() { return Ok(Vec::new()); }
    let text = fs::read_to_string(path).map_err(|error| format!("Could not read pending server adoptions: {error}"))?;
    serde_json::from_str(&text).map_err(|error| format!("Could not parse pending server adoptions: {error}"))
}

fn save_pending_adoptions(entries: &[PendingAdoption]) -> Result<(), String> {
    let path = pending_adoptions_path()?;
    if entries.is_empty() {
        if path.exists() { fs::remove_file(path).map_err(|error| format!("Could not clear pending server adoptions: {error}"))?; }
        return Ok(());
    }
    if let Some(parent) = path.parent() { fs::create_dir_all(parent).map_err(|error| error.to_string())?; }
    let incoming = path.with_extension("json.incoming");
    let previous = path.with_extension("json.previous");
    let text = serde_json::to_string_pretty(entries).map_err(|error| error.to_string())?;
    {
        let mut file = OpenOptions::new().create(true).truncate(true).write(true).open(&incoming).map_err(|error| format!("Could not write pending server adoption intent: {error}"))?;
        file.write_all(text.as_bytes()).map_err(|error| error.to_string())?;
        file.sync_all().map_err(|error| format!("Could not flush pending server adoption intent: {error}"))?;
    }
    if path.exists() {
        let _ = fs::remove_file(&previous);
        fs::rename(&path, &previous).map_err(|error| error.to_string())?;
        match fs::rename(&incoming, &path) {
            Ok(()) => { let _ = fs::remove_file(previous); Ok(()) }
            Err(error) => { let _ = fs::rename(&previous, &path); Err(error.to_string()) }
        }
    } else {
        fs::rename(incoming, path).map_err(|error| error.to_string())
    }
}

fn add_pending_adoption(intent: PendingAdoption) -> Result<(), String> {
    let mut entries = load_pending_adoptions()?;
    if entries.iter().any(|item| paths_equal(&item.root, &intent.root)) { return Err("A previous adoption for this server still requires recovery".into()); }
    entries.push(intent;
    save_pending_adoptions(&entries)
}

fn clear_pending_adoption(root: &str) -> Result<(), String> {
    let mut entries = load_pending_adoptions()?;
    entries.retain(|item| !paths_equal(&item.root, root));
    save_pending_adoptions(&entries)
}

fn paths_equal(left: &str, right: &str) -> bool { if cfg!(windows) { left.eq_ignore_ascii_case(right) } else { left == right } }

fn workspace_paths(root: &Path) -> Vec<PathBuf> {
    vec![
        root.join("server"), root.join("server").join("plugins"), root.join("world-system"), root.join("world-system").join("worlds"),
        root.join("world-system").join("imports"), root.join("world-system").join("exports"), root.join("world-system").join("backups"), root.join("world-system").join("work"),
        root.join("tools"), root.join("tools").join("lazybuilder"), root.join("tools").join("lazybuilder").join("config"), root.join("tools").join("lazybuilder").join("cache"),
        root.join("tools").join("lazybuilder").join("logs"), root.join("tools").join("lazybuilder").join("disabled-plugins"), root.join("tools").join("lazybuilder").join("plugin-backups"),
    ]
}

fn cleanup_failed_adoption(root: &Path, created_layout: &[PathBuf]) {
    let manifest = root.join("tools").join("lazybuilder").join("config").join("workspace.json");
    let _ = fs::remove_file(manifest);
    let mut directories = created_layout.to_vec();
    directories.sort_by_key(|path| std::cmp::Reverse(path.components().count()));
    for directory in directories { let _ = fs::remove_dir(directory); }
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
    if paper_candidates.len() > 1 { return Err("Multiple Paper JAR candidates were found. Keep only the active Paper server JAR in the server root before adoption.".into()); }
    if jar_candidates.len() == 1 { return Ok(jar_candidates.remove(0)); }
    Err("LazyBuilder could not identify a single Paper server JAR in the selected folder.".into())
}

fn detect_worlds(root: &Path) -> Result<Vec<String>, String> {
    let mut worlds = Vec::new();
    for entry in fs::read_dir(root).map_err(|error| error.to_string())? {
        let entry = entry.map_err(|error| error.to_string())?;
        if !entry.file_type().map_err(|error| error.to_string())?.is_dir() { continue; }
        let path = entry.path();
        if path.join("level.dat").is_file() { if let Some(name) = entry.file_name().to_str() { worlds.push(name.to_string()); } }
    }
    worlds.sort();
    Ok(worlds)
}

fn detect_server_entries(root: &Path, paper: &str, worlds: &[String]) -> Result<Vec<String>, String> {
    const KNOWN: &[&str] = &["plugins", "logs", "config", "cache", "libraries", "versions", "server.properties", "eula.txt", "bukkit.yml", "spigot.yml", "commands.yml", "permissions.yml", "help.yml", "whitelist.json", "ops.json", "banned-ips.json", "banned-players.json", "usercache.json", "paper-global.yml", "paper-world-defaults.yml"];
    let mut entries = vec![paper.to_string()];
    for name in KNOWN {
        let path = root.join(name);
        if path.exists() && !worlds.iter().any(|world| world == name) { entries.push((*name).to_string()); }
    }
    entries.sort();
    entries.dedup();
    Ok(entries)
}

fn detect_replaced_plugins(plugins: &Path) -> Result<Vec<String>, String> {
    if !plugins.is_dir() { return Ok(Vec::new()); }
    let mut result = Vec::new();
    for entry in fs::read_dir(plugins).map_err(|error| error.to_string())? {
        let entry = entry.map_err(|error| error.to_string())?;
        if !entry.file_type().map_err(|error| error.to_string())?.is_file() { continue; }
        let Some(name) = entry.file_name().to_str().map(str::to_string) else { continue; };
        let normalized = name.to_ascii_lowercase().replace('_', "-").replace(' ', "-");
        let replaced = normalized.ends_with(".jar") && (normalized.contains("multiverse-core") || normalized.contains("voidworld") || normalized.contains("buildersutilities") || normalized.contains("builders-utilities"));
        if replaced { result.push(name); }
    }
    result.sort();
    Ok(result)
}

fn ensure_destination_free(path: &Path) -> Result<(), String> {
    if path.exists() { return Err(format!("Adoption destination already exists and will not be overwritten: {}", path.display())); }
    Ok(())
}

fn move_one(source: &Path, destination: &Path) -> Result<(), String> {
    if !source.exists() { return Ok(()); }
    if let Some(parent) = destination.parent() { fs::create_dir_all(parent).map_err(|error| error.to_string())?; }
    fs::rename(source, destination).map_err(|error| format!("Could not move {}: {error}", source.display()))
}

#[cfg(windows)]
fn is_reparse_point(metadata: &fs::Metadata) -> bool {
    const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x0400;
    metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0
}
#[cfg(not(windows))]
fn is_reparse_point(_metadata: &fs::Metadata) -> bool { false }

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn test_root(label: &str) -> PathBuf {
        let nonce = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos();
        std::env::temp_dir().join(format!("lazybuilder-{label}-{}-{nonce}", std::process::id()))
    }

    #[test]
    fn analyze_detects_paper_worlds_legacy_plugins_and_preserved_entries() {
        let root = test_root("adoption-analysis");
        fs::create_dir_all(root.join("plugins")).unwrap();
        fs::create_dir_all(root.join("world")).unwrap();
        fs::write(root.join("paper-1.21.4.jar"), b"paper").unwrap();
        fs::write(root.join("server.properties"), b"online-mode=true\n").unwrap();
        fs::write(root.join("world").join("level.dat"), b"level").unwrap();
        fs::write(root.join("plugins").join("Multiverse-Core-4.3.14.jar"), b"legacy").unwrap();
        fs::write(root.join("plugins").join("FastAsyncWorldEdit.jar"), b"keep").unwrap();
        fs::write(root.join("notes.txt"), b"preserve").unwrap();
        let plan = analyze(&root).unwrap();
        assert_eq!(plan.paper_jar, "paper-1.21.4.jar");
        assert_eq!(plan.worlds, vec!["world".to_string()]);
        assert!(plan.server_entries.contains(&"plugins".to_string()));
        assert!(plan.server_entries.contains(&"server.properties".to_string()));
        assert_eq!(plan.legacy_plugins_to_disable, vec!["Multiverse-Core-4.3.14.jar".to_string()]);
        assert!(plan.preserved_entries.contains(&"notes.txt".to_string()));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn analyze_rejects_multiple_paper_candidates() {
        let root = test_root("adoption-multiple-paper");
        fs::create_dir_all(&root).unwrap();
        fs::write(root.join("paper-a.jar"), b"a").unwrap();
        fs::write(root.join("paper-b.jar"), b"b").unwrap();
        fs::write(root.join("server.properties"), b"online-mode=true\n").unwrap();
        let error = analyze(&root).unwrap_err();
        assert!(error.contains("Multiple Paper JAR candidates"));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn adoption_intent_rejects_paths_outside_root() {
        let intent = PendingAdoption {
            root: "D:/Server".into(), requested_name: None, created_layout: vec!["D:/Server/tools".into()],
            moves: vec![AdoptionMove { source: "D:/Server/world".into(), destination: "D:/Other/world".into() }],
        };
        assert!(validate_intent_paths(&intent).is_err());
    }
}
