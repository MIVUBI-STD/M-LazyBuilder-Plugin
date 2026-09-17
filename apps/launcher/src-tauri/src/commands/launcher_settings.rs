use crate::commands::error::{CommandError, CommandResult, RecoveryAction};
use crate::engine::launcher_settings::{self, LauncherSettings};

#[tauri::command]
pub fn launcher_settings_get() -> CommandResult<LauncherSettings> {
    launcher_settings::load().map_err(|error| {
        CommandError::recoverable_action(
            "SETTINGS_READ_FAILED",
            error,
            RecoveryAction::RetryOperation,
        )
    })
}

#[tauri::command]
pub fn launcher_settings_save(settings: LauncherSettings) -> CommandResult<LauncherSettings> {
    launcher_settings::save(&settings).map_err(|error| {
        CommandError::recoverable_action(
            "SETTINGS_SAVE_FAILED",
            error,
            RecoveryAction::RetryOperation,
        )
    })
}
