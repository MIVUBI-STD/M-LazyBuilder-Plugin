use crate::commands::error::{CommandError, CommandResult};
use crate::engine::launcher_settings::{self, LauncherSettings};

#[tauri::command]
pub fn launcher_settings_get() -> CommandResult<LauncherSettings> {
    launcher_settings::load().map_err(CommandError::from)
}

#[tauri::command]
pub fn launcher_settings_save(settings: LauncherSettings) -> CommandResult<LauncherSettings> {
    launcher_settings::save(&settings).map_err(CommandError::from)
}
