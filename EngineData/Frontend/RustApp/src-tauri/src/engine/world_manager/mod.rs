use crate::engine::paths;
use rand::RngCore;
use serde::{de::DeserializeOwned, Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::fs::{self, File};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::time::Duration;

pub const TOKEN_ENV: &str = "LAZYBUILDER_WORLD_CONTROL_TOKEN";
pub const PORT_ENV: &str = "LAZYBUILDER_WORLD_CONTROL_PORT";
pub const DEFAULT_PORT: u16 = 17842;

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WorldControlOptions { pub port: u16, pub token: String }
impl Default for WorldControlOptions {
    fn default() -> Self { Self { port: DEFAULT_PORT, token: String::new() } }
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ManagedWorldSummary {
    pub id: String, pub display_name: String, pub kind: String, pub lifecycle: String,
    pub runtime_state: String, pub auto_load: bool, pub default_game_mode: String,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateWorldRequest { pub folder_name: String, pub display_name: String, pub kind: String }

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CloneWorldRequest { pub world_id: String, pub destination_folder: String, pub display_name: String }

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExportWorldRequest { pub world_id: String, pub target_format: String, pub artifact_name: String }

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportWorldRequest { pub artifact_name: String, pub destination_folder: String, pub display_name: String }

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeleteWorldRequest {
    pub world_id: String,
    #[serde(rename(deserialize = "typedDisplayName", serialize = "typedFolderName"))]
    pub typed_display_name: String,
}

#[derive(Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct UpdateWorldSettingsRequest {
    pub auto_load: Option<bool>, pub default_game_mode: Option<String>, pub time_of_day_ticks: Option<u64>,
    pub weather: Option<String>, pub natural_spawning: Option<bool>, pub daylight_cycle: Option<bool>,
    pub weather_cycle: Option<bool>,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WorldSettingsSnapshot {
    pub id: String, pub display_name: String, pub auto_load: bool, pub default_game_mode: String,
    pub time_of_day_ticks: u64, pub weather: String, pub natural_spawning: bool,
    pub daylight_cycle: bool, pub weather_cycle: bool,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WorldTaskSnapshot {
    pub task_id: String,
    #[serde(rename(deserialize = "type", serialize = "taskType"))]
    pub task_type: String,
    pub world_id: Option<String>, pub state: String, pub progress_percent: u8, pub message: String,
    pub result: String, pub error: String, pub created_at: String, pub updated_at: String,
}

#[derive(Deserialize)] struct WorldListResponse { worlds: Vec<ManagedWorldSummary> }
#[derive(Deserialize)] struct WorldTaskListResponse { tasks: Vec<WorldTaskSnapshot> }
#[derive(Serialize)] #[serde(rename_all = "camelCase")] struct WorldTaskStartRequest<'a> { world_id: &'a str }
#[derive(Deserialize)] struct ErrorResponse { error: String, message: String }
#[derive(Deserialize)] #[serde(rename_all = "camelCase")] struct ImportUploadResponse { file_name: String, total_bytes: u64 }

pub fn load_or_create_control_options() -> Result<WorldControlOptions, String> {
    let path = control_config_path()?;
    let mut options = if path.is_file() {
        let text = fs::read_to_string(&path).map_err(|error| error.to_string())?;
        serde_json::from_str::<WorldControlOptions>(&text).map_err(|error| error.to_string())?
    } else {
        let legacy = legacy_control_config_path()?;
        if legacy.is_file() {
            let text = fs::read_to_string(&legacy).map_err(|error| error.to_string())?;
            let migrated = serde_json::from_str::<WorldControlOptions>(&text).map_err(|error| error.to_string())?;
            save_control_options(&path, &migrated)?;
            migrated
        } else {
            WorldControlOptions::default()
        }
    };
    if options.port < 1024 { return Err("World control port must be between 1024 and 65535".into()); }
    if options.token.trim().is_empty() {
        options.token = generate_token();
        save_control_options(&path, &options)?;
    }
    Ok(options)
}

pub fn list_worlds() -> Result<Vec<ManagedWorldSummary>, String> {
    let payload: WorldListResponse = request_json("GET", "/v1/worlds", Option::<&()>::None)?;
    Ok(payload.worlds)
}

pub fn create_world(request: &CreateWorldRequest) -> Result<ManagedWorldSummary, String> {
    if request.folder_name.trim().is_empty() { return Err("World folder name must not be empty.".into()); }
    let kind = request.kind.trim().to_uppercase();
    if kind != "FLAT" && kind != "VOID" { return Err("World type must be Flat or Void.".into()); }
    request_json("POST", "/v1/worlds", Some(request))
}

pub fn load_world(world_id: &str) -> Result<ManagedWorldSummary, String> {
    request_json("POST", &format!("/v1/worlds/{}/load", validate_world_id(world_id)?), Option::<&()>::None)
}
pub fn unload_world(world_id: &str) -> Result<ManagedWorldSummary, String> {
    request_json("POST", &format!("/v1/worlds/{}/unload", validate_world_id(world_id)?), Option::<&()>::None)
}
pub fn get_world_settings(world_id: &str) -> Result<WorldSettingsSnapshot, String> {
    request_json("GET", &format!("/v1/worlds/{}/settings", validate_world_id(world_id)?), Option::<&()>::None)
}
pub fn update_world_settings(world_id: &str, request: &UpdateWorldSettingsRequest) -> Result<WorldSettingsSnapshot, String> {
    request_json("PATCH", &format!("/v1/worlds/{}/settings", validate_world_id(world_id)?), Some(request))
}

pub fn list_world_tasks() -> Result<Vec<WorldTaskSnapshot>, String> {
    let payload: WorldTaskListResponse = request_json("GET", "/v1/tasks", Option::<&()>::None)?;
    Ok(payload.tasks)
}
pub fn get_world_task(task_id: &str) -> Result<WorldTaskSnapshot, String> {
    request_json("GET", &format!("/v1/tasks/{}", validate_task_id(task_id)?), Option::<&()>::None)
}
pub fn start_archive_world(world_id: &str) -> Result<WorldTaskSnapshot, String> { start_world_task("archive", world_id) }
pub fn start_restore_world(world_id: &str) -> Result<WorldTaskSnapshot, String> { start_world_task("restore", world_id) }
pub fn start_backup_world(world_id: &str) -> Result<WorldTaskSnapshot, String> { start_world_task("backup", world_id) }

pub fn start_clone_world(request: &CloneWorldRequest) -> Result<WorldTaskSnapshot, String> {
    validate_world_id(&request.world_id)?;
    if request.destination_folder.trim().is_empty() { return Err("Clone destination folder must not be empty.".into()); }
    if request.display_name.trim().is_empty() { return Err("Clone display name must not be empty.".into()); }
    request_json("POST", "/v1/tasks/clone", Some(request))
}

pub fn start_export_world(request: &ExportWorldRequest) -> Result<WorldTaskSnapshot, String> {
    validate_world_id(&request.world_id)?;
    if request.target_format.trim().is_empty() { return Err("Export target format must not be empty.".into()); }
    if request.artifact_name.trim().is_empty() { return Err("Export artifact name must not be empty.".into()); }
    request_json("POST", "/v1/tasks/export", Some(request))
}

pub fn start_import_world(request: &ImportWorldRequest) -> Result<WorldTaskSnapshot, String> {
    if request.artifact_name.trim().is_empty() { return Err("Import artifact name must not be empty.".into()); }
    if request.destination_folder.trim().is_empty() { return Err("Import destination folder must not be empty.".into()); }
    if request.display_name.trim().is_empty() { return Err("Import display name must not be empty.".into()); }
    request_json("POST", "/v1/tasks/import", Some(request))
}

pub fn start_delete_world(request: &DeleteWorldRequest) -> Result<WorldTaskSnapshot, String> {
    validate_world_id(&request.world_id)?;
    if request.typed_display_name.trim().is_empty() { return Err("Delete confirmation must not be empty.".into()); }
    request_json("POST", "/v1/tasks/delete", Some(request))
}

pub fn upload_world_import(file_path: &str) -> Result<String, String> {
    let path = PathBuf::from(file_path);
    if !path.is_file() { return Err("Selected world import file does not exist.".into()); }
    let file_name = path.file_name().and_then(|value| value.to_str())
        .ok_or_else(|| "Selected world import filename is invalid.".to_string())?;
    let lower = file_name.to_ascii_lowercase();
    if !lower.ends_with(".zip") && !lower.ends_with(".mcworld") {
        return Err("World import file must be .zip or .mcworld.".into());
    }
    let total_bytes = fs::metadata(&path).map_err(|error| error.to_string())?.len();
    if total_bytes == 0 { return Err("World import file is empty.".into()); }
    let sha256 = sha256_file(&path)?;
    let options = load_or_create_control_options()?;
    let url = format!("http://127.0.0.1:{}/v1/imports/upload", options.port);
    let authorization = format!("Bearer {}", options.token);
    let file = File::open(&path).map_err(|error| error.to_string())?;
    let agent = ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(2))
        .timeout_read(Duration::from_secs(30))
        .timeout_write(Duration::from_secs(300))
        .build();
    let response = agent
        .post(&url)
        .set("Authorization", &authorization)
        .set("Content-Type", "application/octet-stream")
        .set("Content-Length", &total_bytes.to_string())
        .set("X-LazyBuilder-File-Name", file_name)
        .set("X-LazyBuilder-Sha256", &sha256)
        .send(file);
    match response {
        Ok(response) => {
            let uploaded = response.into_json::<ImportUploadResponse>()
                .map_err(|error| format!("Invalid World-Manager upload response: {error}"))?;
            if uploaded.total_bytes != total_bytes { return Err("World-Manager upload size confirmation mismatch.".into()); }
            Ok(uploaded.file_name)
        }
        Err(ureq::Error::Status(_, response)) => match response.into_json::<ErrorResponse>() {
            Ok(error) => Err(format!("World-Manager {}: {}", error.error, error.message)),
            Err(_) => Err("World-Manager import upload failed.".into()),
        },
        Err(error) => Err(format!("World-Manager import upload unavailable: {error}")),
    }
}

fn start_world_task(operation: &str, world_id: &str) -> Result<WorldTaskSnapshot, String> {
    let world_id = validate_world_id(world_id)?;
    let body = WorldTaskStartRequest { world_id };
    request_json("POST", &format!("/v1/tasks/{operation}"), Some(&body))
}

fn request_json<T, B>(method: &str, path: &str, body: Option<&B>) -> Result<T, String>
where T: DeserializeOwned, B: Serialize + ?Sized {
    let options = load_or_create_control_options()?;
    let url = format!("http://127.0.0.1:{}{}", options.port, path);
    let authorization = format!("Bearer {}", options.token);
    let agent = ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(2)).timeout_read(Duration::from_secs(8))
        .timeout_write(Duration::from_secs(8)).build();
    let request = agent.request(method, &url).set("Authorization", &authorization);
    let response = match body { Some(payload) => request.send_json(ureq::json!(payload)), None => request.call() };
    match response {
        Ok(response) => response.into_json::<T>().map_err(|error| format!("Invalid World-Manager response: {error}")),
        Err(ureq::Error::Status(_, response)) => match response.into_json::<ErrorResponse>() {
            Ok(error) => Err(format!("World-Manager {}: {}", error.error, error.message)),
            Err(_) => Err("World-Manager request failed.".into()),
        },
        Err(error) => Err(format!("World-Manager control bridge unavailable: {error}")),
    }
}

fn sha256_file(path: &Path) -> Result<String, String> {
    let mut file = File::open(path).map_err(|error| error.to_string())?;
    let mut digest = Sha256::new();
    let mut buffer = [0u8; 1024 * 1024];
    loop {
        let read = file.read(&mut buffer).map_err(|error| error.to_string())?;
        if read == 0 { break; }
        digest.update(&buffer[..read]);
    }
    Ok(format!("{:x}", digest.finalize()))
}

fn validate_world_id(world_id: &str) -> Result<&str, String> {
    let value = world_id.trim();
    if value.is_empty() || !value.chars().all(|ch| ch.is_ascii_hexdigit() || ch == '-') { return Err("Invalid world id.".into()); }
    Ok(value)
}
fn validate_task_id(task_id: &str) -> Result<&str, String> {
    let value = task_id.trim();
    if value.is_empty() || !value.chars().all(|ch| ch.is_ascii_hexdigit() || ch == '-') { return Err("Invalid world task id.".into()); }
    Ok(value)
}
fn control_config_path() -> Result<PathBuf, String> { Ok(paths::lazybuilder_config_dir()?.join("world-control.json")) }
fn legacy_control_config_path() -> Result<PathBuf, String> { Ok(paths::lazybuilder_tools_dir()?.join("world-control.json")) }
fn save_control_options(path: &PathBuf, options: &WorldControlOptions) -> Result<(), String> {
    if let Some(parent) = path.parent() { fs::create_dir_all(parent).map_err(|error| error.to_string())?; }
    let temporary = path.with_extension("json.tmp");
    let text = serde_json::to_string_pretty(options).map_err(|error| error.to_string())?;
    fs::write(&temporary, text).map_err(|error| error.to_string())?;
    if path.exists() { fs::remove_file(path).map_err(|error| error.to_string())?; }
    fs::rename(&temporary, path).map_err(|error| error.to_string())?;
    Ok(())
}
fn generate_token() -> String {
    let mut bytes = [0u8; 32]; rand::thread_rng().fill_bytes(&mut bytes);
    let mut token = String::with_capacity(64);
    for byte in bytes { use std::fmt::Write; let _ = write!(&mut token, "{byte:02X}"); }
    token
}
