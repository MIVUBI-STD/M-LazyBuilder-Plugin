use crate::engine::plugin_manager::{list_plugins, PluginSummary};

#[tauri::command]
pub fn plugin_list() -> Result<Vec<PluginSummary>, String> {
    list_plugins()
}
