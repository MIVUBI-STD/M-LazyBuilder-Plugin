use crate::engine::diagnostics;
use serde::Serialize;

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

    pub fn recoverable(code: &'static str, message: impl Into<String>, action: impl Into<String>) -> Self {
        let mut error = Self::new(code, message);
        error.recoverable = true;
        error.action = Some(action.into());
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
    fn recoverable_errors_expose_action_and_correlation() {
        let error = CommandError::recoverable("WORKSPACE_UNAVAILABLE", "Missing", "Locate server");
        assert!(error.recoverable);
        assert_eq!(error.action.as_deref(), Some("Locate server"));
        assert!(!error.correlation_id.is_empty());
    }
}
