use serde::Serialize;
use std::fs;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PluginSummary {
    pub id: String,
    pub display_name: String,
    pub version: String,
    pub category: String,
    pub state: String,
    pub problem_detail: Option<String>,
}

pub fn list_plugins() -> Result<Vec<PluginSummary>, String> {
    let root = std::env::current_dir().map_err(|e| e.to_string())?.join("server").join("plugins");
    if !root.is_dir() { return Ok(Vec::new()); }
    let mut result = Vec::new();
    for entry in fs::read_dir(root).map_err(|e| e.to_string())? {
        let entry = entry.map_err(|e| e.to_string())?;
        let path = entry.path();
        if path.extension().and_then(|v| v.to_str()).map(|v| v.eq_ignore_ascii_case("jar")) != Some(true) { continue; }
        let name = path.file_stem().and_then(|v| v.to_str()).unwrap_or("plugin").to_string();
        result.push(PluginSummary {
            id: name.to_lowercase(), display_name: name, version: "Unknown".into(), category: "Other".into(), state: "Enabled".into(), problem_detail: None
        });
    }
    Ok(result)
}
