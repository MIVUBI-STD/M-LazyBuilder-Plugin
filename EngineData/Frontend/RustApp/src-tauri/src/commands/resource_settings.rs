use crate::engine::resource_settings::{self, ResourceUpdateRequest, ServerResourceProfile};

#[tauri::command]
pub fn server_resource_profile() -> Result<ServerResourceProfile, String> {
    resource_settings::profile()
}

#[tauri::command]
pub fn server_resource_save(request: ResourceUpdateRequest) -> Result<ServerResourceProfile, String> {
    resource_settings::save(request)
}

#[tauri::command]
pub fn server_resource_preset(name: String) -> Result<ServerResourceProfile, String> {
    resource_settings::apply_preset(&name)
}
