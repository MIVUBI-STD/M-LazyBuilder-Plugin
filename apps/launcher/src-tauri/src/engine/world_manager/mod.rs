use crate::engine::{paths, persistence};
use rand::RngCore;
use serde::{de::DeserializeOwned, Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::fs::{self, File};
use std::io::Read;
use std::net::TcpListener;
use std::path::{Path, PathBuf};
use std::time::Duration;

const DEFAULT_CONTROL_READ_TIMEOUT: Duration = Duration::from_secs(8);
const CREATE_WORLD_READ_TIMEOUT: Duration = Duration::from_secs(120);

pub const TOKEN_ENV: &str = "LAZYBUILDER_WORLD_CONTROL_TOKEN";
pub const PORT_ENV: &str = "LAZYBUILDER_WORLD_CONTROL_PORT";
pub const DEFAULT_PORT: u16 = 17842;
const EXPECTED_PROTOCOL_VERSION: u32 = 2;
const CONTROL_PORT_SCAN_LIMIT: u16 = 128;
const CONTROL_OPTIONS_LABEL: &str = "World Manager control options";

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
pub struct DuplicateWorldRequest {
    pub world_id: String,
    pub destination_folder: String,
    pub display_name: String,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExportWorldRequest {
    pub world_id: String,
    pub target_format: String,
    pub artifact_name: String,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportWorldRequest {
    pub artifact_name: String,
    pub destination_folder: String,
    pub display_name: String,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeleteWorldRequest {
    pub world_id: String,
    pub typed_display_name: String,
}

#[derive(Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct UpdateWorldSettingsRequest {
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
struct StatusResponse {
    status: String,
    #[serde(rename = "protocolVersion")]
    protocol_version: u32,
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

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ImportUploadResponse {
    file_name: String,
    total_bytes: u64,
}

pub fn load_or_create_control_options() -> Result<WorldControlOptions, String> {
    let path = control_config_path()?;
    persistence::recover_atomic_file(&path, CONTROL_OPTIONS_LABEL)?;
    let mut options = if persistence::metadata_entry_exists(&path, CONTROL_OPTIONS_LABEL)? {
        persistence::read_json::<WorldControlOptions>(&path, CONTROL_OPTIONS_LABEL)?
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
    if options.port < 1024 {
        return Err("World control port must be between 1024 and 65535".into());
    }
    if options.token.trim().is_empty() {
        options.token = generate_token();
        save_control_options(&path, &options)?;
    } else {
        persistence::cleanup_recovery_files(&path, CONTROL_OPTIONS_LABEL)?;
    }
    Ok(options)
}

/// Ensures the active workspace owns a currently free loopback control port.
/// The selected port is persisted by the World Manager config owner so every
/// subsequent Launcher request for this workspace resolves the same bridge.
pub fn prepare_control_options_for_start() -> Result<WorldControlOptions, String> {
    let path = control_config_path()?;
    let mut options = load_or_create_control_options()?;
    if loopback_port_available(options.port) {
        return Ok(options);
    }

    let mut candidate = options.port.saturating_add(1).max(DEFAULT_PORT);
    for _ in 0..CONTROL_PORT_SCAN_LIMIT {
        if candidate < 1024 {
            candidate = DEFAULT_PORT;
        }
        if loopback_port_available(candidate) {
            options.port = candidate;
            save_control_options(&path, &options)?;
            return Ok(options);
        }
        candidate = candidate.checked_add(1).unwrap_or(DEFAULT_PORT);
    }

    Err(format!(
        "LazyBuilder could not find a free World Manager loopback port after checking {CONTROL_PORT_SCAN_LIMIT} candidates. Stop conflicting local services and retry."
    ))
}

fn loopback_port_available(port: u16) -> bool {
    TcpListener::bind(("127.0.0.1", port)).is_ok()
}

pub fn list_worlds() -> Result<Vec<ManagedWorldSummary>, String> {
    ensure_bridge_compatible()?;
    let payload: WorldListResponse = request_json("GET", "/v1/worlds", Option::<&()>::None)?;
    Ok(payload.worlds)
}

pub fn create_world(request: &CreateWorldRequest) -> Result<ManagedWorldSummary, String> {
    if request.folder_name.trim().is_empty() {
        return Err("World folder name must not be empty.".into());
    }
    if request.display_name.trim().is_empty() {
        return Err("World display name must not be empty.".into());
    }
    let kind = request.kind.trim().to_uppercase();
    if kind != "FLAT" && kind != "VOID" {
        return Err("World type must be Flat or Void.".into());
    }
    ensure_bridge_compatible()?;
    request_json_with_read_timeout(
        "POST",
        "/v1/worlds",
        Some(request),
        CREATE_WORLD_READ_TIMEOUT,
    )
}

pub fn get_world_settings(world_id: &str) -> Result<WorldSettingsSnapshot, String> {
    request_json(
        "GET",
        &format!("/v1/worlds/{}/settings", validate_world_id(world_id)?),
        Option::<&()>::None,
    )
}

pub fn update_world_settings(
    world_id: &str,
    request: &UpdateWorldSettingsRequest,
) -> Result<WorldSettingsSnapshot, String> {
    request_mutation_json(
        "PATCH",
        &format!("/v1/worlds/{}/settings", validate_world_id(world_id)?),
        Some(request),
    )
}

pub fn list_world_tasks() -> Result<Vec<WorldTaskSnapshot>, String> {
    let payload: WorldTaskListResponse = request_json("GET", "/v1/tasks", Option::<&()>::None)?;
    Ok(payload.tasks)
}

pub fn get_world_task(task_id: &str) -> Result<WorldTaskSnapshot, String> {
    request_json(
        "GET",
        &format!("/v1/tasks/{}", validate_task_id(task_id)?),
        Option::<&()>::None,
    )
}

pub fn start_archive_world(world_id: &str) -> Result<WorldTaskSnapshot, String> {
    start_world_task("archive", world_id)
}

pub fn start_restore_world(world_id: &str) -> Result<WorldTaskSnapshot, String> {
    start_world_task("restore", world_id)
}

pub fn start_backup_world(world_id: &str) -> Result<WorldTaskSnapshot, String> {
    start_world_task("backup", world_id)
}

pub fn start_duplicate_world(request: &DuplicateWorldRequest) -> Result<WorldTaskSnapshot, String> {
    validate_world_id(&request.world_id)?;
    if request.destination_folder.trim().is_empty() {
        return Err("Duplicate destination folder must not be empty.".into());
    }
    if request.display_name.trim().is_empty() {
        return Err("Duplicate display name must not be empty.".into());
    }
    request_mutation_json("POST", "/v1/tasks/duplicate", Some(request))
}

pub fn start_export_world(request: &ExportWorldRequest) -> Result<WorldTaskSnapshot, String> {
    validate_world_id(&request.world_id)?;
    if request.target_format.trim().is_empty() {
        return Err("Export target format must not be empty.".into());
    }
    if request.artifact_name.trim().is_empty() {
        return Err("Export artifact name must not be empty.".into());
    }
    request_mutation_json("POST", "/v1/tasks/export", Some(request))
}

pub fn start_import_world(request: &ImportWorldRequest) -> Result<WorldTaskSnapshot, String> {
    if request.artifact_name.trim().is_empty() {
        return Err("Import artifact name must not be empty.".into());
    }
    if request.destination_folder.trim().is_empty() {
        return Err("Import destination folder must not be empty.".into());
    }
    if request.display_name.trim().is_empty() {
        return Err("Import display name must not be empty.".into());
    }
    request_mutation_json("POST", "/v1/tasks/import", Some(request))
}

pub fn start_delete_world(request: &DeleteWorldRequest) -> Result<WorldTaskSnapshot, String> {
    validate_world_id(&request.world_id)?;
    if request.typed_display_name.trim().is_empty() {
        return Err("Delete confirmation must not be empty.".into());
    }
    request_mutation_json("POST", "/v1/tasks/delete", Some(request))
}

pub fn upload_world_import(file_path: &str) -> Result<String, String> {
    let path = PathBuf::from(file_path);
    if !path.is_file() {
        return Err("Selected world import file does not exist.".into());
    }
    let file_name = path
        .file_name()
        .and_then(|value| value.to_str())
        .ok_or_else(|| "Selected world import filename is invalid.".to_string())?;
    let lower = file_name.to_ascii_lowercase();
    if !lower.ends_with(".zip") && !lower.ends_with(".mcworld") {
        return Err("World import file must be .zip or .mcworld.".into());
    }
    let total_bytes = fs::metadata(&path).map_err(|error| error.to_string())?.len();
    if total_bytes == 0 {
        return Err("World import file is empty.".into());
    }
    ensure_bridge_compatible()?;
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
            let uploaded = response
                .into_json::<ImportUploadResponse>()
                .map_err(|error| format!("Invalid World-Manager upload response: {error}"))?;
            if uploaded.total_bytes != total_bytes {
                return Err("World-Manager upload size confirmation mismatch.".into());
            }
            Ok(uploaded.file_name)
        }
        Err(ureq::Error::Status(_, response)) => match response.into_json::<ErrorResponse>() {
            Ok(error) => Err(format!("World-Manager {}: {}", error.error, error.message)),
            Err(_) => Err("World-Manager import upload failed.".into()),
        },
        Err(error) => Err(format!("World-Manager import upload unavailable: {error}")),
    }
}

fn ensure_bridge_compatible() -> Result<(), String> {
    let status: StatusResponse = request_json("GET", "/v1/status", Option::<&()>::None)?;
    if status.status != "ready" {
        return Err(format!("World-Manager desktop bridge is not ready: {}", status.status));
    }
    if status.protocol_version != EXPECTED_PROTOCOL_VERSION {
        return Err(format!(
            "World-Manager desktop bridge protocol mismatch. Launcher expects v{EXPECTED_PROTOCOL_VERSION}, server provides v{}.",
            status.protocol_version
        ));
    }
    Ok(())
}

fn start_world_task(operation: &str, world_id: &str) -> Result<WorldTaskSnapshot, String> {
    let world_id = validate_world_id(world_id)?;
    let body = WorldTaskStartRequest { world_id };
    request_mutation_json("POST", &format!("/v1/tasks/{operation}"), Some(&body))
}

fn request_mutation_json<T, B>(
    method: &str,
    path: &str,
    body: Option<&B>,
) -> Result<T, String>
where
    T: DeserializeOwned,
    B: Serialize + ?Sized,
{
    ensure_bridge_compatible()?;
    request_json(method, path, body)
}

fn request_json<T, B>(method: &str, path: &str, body: Option<&B>) -> Result<T, String>
where
    T: DeserializeOwned,
    B: Serialize + ?Sized,
{
    request_json_with_read_timeout(method, path, body, DEFAULT_CONTROL_READ_TIMEOUT)
}

fn request_json_with_read_timeout<T, B>(
    method: &str,
    path: &str,
    body: Option<&B>,
    read_timeout: Duration,
) -> Result<T, String>
where
    T: DeserializeOwned,
    B: Serialize + ?Sized,
{
    let options = load_or_create_control_options()?;
    let url = format!("http://127.0.0.1:{}{}", options.port, path);
    let authorization = format!("Bearer {}", options.token);
    let agent = ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(2))
        .timeout_read(read_timeout)
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
        if read == 0 {
            break;
        }
        digest.update(&buffer[..read]);
    }
    Ok(format!("{:x}", digest.finalize()))
}

fn validate_world_id(world_id: &str) -> Result<&str, String> {
    let value = world_id.trim();
    if value.is_empty() || !value.chars().all(|ch| ch.is_ascii_hexdigit() || ch == '-') {
        return Err("Invalid world id.".into());
    }
    Ok(value)
}

fn validate_task_id(task_id: &str) -> Result<&str, String> {
    let value = task_id.trim();
    if value.is_empty() || !value.chars().all(|ch| ch.is_ascii_hexdigit() || ch == '-') {
        return Err("Invalid world task id.".into());
    }
    Ok(value)
}

fn control_config_path() -> Result<PathBuf, String> {
    Ok(paths::lazybuilder_config_dir()?.join("world-control.json"))
}

fn legacy_control_config_path() -> Result<PathBuf, String> {
    Ok(paths::lazybuilder_tools_dir()?.join("world-control.json"))
}

fn save_control_options(path: &Path, options: &WorldControlOptions) -> Result<(), String> {
    persistence::write_json_atomically(path, options, CONTROL_OPTIONS_LABEL)
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

#[cfg(test)]
mod tests {
    use super::{loopback_port_available, CREATE_WORLD_READ_TIMEOUT, DEFAULT_CONTROL_READ_TIMEOUT};
    use std::net::TcpListener;

    #[test]
    fn create_world_timeout_exceeds_normal_control_timeout() {
        assert!(CREATE_WORLD_READ_TIMEOUT > DEFAULT_CONTROL_READ_TIMEOUT);
        assert_eq!(CREATE_WORLD_READ_TIMEOUT, std::time::Duration::from_secs(120));
    }

    #[test]
    fn occupied_loopback_port_is_not_available() {
        let listener = TcpListener::bind(("127.0.0.1", 0)).expect("bind ephemeral port");
        let port = listener.local_addr().expect("local address").port();
        assert!(!loopback_port_available(port));
        drop(listener);
        assert!(loopback_port_available(port));
    }
}
