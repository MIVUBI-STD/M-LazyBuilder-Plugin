use crate::engine::diagnostics;
use serde::Serialize;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RecoveryAction {
    AcceptEula,
    EditCommand,
    LocateWorkspace,
    OpenActivity,
    OpenLogs,
    ReconnectClientProfile,
    RepairServer,
    RestartLauncher,
    RetryOperation,
    ReviewBackups,
    ReviewPlugins,
    ReviewServerHealth,
    StartServer,
    StopServer,
    UseServerControls,
    WaitForServerStart,
    WaitForServerStop,
}

impl RecoveryAction {
    pub const fn as_code(self) -> &'static str {
        match self {
            Self::AcceptEula => "ACCEPT_EULA",
            Self::EditCommand => "EDIT_COMMAND",
            Self::LocateWorkspace => "LOCATE_WORKSPACE",
            Self::OpenActivity => "OPEN_ACTIVITY",
            Self::OpenLogs => "OPEN_LOGS",
            Self::ReconnectClientProfile => "RECONNECT_CLIENT_PROFILE",
            Self::RepairServer => "REPAIR_SERVER",
            Self::RestartLauncher => "RESTART_LAUNCHER",
            Self::RetryOperation => "RETRY_OPERATION",
            Self::ReviewBackups => "REVIEW_BACKUPS",
            Self::ReviewPlugins => "REVIEW_PLUGINS",
            Self::ReviewServerHealth => "REVIEW_SERVER_HEALTH",
            Self::StartServer => "START_SERVER",
            Self::StopServer => "STOP_SERVER",
            Self::UseServerControls => "USE_SERVER_CONTROLS",
            Self::WaitForServerStart => "WAIT_FOR_SERVER_START",
            Self::WaitForServerStop => "WAIT_FOR_SERVER_STOP",
        }
    }
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CommandError {
    pub code: &'static str,
    pub message: String,
    pub details: String,
    pub recoverable: bool,
    pub action: Option<String>,
    pub correlation_id: String,
}

impl CommandError {
    pub fn new(code: &'static str, message: impl Into<String>) -> Self {
        let message = message.into();
        let correlation_id = diagnostics::new_correlation_id("command");
        diagnostics::error_with_context(&correlation_id, &format!("{code}: {message}"));
        Self {
            code,
            message,
            details: String::new(),
            recoverable: false,
            action: None,
            correlation_id,
        }
    }

    /// Compatibility constructor for call sites that have not yet migrated to
    /// machine-readable RecoveryAction values. New code should use
    /// `recoverable_action` so UI behavior never depends on backend prose.
    pub fn recoverable(code: &'static str, message: impl Into<String>, action: impl Into<String>) -> Self {
        let mut error = Self::new(code, message);
        error.recoverable = true;
        error.action = Some(action.into());
        error
    }

    pub fn recoverable_action(code: &'static str, message: impl Into<String>, action: RecoveryAction) -> Self {
        let mut error = Self::new(code, message);
        error.recoverable = true;
        error.action = Some(action.as_code().into());
        error
    }

    pub fn with_details(mut self, details: impl Into<String>) -> Self {
        self.details = details.into();
        self
    }

    pub fn runtime(message: impl Into<String>) -> Self {
        Self::new("RUNTIME_ERROR", message)
    }
}

impl From<String> for CommandError {
    fn from(message: String) -> Self { Self::runtime(message) }
}

impl From<&str> for CommandError {
    fn from(message: &str) -> Self { Self::runtime(message) }
}

pub type CommandResult<T> = Result<T, CommandError>;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn typed_recovery_actions_expose_stable_machine_code_and_correlation() {
        let error = CommandError::recoverable_action(
            "WORKSPACE_UNAVAILABLE",
            "Missing",
            RecoveryAction::LocateWorkspace,
        );
        assert!(error.recoverable);
        assert_eq!(error.action.as_deref(), Some("LOCATE_WORKSPACE"));
        assert!(!error.correlation_id.is_empty());
    }

    #[test]
    fn recovery_action_codes_are_not_presentation_copy() {
        assert_eq!(RecoveryAction::StopServer.as_code(), "STOP_SERVER");
        assert_eq!(RecoveryAction::RestartLauncher.as_code(), "RESTART_LAUNCHER");
        assert_eq!(RecoveryAction::ReviewBackups.as_code(), "REVIEW_BACKUPS");
        assert_eq!(RecoveryAction::ReviewPlugins.as_code(), "REVIEW_PLUGINS");
        assert_eq!(RecoveryAction::ReviewServerHealth.as_code(), "REVIEW_SERVER_HEALTH");
        assert_eq!(RecoveryAction::UseServerControls.as_code(), "USE_SERVER_CONTROLS");
        assert_eq!(RecoveryAction::WaitForServerStop.as_code(), "WAIT_FOR_SERVER_STOP");
    }
}
