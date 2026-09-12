use serde::Serialize;

#[derive(Serialize)]
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

pub fn list_worlds() -> Result<Vec<ManagedWorldSummary>, String> {
    Err("World-Manager Rust control client is not wired yet. Start with the authenticated loopback bridge migration next.".into())
}
