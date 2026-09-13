use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::{OnceLock, RwLock};
use std::time::{SystemTime, UNIX_EPOCH};

const REGISTRY_SCHEMA_VERSION: u32 = 1;
const WORKSPACE_SCHEMA_VERSION: u32 = 1;
const MINECRAFT_VERSION: &str = "1.21.4";
const SERVER_PLATFORM: &str = "paper";

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
    #[serde(default)]
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
pub struct WorkspaceManifest {
    pub schema_version: u32,
    pub workspace_id: String,
    pub name: String,
    pub minecraft_version: String,
    pub server_platform: String,
    pub paper_build: Option<u32>,
    pub world_manager_version: Option<String>,
    pub utilities_manager_version: Option<String>,
    pub created_unix_seconds: u64,
    pub last_opened_unix_seconds: u64,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ProvisioningStatus {
    pub workspace_created: bool,
    pub java_ready: bool,
    pub paper_ready: bool,
    pub core_modules_ready: bool,
    pub config_ready: bool,
    pub eula_accepted: bool,
    pub ready: bool,
    pub next_step: String,
}

pub fn initialize() -> Result<(), String> {
    // Validate that the persisted server library can be read, but never restore an
    // active workspace across application sessions. Selection is session-only.
    let _ = load_registry()?;
    set_active_memory(None)
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
    let canonical = root.canonicalize().map_err(|error| error.to_string())?;
    let id = workspace_id(&canonical.display().to_string());
    write_manifest(&canonical, WorkspaceManifest {
        schema_version: WORKSPACE_SCHEMA_VERSION,
        workspace_id: id,
        name: safe_name.clone(),
        minecraft_version: MINECRAFT_VERSION.into(),
        server_platform: SERVER_PLATFORM.into(),
        paper_build: None,
        world_manager_version: None,
        utilities_manager_version: None,
        created_unix_seconds: now_unix_seconds(),
        last_opened_unix_seconds: now_unix_seconds(),
    })?;
    register_and_activate(&canonical, &safe_name)
}

pub fn open(root: &Path) -> Result<WorkspaceEntry, String> {
    open_with_display_name(root, None)
}

pub fn open_with_display_name(root: &Path, requested_name: Option<&str>) -> Result<WorkspaceEntry, String> {
    let root = root.canonicalize().map_err(|error| format!("Could not resolve server workspace: {error}"))?;
    if !root.is_dir() {
        return Err("Selected workspace is not a directory".into());
    }
    let requested_name = requested_name.map(validate_display_name).transpose()?;

    let name = match read_manifest(&root)? {
        Some(mut manifest) => {
            validate_manifest(&manifest)?;
            if let Some(name) = requested_name.as_ref() {
                manifest.name = name.clone();
            }
            manifest.last_opened_unix_seconds = now_unix_seconds();
            write_manifest(&root, manifest.clone())?;
            manifest.name
        }
        None if looks_like_legacy_lazybuilder_workspace(&root) => {
            let name = requested_name.unwrap_or_else(|| {
                root.file_name()
                    .and_then(|value| value.to_str())
                    .unwrap_or("LazyBuilder Server")
                    .to_string()
            });
            let id = workspace_id(&root.display().to_string());
            write_manifest(&root, WorkspaceManifest {
                schema_version: WORKSPACE_SCHEMA_VERSION,
                workspace_id: id,
                name: name.clone(),
                minecraft_version: MINECRAFT_VERSION.into(),
                server_platform: SERVER_PLATFORM.into(),
                paper_build: None,
                world_manager_version: None,
                utilities_manager_version: None,
                created_unix_seconds: now_unix_seconds(),
                last_opened_unix_seconds: now_unix_seconds(),
            })?;
            name
        }
        None => {
            return Err("This folder is not a LazyBuilder workspace. Plain Paper servers must be adopted through the migration flow instead of opened directly.".into());
        }
    };

    provision_layout(&root)?;
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
    save_registry(&registry)?;
    let canonical = path.canonicalize().map_err(|error| error.to_string())?;
    if let Some(mut manifest) = read_manifest(&canonical)? {
        manifest.last_opened_unix_seconds = now_unix_seconds();
        write_manifest(&canonical, manifest)?;
    }
    set_active_memory(Some(canonical))?;
    Ok(result)
}

pub fn deactivate() -> Result<(), String> {
    set_active_memory(None)
}

pub fn provisioning_status() -> Result<ProvisioningStatus, String> {
    let root = active_workspace()?;
    let workspace_created = manifest_path(&root).is_file();
    let config_ready = root.join("tools").join("lazybuilder").join("config").is_dir()
        && root.join("server").is_dir()
        && root.join("server").join("plugins").is_dir();
    let paper_ready = root.join("server").join("paper.jar").is_file();
    let plugins = root.join("server").join("plugins");
    let world_manager_ready = contains_plugin_prefix(&plugins, "World-Manager-")?;
    let utilities_manager_ready = contains_plugin_prefix(&plugins, "Utilities-Manager-")?;
    let core_modules_ready = world_manager_ready && utilities_manager_ready;
    let eula_accepted = read_eula(&root)?;

    let java_ready = true;
    let ready = workspace_created && java_ready && paper_ready && core_modules_ready && config_ready && eula_accepted;
    let next_step = if !workspace_created {
        "Create workspace metadata"
    } else if !paper_ready {
        "Provision Paper 1.21.4"
    } else if !core_modules_ready {
        "Install LazyBuilder core modules"
    } else if !config_ready {
        "Prepare server configuration"
    } else if !eula_accepted {
        "Accept the Minecraft EULA"
    } else {
        "Ready"
    };

    Ok(ProvisioningStatus {
        workspace_created,
        java_ready,
        paper_ready,
        core_modules_ready,
        config_ready,
        eula_accepted,
        ready,
        next_step: next_step.into(),
    })
}

pub fn accept_eula() -> Result<(), String> {
    let root = active_workspace()?;
    let eula = root.join("server").join("eula.txt");
    fs::write(eula, "# Accepted through LazyBuilder after explicit user confirmation\neula=true\n")
        .map_err(|error| error.to_string())
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
    registry.active_workspace_id = None;
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
        let backup = path.with_extension("json.previous");
        let _ = fs::remove_file(&backup);
        fs::rename(&path, &backup).map_err(|error| error.to_string())?;
        match fs::rename(&temporary, &path) {
            Ok(()) => {
                let _ = fs::remove_file(backup);
                Ok(())
            }
            Err(error) => {
                let _ = fs::rename(&backup, &path);
                Err(error.to_string())
            }
        }
    } else {
        fs::rename(temporary, path).map_err(|error| error.to_string())
    }
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

fn manifest_path(root: &Path) -> PathBuf {
    root.join("tools").join("lazybuilder").join("config").join("workspace.json")
}

fn write_manifest(root: &Path, manifest: WorkspaceManifest) -> Result<(), String> {
    let path = manifest_path(root);
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    let temporary = path.with_extension("json.tmp");
    let text = serde_json::to_string_pretty(&manifest).map_err(|error| error.to_string())?;
    fs::write(&temporary, text).map_err(|error| error.to_string())?;
    if path.exists() {
        let backup = path.with_extension("json.previous");
        let _ = fs::remove_file(&backup);
        fs::rename(&path, &backup).map_err(|error| error.to_string())?;
        match fs::rename(&temporary, &path) {
            Ok(()) => {
                let _ = fs::remove_file(backup);
                Ok(())
            }
            Err(error) => {
                let _ = fs::rename(&backup, &path);
                Err(error.to_string())
            }
        }
    } else {
        fs::rename(temporary, path).map_err(|error| error.to_string())
    }
}

fn read_manifest(root: &Path) -> Result<Option<WorkspaceManifest>, String> {
    let path = manifest_path(root);
    if !path.is_file() {
        return Ok(None);
    }
    let text = fs::read_to_string(path).map_err(|error| error.to_string())?;
    let manifest = serde_json::from_str(&text).map_err(|error| error.to_string())?;
    Ok(Some(manifest))
}

fn validate_manifest(manifest: &WorkspaceManifest) -> Result<(), String> {
    if manifest.schema_version != WORKSPACE_SCHEMA_VERSION {
        return Err("Workspace manifest schema is newer or unsupported".into());
    }
    if manifest.minecraft_version != MINECRAFT_VERSION {
        return Err(format!("This LazyBuilder build currently supports Minecraft {MINECRAFT_VERSION}; workspace targets {}.", manifest.minecraft_version));
    }
    if !manifest.server_platform.eq_ignore_ascii_case(SERVER_PLATFORM) {
        return Err("This LazyBuilder build currently supports Paper workspaces only".into());
    }
    Ok(())
}

fn looks_like_legacy_lazybuilder_workspace(root: &Path) -> bool {
    root.join("server").is_dir()
        && root.join("world-system").is_dir()
        && root.join("tools").join("lazybuilder").is_dir()
}

fn contains_plugin_prefix(directory: &Path, prefix: &str) -> Result<bool, String> {
    if !directory.is_dir() {
        return Ok(false);
    }
    for entry in fs::read_dir(directory).map_err(|error| error.to_string())? {
        let entry = entry.map_err(|error| error.to_string())?;
        if !entry.file_type().map_err(|error| error.to_string())?.is_file() {
            continue;
        }
        let name = entry.file_name();
        let name = name.to_string_lossy();
        if name.starts_with(prefix) && name.to_ascii_lowercase().ends_with(".jar") {
            return Ok(true);
        }
    }
    Ok(false)
}

fn read_eula(root: &Path) -> Result<bool, String> {
    let path = root.join("server").join("eula.txt");
    if !path.is_file() {
        return Ok(false);
    }
    let text = fs::read_to_string(path).map_err(|error| error.to_string())?;
    Ok(text.lines().any(|line| line.trim().eq_ignore_ascii_case("eula=true")))
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

fn validate_display_name(value: &str) -> Result<String, String> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        return Err("Workspace display name is required".into());
    }
    if trimmed.chars().any(char::is_control) {
        return Err("Workspace display name contains control characters".into());
    }
    if trimmed.chars().count() > 96 {
        return Err("Workspace display name is too long".into());
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
