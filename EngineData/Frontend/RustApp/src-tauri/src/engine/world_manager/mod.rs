use crate::engine::paths;
use rand::RngCore;
use serde::{Deserialize, Serialize};
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

#[derive(Deserialize)]
struct WorldListResponse {
    worlds: Vec<ManagedWorldSummary>,
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
    let options = load_or_create_control_options()?;
    let url = format!("http://127.0.0.1:{}/v1/worlds", options.port);
    let authorization = format!("Bearer {}", options.token);

    let agent = ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(2))
        .timeout_read(Duration::from_secs(4))
        .timeout_write(Duration::from_secs(4))
        .build();

    let response = agent
        .get(&url)
        .set("Authorization", &authorization)
        .call()
        .map_err(|error| format!("World-Manager control bridge unavailable: {error}"))?;

    let payload: WorldListResponse = response
        .into_json()
        .map_err(|error| format!("Invalid World-Manager response: {error}"))?;
    Ok(payload.worlds)
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
