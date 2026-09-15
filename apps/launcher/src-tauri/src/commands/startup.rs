use crate::engine::startup::StartupReport;
use tauri::State;

#[tauri::command]
pub fn launcher_startup_status(report: State<'_, StartupReport>) -> StartupReport {
    report.inner().clone()
}
