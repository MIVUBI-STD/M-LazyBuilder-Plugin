use serde::{Deserialize, Serialize};

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WorldControlOptions {
    pub port: u16,
    pub token: String,
}

impl Default for WorldControlOptions {
    fn default() -> Self {
        Self { port: super::DEFAULT_PORT, token: String::new() }
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

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WorldBridgeStatus {
    pub status: String,
    pub protocol_version: u32,
    #[serde(default)]
    pub capabilities: Vec<String>,
}
