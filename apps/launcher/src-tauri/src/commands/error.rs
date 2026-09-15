use crate::engine::diagnostics;
use serde::Serialize;

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CommandError {
    pub code: &'static str,
    pub message: String,
}

impl CommandError {
    pub fn new(code: &'static str, message: impl Into<String>) -> Self {
        let message = message.into();
        diagnostics::error(&format!("{code}: {message}"));
        Self { code, message }
    }

    pub fn runtime(message: impl Into<String>) -> Self {
        Self::new("RUNTIME_ERROR", message)
    }
}

impl From<String> for CommandError {
    fn from(message: String) -> Self {
        Self::runtime(message)
    }
}

impl From<&str> for CommandError {
    fn from(message: &str) -> Self {
        Self::runtime(message)
    }
}

pub type CommandResult<T> = Result<T, CommandError>;
