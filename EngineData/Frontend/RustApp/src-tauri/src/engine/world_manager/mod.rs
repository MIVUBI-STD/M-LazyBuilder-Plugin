use crate::engine::paths;
use rand::RngCore;
use serde::{de::DeserializeOwned, Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;
use std::time::Duration;

pub const TOKEN_ENV: &str = "LAZYBUILDER_WORLD_CONTROL_TOKEN";
pub const PORT_ENV: &str = "LAZYBUILDER_WORLD_CONTROL_PORT";
pub const DEFAULT_PORT: u16 = 17842;

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WorldControlOptions {
    pub port: u16,
    pub token: String,
}

impl Default for WorldControlOptions {
    fn default() -> Self {
        Self { port: DEFAULT_PORT, token: String::new() }
    }
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ManagedWorldSummary {
    pub id: String,
    pub display_name: String,
    pub kind: String,
    pub lifecycle: String,
    pub runtime_state: String,
    pub auto_load: bool,
    pub default_game_mode: String,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateWorldRequest {
    pub folder_name: String,
    pub display_name: String,
    pub kind: String,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CloneWorldRequest {
    pub world_id: String,
    pub destination_folder: String,
    pub display_name: String,
}

#[derive(Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct UpdateWorldSettingsRequest {
    pub auto_load: Option<bool>,
    pub default_game_mode: Option<String>,
    pub time_of_day_ticks: Option<u64>,
    pub weather: Option<String>,
    pub natural_spawning: Option<bool>,
    pub daylight_cycle: Option<bool>,
    pub weather_cycle: Option<bool>,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WorldSettingsSnapshot {
    pub id: String,
    pub display_name: String,
    pub auto_load: bool,
    pub default_game_mode: String,
    pub time_of_day_ticks: u64,
    pub weather: String,
    pub natural_spawning: bool,
    pub daylight_cycle: bool,
    pub weather_cycle: bool,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WorldTaskSnapshot {
    pub task_id: String,
    #[serde(rename(deserialize = "type", serialize = "taskType"))]
    pub task_type: String,
    pub world_id: Option<String>,
    pub state: String,
    pub progress_percent: u8,
    pub message: String,
    pub result: String,
    pub error: String,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Deserialize)]
struct WorldListResponse {
    worlds: Vec<ManagedWorldSummary>,
}

#[derive(Deserialize)]
struct WorldTaskListResponse {
    tasks: Vec<WorldTaskSnapshot>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct WorldTaskStartRequest<'a> {
    world_id: &'a str,
}

#[derive(Deserialize)]
struct ErrorResponse {
    error: String,
    message: String,
}

pub fn load_or_create_control_options() -> Result<WorldControlOptions, String> {
    let path = control_config_path()?;
    let mut options = if path.is_file() {
        let text = fs::read_to_string(&path).map_err(|error| error.to_string())?;
        serde_json::from_str::<WorldControlOptions>(&text).map_err(|error| error.to_string())?
    } else {
        WorldControlOptions::default()
    };

    if options.port < 1024 {
        return Err("World control port must be between 1024 and 65535".into());
    }

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
    if request.folder_name.trim().is_empty() {
        return Err("World folder name must not be empty.".into());
    }
    let kind = request.kind.trim().to_uppercase();
    if kind != "FLAT" && kind != "VOID" {
        return Err("World type must be Flat or Void.".into());
    }
    request_json("POST", "/v1/worlds", Some(request))
}

pub fn load_world(world_id: &str) -> Result<ManagedWorldSummary, String> {
    let path = format!("/v1/worlds/{}/load", validate_world_id(world_id)?);
    request_json("POST", &path, Option::<&()>::None)
}

pub fn unload_world(world_id: &str) -> Result<ManagedWorldSummary, String> {
    let path = format!("/v1/worlds/{}/unload", validate_world_id(world_id)?);
    request_json("POST", &path, Option::<&()>::None)
}

pub fn get_world_settings(world_id: &str) -> Result<WorldSettingsSnapshot, String> {
    let path = format!("/v1/worlds/{}/settings", validate_world_id(world_id)?);
    request_json("GET", &path, Option::<&()>::None)
}

pub fn update_world_settings(
    world_id: &str,
    request: &UpdateWorldSettingsRequest,
) -> Result<WorldSettingsSnapshot, String> {
    let path = format!("/v1/worlds/{}/settings", validate_world_id(world_id)?);
    request_json("PATCH", &path, Some(request))
}

pub fn list_world_tasks() -> Result<Vec<WorldTaskSnapshot>, String> {
    let payload: WorldTaskListResponse = request_json("GET", "/v1/tasks", Option::<&()>::None)?;
    Ok(payload.tasks)
}

pub fn get_world_task(task_id: &str) -> Result<WorldTaskSnapshot, String> {
    let task_id = validate_task_id(task_id)?;
    request_json("GET", &format!("/v1/tasks/{task_id}"), Option::<&()>::None)
}

pub fn start_archive_world(world_id: &str) -> Result<WorldTaskSnapshot, String> {
    start_world_task("archive", world_id)
}

pub fn start_restore_world(world_id: &str) -> Result<WorldTaskSnapshot, String> {
    start_world_task("restore", world_id)
}

pub fn start_clone_world(request: &CloneWorldRequest) -> Result<WorldTaskSnapshot, String> {
    validate_world_id(&request.world_id)?;
    if request.destination_folder.trim().is_empty() {
        return Err("Clone destination folder must not be empty.".into());
    }
    if request.display_name.trim().is_empty() {
        return Err("Clone display name must not be empty.".into());
    }
    request_json("POST", "/v1/tasks/clone", Some(request))
}

fn start_world_task(operation: &str, world_id: &str) -> Result<WorldTaskSnapshot, String> {
    let world_id = validate_world_id(world_id)?;
    let body = WorldTaskStartRequest { world_id };
    request_json("POST", &format!("/v1/tasks/{operation}"), Some(&body))
}

fn request_json<T, B>(method: &str, path: &str, body: Option<&B>) -> Result<T, String>
where
    T: DeserializeOwned,
    B: Serialize + ?Sized,
{
    let options = load_or_create_control_options()?;
    let url = format!("http://127.0.0.1:{}{}", options.port, path);
    let authorization = format!("Bearer {}", options.token);
    let agent = ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(2))
        .timeout_read(Duration::from_secs(8))
        .timeout_write(Duration::from_secs(8))
        .build();

    let request = agent.request(method, &url).set("Authorization", &authorization);
    let response = match body {
        Some(payload) => request.send_json(ureq::json!(payload)),
        None => request.call(),
    };

    match response {
        Ok(response) => response
            .into_json::<T>()
            .map_err(|error| format!("Invalid World-Manager response: {error}")),
        Err(ureq::Error::Status(_, response)) => {
            let parsed = response.into_json::<ErrorResponse>();
            match parsed {
                Ok(error) => Err(format!("World-Manager {}: {}", error.error, error.message)),
                Err(_) => Err("World-Manager request failed.".into()),
            }
        }
        Err(error) => Err(format!("World-Manager control bridge unavailable: {error}")),
    }
}

fn validate_world_id(world_id: &str) -> Result<&str, String> {
    let value = world_id.trim();
    if value.is_empty()
        || !value.chars().all(|ch| ch.is_ascii_hexdigit() || ch == '-')
    {
        return Err("Invalid world id.".into());
    }
    Ok(value)
}

fn validate_task_id(task_id: &str) -> Result<&str, String> {
    let value = task_id.trim();
    if value.is_empty()
        || !value.chars().all(|ch| ch.is_ascii_hexdigit() || ch == '-')
    {
        return Err("Invalid world task id.".into());
    }
    Ok(value)
}

fn control_config_path() -> Result<PathBuf, String> {
    Ok(paths::lazybuilder_tools_dir()?.join("world-control.json"))
}

fn save_control_options(path: &PathBuf, options: &WorldControlOptions) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    let temporary = path.with_extension("json.tmp");
    let text = serde_json::to_string_pretty(options).map_err(|error| error.to_string())?;
    fs::write(&temporary, text).map_err(|error| error.to_string())?;
    if path.exists() {
        fs::remove_file(path).map_err(|error| error.to_string())?;
    }
    fs::rename(&temporary, path).map_err(|error| error.to_string())?;
    Ok(())
}

fn generate_token() -> String {
    let mut bytes = [0u8; 32];
    rand::thread_rng().fill_bytes(&mut bytes);
    let mut token = String::with_capacity(64);
    for byte in bytes {
        use std::fmt::Write;
        let _ = write!(&mut token, "{byte:02X}");
    }
    token
}
