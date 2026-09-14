use crate::engine::client_integration::{self, ClientIntegrationStatus};
use tauri::{AppHandle, Manager};

#[tauri::command]
pub async fn client_integration_status(app: AppHandle) -> Result<ClientIntegrationStatus, String> {
    let resource_dir = app.path().resource_dir().ok();
    run_blocking("Client setup status", move || {
        client_integration::status(resource_dir.as_deref())
    })
    .await
}

#[tauri::command]
pub async fn client_integration_select_profile(
    app: AppHandle,
    profile_path: String,
) -> Result<ClientIntegrationStatus, String> {
    let resource_dir = app.path().resource_dir().ok();
    run_blocking("Client profile selection", move || {
        client_integration::select_profile(&profile_path)?;
        client_integration::status(resource_dir.as_deref())
    })
    .await
}

#[tauri::command]
pub async fn client_integration_sync(app: AppHandle) -> Result<ClientIntegrationStatus, String> {
    let resource_dir = app.path().resource_dir().ok();
    run_blocking("Client sync", move || {
        client_integration::sync(resource_dir.as_deref())?;
        client_integration::status(resource_dir.as_deref())
    })
    .await
}

async fn run_blocking<T, F>(label: &'static str, work: F) -> Result<T, String>
where
    T: Send + 'static,
    F: FnOnce() -> Result<T, String> + Send + 'static,
{
    tauri::async_runtime::spawn_blocking(work)
        .await
        .map_err(|error| format!("{label} task failed: {error}"))?
}
