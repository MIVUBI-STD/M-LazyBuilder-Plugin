use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::{OnceLock, RwLock};
use std::time::{SystemTime, UNIX_EPOCH};

const REGISTRY_SCHEMA_VERSION: u32 = 1;
const WORKSPACE_MANIFEST: &str = ".lazybuilder-workspace.json";

static ACTIVE_WORKSPACE: OnceLock<RwLock<Option<PathBuf>>> = OnceLock::new();

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WorkspaceEntry {
    pub id: String,
    pub name: String,
    pub path: String,
    pub last_opened_unix_seconds: u64,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct WorkspaceRegistryFile {
    schema_version: u32,
    active_workspace_id: Option<String>,
    servers: Vec<WorkspaceEntry>,
}

impl Default for WorkspaceRegistryFile {
    fn default() -> Self {
        Self {
            schema_version: REGISTRY_SCHEMA_VERSION,
            active_workspace_id: None,
            servers: Vec::new(),
        }
    }
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct WorkspaceManifest {
    schema_version: u32,
    name: String,
}

pub fn initialize() -> Result<(), String> {
    let registry = load_registry()?;
    if let Some(active_id) = registry.active_workspace_id.as_deref() {
        if let Some(entry) = registry.servers.iter().find(|entry| entry.id == active_id) {
            let path = PathBuf::from(&entry.path);
            if path.is_dir() {
                set_active_memory(Some(path.canonicalize().map_err(|error| error.to_string())?))?;
            }
        }
    }
    Ok(())
}

pub fn active_workspace() -> Result<PathBuf, String> {
    ACTIVE_WORKSPACE
        .get_or_init(|| RwLock::new(None))
        .read()
        .map_err(|_| "workspace state lock poisoned".to_string())?
        .clone()
        .ok_or_else(|| "No LazyBuilder server workspace is active. Create or open a server first.".to_string())
}

pub fn current() -> Result<Option<WorkspaceEntry>, String> {
    let registry = load_registry()?;
    let Some(active) = active_workspace_memory()? else { return Ok(None); };
    let active_text = active.display().to_string();
    Ok(registry.servers.into_iter().find(|entry| entry.path == active_text))
}

pub fn list() -> Result<Vec<WorkspaceEntry>, String> {
    let mut registry = load_registry()?;
    registry.servers.retain(|entry| Path::new(&entry.path).is_dir());
    registry.servers.sort_by(|left, right| right.last_opened_unix_seconds.cmp(&left.last_opened_unix_seconds));
    Ok(registry.servers)
}

pub fn create(parent: &Path, name: &str) -> Result<WorkspaceEntry, String> {
    let safe_name = validate_workspace_name(name)?;
    let parent = parent.canonicalize().map_err(|error| format!("Could not resolve server location: {error}"))?;
    if !parent.is_dir() {
        return Err("Selected server location is not a directory".into());
    }

    let root = parent.join(&safe_name);
    if root.exists() {
        let mut entries = fs::read_dir(&root).map_err(|error| error.to_string())?;
        if entries.next().is_some() {
            return Err("A non-empty folder with that server name already exists".into());
        }
    }
    fs::create_dir_all(&root).map_err(|error| error.to_string())?;
    provision_layout(&root)?;
    write_manifest(&root, &safe_name)?;
    register_and_activate(&root, &safe_name)
}

pub fn open(root: &Path) -> Result<WorkspaceEntry, String> {
    let root = root.canonicalize().map_err(|error| format!("Could not resolve server workspace: {error}"))?;
    if !root.is_dir() {
        return Err("Selected workspace is not a directory".into());
    }

    let manifest = read_manifest(&root)?;
    let name = if let Some(manifest) = manifest {
        manifest.name
    } else if root.join("server").is_dir() || root.join("world-system").is_dir() {
        root.file_name()
            .and_then(|value| value.to_str())
            .unwrap_or("LazyBuilder Server")
            .to_string()
    } else {
        return Err("This folder is not a LazyBuilder server workspace. Use Create New Server for a new workspace.".into());
    };

    provision_layout(&root)?;
    if !root.join(WORKSPACE_MANIFEST).is_file() {
        write_manifest(&root, &name)?;
    }
    register_and_activate(&root, &name)
}

pub fn activate(id: &str) -> Result<WorkspaceEntry, String> {
    let mut registry = load_registry()?;
    let entry = registry.servers.iter_mut().find(|entry| entry.id == id)
        .ok_or_else(|| "Saved server workspace was not found".to_string())?;
    let path = PathBuf::from(&entry.path);
    if !path.is_dir() {
        return Err("Saved server workspace no longer exists".into());
    }
    entry.last_opened_unix_seconds = now_unix_seconds();
    let result = entry.clone();
    registry.active_workspace_id = Some(result.id.clone());
    save_registry(&registry)?;
    set_active_memory(Some(path.canonicalize().map_err(|error| error.to_string())?))?;
    Ok(result)
}

pub fn deactivate() -> Result<(), String> {
    let mut registry = load_registry()?;
    registry.active_workspace_id = None;
    save_registry(&registry)?;
    set_active_memory(None)
}

fn register_and_activate(root: &Path, name: &str) -> Result<WorkspaceEntry, String> {
    let canonical = root.canonicalize().map_err(|error| error.to_string())?;
    let canonical_text = canonical.display().to_string();
    let id = workspace_id(&canonical_text);
    let now = now_unix_seconds();
    let mut registry = load_registry()?;
    if let Some(existing) = registry.servers.iter_mut().find(|entry| entry.id == id) {
        existing.name = name.to_string();
        existing.path = canonical_text.clone();
        existing.last_opened_unix_seconds = now;
    } else {
        registry.servers.push(WorkspaceEntry {
            id: id.clone(),
            name: name.to_string(),
            path: canonical_text.clone(),
            last_opened_unix_seconds: now,
        });
    }
    registry.active_workspace_id = Some(id.clone());
    save_registry(&registry)?;
    set_active_memory(Some(canonical))?;
    Ok(registry.servers.into_iter().find(|entry| entry.id == id).expect("registered workspace missing"))
}

fn active_workspace_memory() -> Result<Option<PathBuf>, String> {
    ACTIVE_WORKSPACE
        .get_or_init(|| RwLock::new(None))
        .read()
        .map_err(|_| "workspace state lock poisoned".to_string())
        .map(|guard| guard.clone())
}

fn set_active_memory(value: Option<PathBuf>) -> Result<(), String> {
    *ACTIVE_WORKSPACE
        .get_or_init(|| RwLock::new(None))
        .write()
        .map_err(|_| "workspace state lock poisoned".to_string())? = value;
    Ok(())
}

fn registry_path() -> Result<PathBuf, String> {
    let base = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())?;
    Ok(base.join("LazyBuilder").join("workspaces.json"))
}

fn load_registry() -> Result<WorkspaceRegistryFile, String> {
    let path = registry_path()?;
    if !path.is_file() {
        return Ok(WorkspaceRegistryFile::default());
    }
    let text = fs::read_to_string(&path).map_err(|error| error.to_string())?;
    let mut registry: WorkspaceRegistryFile = serde_json::from_str(&text).map_err(|error| error.to_string())?;
    if registry.schema_version != REGISTRY_SCHEMA_VERSION {
        return Err("Workspace registry schema is newer or unsupported".into());
    }
    registry.servers.retain(|entry| !entry.path.trim().is_empty());
    Ok(registry)
}

fn save_registry(registry: &WorkspaceRegistryFile) -> Result<(), String> {
    let path = registry_path()?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    let temporary = path.with_extension("json.tmp");
    let text = serde_json::to_string_pretty(registry).map_err(|error| error.to_string())?;
    fs::write(&temporary, text).map_err(|error| error.to_string())?;
    if path.exists() {
        let _ = fs::remove_file(&path);
    }
    fs::rename(temporary, path).map_err(|error| error.to_string())
}

fn provision_layout(root: &Path) -> Result<(), String> {
    let directories = [
        root.join("server"),
        root.join("server").join("plugins"),
        root.join("world-system").join("worlds"),
        root.join("world-system").join("imports"),
        root.join("world-system").join("exports"),
        root.join("world-system").join("backups"),
        root.join("world-system").join("work"),
        root.join("tools").join("lazybuilder").join("config"),
        root.join("tools").join("lazybuilder").join("cache"),
        root.join("tools").join("lazybuilder").join("logs"),
        root.join("tools").join("lazybuilder").join("disabled-plugins"),
        root.join("tools").join("lazybuilder").join("plugin-backups"),
    ];
    for directory in directories {
        fs::create_dir_all(directory).map_err(|error| error.to_string())?;
    }
    Ok(())
}

fn write_manifest(root: &Path, name: &str) -> Result<(), String> {
    let manifest = WorkspaceManifest { schema_version: 1, name: name.to_string() };
    let text = serde_json::to_string_pretty(&manifest).map_err(|error| error.to_string())?;
    fs::write(root.join(WORKSPACE_MANIFEST), text).map_err(|error| error.to_string())
}

fn read_manifest(root: &Path) -> Result<Option<WorkspaceManifest>, String> {
    let path = root.join(WORKSPACE_MANIFEST);
    if !path.is_file() {
        return Ok(None);
    }
    let text = fs::read_to_string(path).map_err(|error| error.to_string())?;
    let manifest = serde_json::from_str(&text).map_err(|error| error.to_string())?;
    Ok(Some(manifest))
}

fn validate_workspace_name(value: &str) -> Result<String, String> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        return Err("Server name is required".into());
    }
    if trimmed.chars().any(|character| character.is_control() || "<>:\"/\\|?*".contains(character)) {
        return Err("Server name contains characters that are not valid in a Windows folder name".into());
    }
    if trimmed.ends_with('.') || trimmed.ends_with(' ') {
        return Err("Server name may not end with a dot or space".into());
    }
    Ok(trimmed.to_string())
}

fn workspace_id(path: &str) -> String {
    use sha2::{Digest, Sha256};
    let mut digest = Sha256::new();
    digest.update(path.to_lowercase().as_bytes());
    format!("{:x}", digest.finalize())
}

fn now_unix_seconds() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|duration| duration.as_secs()).unwrap_or(0)
}
