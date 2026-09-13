use crate::engine::performance_diagnostics::{self, JfrStartRequest, JfrStatus};

#[tauri::command]
pub fn performance_jfr_status() -> Result<JfrStatus, String> {
    performance_diagnostics::status()
}

#[tauri::command]
pub fn performance_jfr_start(request: JfrStartRequest) -> Result<JfrStatus, String> {
    performance_diagnostics::start(request)
}

#[tauri::command]
pub fn performance_jfr_stop() -> Result<JfrStatus, String> {
    performance_diagnostics::stop()
}
