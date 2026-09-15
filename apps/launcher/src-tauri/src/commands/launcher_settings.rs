use crate::commands::error::{CommandError, CommandResult};
use crate::engine::launcher_settings::{self, LauncherSettings};

#[tauri::command]
pub fn launcher_settings_get() -> CommandResult<LauncherSettings> {
    launcher_settings::load().map_err(|error| {
        CommandError::recoverable(
            "SETTINGS_READ_FAILED",
            error,
            "Review or reset the launcher settings file, then retry.",
        )
    })
}

#[tauri::command]
pub fn launcher_settings_save(settings: LauncherSettings) -> CommandResult<LauncherSettings> {
    launcher_settings::save(&settings).map_err(|error| {
        CommandError::recoverable(
            "SETTINGS_SAVE_FAILED",
            error,
            "Review the launcher settings values, then retry.",
        )
    })
}
