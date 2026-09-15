use crate::engine::{diagnostics, runtime_environment, server_process_guard, workspace_registry};
use serde::Serialize;
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum StartupStepState {
    Ready,
    Warning,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct StartupStep {
    pub key: String,
    pub state: StartupStepState,
    pub summary: String,
    pub details: String,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct StartupReport {
    pub ready: bool,
    pub degraded: bool,
    pub started_at_unix_seconds: u64,
    pub completed_at_unix_seconds: u64,
    pub runtime_temp_path: Option<String>,
    pub steps: Vec<StartupStep>,
}

pub fn coordinate() -> StartupReport {
    let started_at = now_unix_seconds();
    diagnostics::info("LazyBuilder startup coordinator beginning");

    let mut steps = Vec::new();
    let mut degraded = false;

    let runtime_temp = match runtime_environment::prepare() {
        Ok(temp) => {
            diagnostics::info(&format!("LazyBuilder runtime TEMP/TMP: {}", temp.display()));
            steps.push(ready_step(
                "runtime-environment",
                "Runtime environment ready",
                &format!("Temporary runtime path: {}", temp.display()),
            ));
            Some(temp)
        }
        Err(error) => {
            degraded = true;
            diagnostics::error(&format!("Runtime environment preparation failed: {error}"));
            eprintln!("LazyBuilder runtime environment preparation failed: {error}");
            steps.push(warning_step(
                "runtime-environment",
                "Runtime environment needs attention",
                &error,
            ));
            None
        }
    };

    let registry_ready = match workspace_registry::initialize() {
        Ok(()) => {
            steps.push(ready_step(
                "workspace-registry",
                "Server library ready",
                "Workspace registry loaded and pending deletion recovery completed.",
            ));
            true
        }
        Err(error) => {
            degraded = true;
            diagnostics::error(&format!("Workspace registry initialization failed: {error}"));
            eprintln!("LazyBuilder workspace registry initialization failed: {error}");
            steps.push(warning_step(
                "workspace-registry",
                "Server library needs attention",
                &error,
            ));
            false
        }
    };

    if registry_ready {
        match server_process_guard::ensure_no_running_paper_except(None) {
            Ok(()) => steps.push(ready_step(
                "server-process-reconciliation",
                "Background server state reconciled",
                "No registered LazyBuilder Paper process requires recovery attention.",
            )),
            Err(message) => {
                degraded = true;
                diagnostics::info(&format!("Startup detected recoverable background Paper state: {message}"));
                steps.push(warning_step(
                    "server-process-reconciliation",
                    "A background server needs attention",
                    &message,
                ));
            }
        }
    } else {
        degraded = true;
        steps.push(warning_step(
            "server-process-reconciliation",
            "Background server state could not be checked",
            "The server library was unavailable, so LazyBuilder could not safely inspect registered Paper process markers.",
        ));
    }

    let completed_at = now_unix_seconds();
    let report = StartupReport {
        ready: !degraded,
        degraded,
        started_at_unix_seconds: started_at,
        completed_at_unix_seconds: completed_at,
        runtime_temp_path: runtime_temp.as_ref().map(|path| path.display().to_string()),
        steps,
    };

    diagnostics::info(if report.degraded {
        "LazyBuilder startup coordinator completed in degraded mode"
    } else {
        "LazyBuilder startup coordinator completed"
    });
    report
}

fn ready_step(key: &str, summary: &str, details: &str) -> StartupStep {
    StartupStep {
        key: key.into(),
        state: StartupStepState::Ready,
        summary: summary.into(),
        details: details.into(),
    }
}

fn warning_step(key: &str, summary: &str, details: &str) -> StartupStep {
    StartupStep {
        key: key.into(),
        state: StartupStepState::Warning,
        summary: summary.into(),
        details: details.into(),
    }
}

fn now_unix_seconds() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|value| value.as_secs())
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn startup_steps_preserve_machine_readable_keys() {
        let step = ready_step("workspace-registry", "ready", "details");
        assert_eq!(step.key, "workspace-registry");
        assert!(matches!(step.state, StartupStepState::Ready));
    }

    #[test]
    fn warning_steps_are_explicitly_degraded() {
        let step = warning_step("runtime-environment", "warning", "details");
        assert!(matches!(step.state, StartupStepState::Warning));
    }
}
