use crate::engine::{adoption, backup_recovery, diagnostics, launcher_settings, operations::OperationRecoveryReport, runtime_environment, server_backups, server_process_guard, server_restore, workspace_creation, workspace_registry};
use serde::Serialize;
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum StartupStepState { Ready, Warning }

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct StartupStep { pub key: String, pub state: StartupStepState, pub summary: String, pub details: String }

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

pub fn coordinate(operation_recovery: Result<OperationRecoveryReport, String>, previous_session_unclean: bool) -> StartupReport {
    let started_at = now_unix_seconds();
    diagnostics::info("LazyBuilder startup coordinator beginning");
    let mut steps = Vec::new();
    let mut degraded = false;

    let runtime_temp = match runtime_environment::prepare() {
        Ok(temp) => {
            diagnostics::info(&format!("LazyBuilder runtime TEMP/TMP: {}", temp.display()));
            steps.push(ready_step("runtime-environment", "Runtime environment ready", &format!("Temporary runtime path: {}", temp.display())));
            Some(temp)
        }
        Err(error) => {
            degraded = true;
            diagnostics::error(&format!("Runtime environment preparation failed: {error}"));
            steps.push(warning_step("runtime-environment", "Runtime environment needs attention", &error));
            None
        }
    };

    if previous_session_unclean {
        degraded = true;
        diagnostics::info("Previous LazyBuilder session ended without releasing its instance marker; recovery checks will run before normal use.");
        steps.push(warning_step("launcher-session", "Previous Launcher session ended unexpectedly", "LazyBuilder recovered a stale instance marker. Filesystem, operation, creation, adoption, duplicate, restore, backup, and Paper process reconciliation will run before normal use."));
    } else {
        steps.push(ready_step("launcher-session", "Launcher session state clean", "No stale Launcher instance marker was found."));
    }

    match operation_recovery {
        Ok(report) if report.interrupted == 0 => steps.push(ready_step("operation-recovery", "Launcher operations reconciled", "No interrupted Launcher operation was found in the durable operation journal.")),
        Ok(report) => {
            degraded = true;
            let details = format!("Reconciled {} interrupted operation(s): {} require recovery review and {} low-risk operation(s) were marked failed/interrupted.", report.interrupted, report.recovery_required, report.failed);
            diagnostics::info(&format!("Launcher operation reconciliation: {details}"));
            steps.push(warning_step("operation-recovery", "Interrupted Launcher operations recovered", &details));
        }
        Err(error) => {
            degraded = true;
            diagnostics::error(&format!("Launcher operation journal initialization failed: {error}"));
            steps.push(warning_step("operation-recovery", "Launcher operation journal needs attention", &format!("{error}. Long-running Launcher mutations are disabled until the journal can be opened safely.")));
        }
    }

    let settings = match launcher_settings::initialize() {
        Ok(settings) => {
            steps.push(ready_step("launcher-settings", "Launcher settings ready", &format!("Settings schema {} loaded; update channel: {}.", settings.schema_version, settings.update_channel)));
            Some(settings)
        }
        Err(error) => {
            degraded = true;
            diagnostics::error(&format!("Launcher settings initialization failed: {error}"));
            steps.push(warning_step("launcher-settings", "Launcher settings need attention", &error));
            None
        }
    };

    let registry_ready = match workspace_registry::initialize() {
        Ok(()) => {
            steps.push(ready_step("workspace-registry", "Server library ready", "Workspace registry loaded and pending deletion recovery completed."));
            true
        }
        Err(error) => {
            degraded = true;
            diagnostics::error(&format!("Workspace registry initialization failed: {error}"));
            steps.push(warning_step("workspace-registry", "Server library needs attention", &error));
            false
        }
    };

    if registry_ready {
        match workspace_creation::recover_pending_creations() {
            Ok(report) => {
                let mut details = Vec::new();
                if report.completed > 0 { details.push(format!("Completed {} interrupted server creation(s).", report.completed)); }
                if report.cleaned > 0 { details.push(format!("Cleaned {} unpublished creation staging location(s).", report.cleaned)); }
                if !report.issues.is_empty() { details.push(report.issues.join(" ")); }
                if details.is_empty() { details.push("No interrupted server creation requires recovery.".into()); }
                let details = details.join(" ");
                if report.issues.is_empty() {
                    steps.push(ready_step("creation-recovery", "Server creation state reconciled", &details));
                } else {
                    degraded = true;
                    diagnostics::error(&format!("Server creation recovery needs attention: {details}"));
                    steps.push(warning_step("creation-recovery", "Server creation needs attention", &details));
                }
            }
            Err(error) => {
                degraded = true;
                diagnostics::error(&format!("Server creation recovery failed: {error}"));
                steps.push(warning_step("creation-recovery", "Server creation recovery could not run", &error));
            }
        }

        match adoption::recover_pending_adoptions() {
            Ok(report) => {
                let mut details = Vec::new();
                if report.completed > 0 { details.push(format!("Completed {} adopted server registration(s) after an interrupted Launcher session.", report.completed)); }
                if report.rolled_back > 0 { details.push(format!("Rolled back {} interrupted adoption(s) to their original server layout.", report.rolled_back)); }
                if !report.issues.is_empty() { details.push(report.issues.join(" ")); }
                if details.is_empty() { details.push("No interrupted server adoption requires recovery.".into()); }
                let details = details.join(" ");
                if report.issues.is_empty() {
                    steps.push(ready_step("adoption-recovery", "Server adoption state reconciled", &details));
                } else {
                    degraded = true;
                    diagnostics::error(&format!("Server adoption recovery needs attention: {details}"));
                    steps.push(warning_step("adoption-recovery", "Server adoption needs attention", &details));
                }
            }
            Err(error) => {
                degraded = true;
                diagnostics::error(&format!("Server adoption recovery failed: {error}"));
                steps.push(warning_step("adoption-recovery", "Server adoption recovery could not run", &error));
            }
        }

        match workspace_registry::recover_pending_duplicates() {
            Ok(report) => {
                let mut details = Vec::new();
                if report.completed > 0 { details.push(format!("Completed {} published duplicate(s) that had not yet been registered.", report.completed)); }
                if report.cleaned > 0 { details.push(format!("Cleaned {} interrupted duplicate staging location(s).", report.cleaned)); }
                if !report.issues.is_empty() { details.push(report.issues.join(" ")); }
                if details.is_empty() { details.push("No interrupted server duplicate requires recovery.".into()); }
                let details = details.join(" ");
                if report.issues.is_empty() {
                    steps.push(ready_step("duplicate-recovery", "Server duplicate state reconciled", &details));
                } else {
                    degraded = true;
                    diagnostics::error(&format!("Server duplicate recovery needs attention: {details}"));
                    steps.push(warning_step("duplicate-recovery", "Server duplicate needs attention", &details));
                }
            }
            Err(error) => {
                degraded = true;
                diagnostics::error(&format!("Server duplicate recovery failed: {error}"));
                steps.push(warning_step("duplicate-recovery", "Server duplicate recovery could not run", &error));
            }
        }

        match server_restore::recover_pending_restores() {
            Ok(report) => {
                let mut details = Vec::new();
                if report.recovered > 0 { details.push(format!("Reconciled {} interrupted server restore(s).", report.recovered)); }
                if !report.issues.is_empty() { details.push(report.issues.join(" ")); }
                if details.is_empty() { details.push("No interrupted server restore requires recovery.".into()); }
                let details = details.join(" ");
                if report.issues.is_empty() {
                    steps.push(ready_step("server-restore-recovery", "Server restore state reconciled", &details));
                } else {
                    degraded = true;
                    diagnostics::error(&format!("Server restore recovery needs attention: {details}"));
                    steps.push(warning_step("server-restore-recovery", "Server restore needs attention", &details));
                }
            }
            Err(error) => {
                degraded = true;
                diagnostics::error(&format!("Server restore recovery failed: {error}"));
                steps.push(warning_step("server-restore-recovery", "Server restore recovery could not run", &error));
            }
        }

        // New builds recover only workspace IDs recorded in pending-backups.json. To
        // preserve upgrade compatibility, one full legacy sweep is allowed until the
        // migration marker is committed successfully; after that startup is O(pending)
        // for backup staging rather than O(all registered servers).
        match backup_recovery::recover_pending() {
            Ok(report) => {
                let mut details = Vec::new();
                let mut issues = report.issues;
                if report.recovered_workspaces > 0 {
                    details.push(format!("Reconciled backup staging for {} interrupted workspace(s).", report.recovered_workspaces));
                }
                if report.removed_staging > 0 {
                    details.push(format!("Cleaned {} indexed backup staging director{}.", report.removed_staging, if report.removed_staging == 1 { "y" } else { "ies" }));
                }

                match backup_recovery::legacy_sweep_required() {
                    Ok(true) => match server_backups::recover_staging() {
                        Ok(removed) => match backup_recovery::mark_legacy_sweep_complete() {
                            Ok(()) => details.push(format!("Completed the one-time legacy backup staging sweep; cleaned {removed} legacy staging director{}.", if removed == 1 { "y" } else { "ies" })),
                            Err(error) => issues.push(format!("Legacy backup sweep completed but its migration marker could not be persisted: {error}")),
                        },
                        Err(error) => issues.push(format!("One-time legacy backup staging sweep failed: {error}")),
                    },
                    Ok(false) => {}
                    Err(error) => issues.push(format!("Could not inspect the backup recovery migration marker: {error}")),
                }

                if details.is_empty() && issues.is_empty() {
                    details.push("No indexed interrupted server backup staging requires cleanup.".into());
                }
                if !issues.is_empty() { details.push(issues.join(" ")); }
                let details = details.join(" ");
                if issues.is_empty() {
                    steps.push(ready_step("backup-recovery", "Backup staging reconciled", &details));
                } else {
                    degraded = true;
                    diagnostics::error(&format!("Server backup staging recovery needs attention: {details}"));
                    steps.push(warning_step("backup-recovery", "Backup staging needs attention", &details));
                }
            }
            Err(error) => {
                degraded = true;
                diagnostics::error(&format!("Indexed server backup staging recovery failed: {error}"));
                steps.push(warning_step("backup-recovery", "Backup staging needs attention", &error));
            }
        }

        match server_process_guard::reconcile_registered_process_markers() {
            Ok(result) => {
                let mut details = Vec::new();
                if result.stale_markers_cleared > 0 { details.push(format!("Cleared {} stale process marker(s).", result.stale_markers_cleared)); }
                if !result.running_servers.is_empty() { details.push(format!("Running in background: {}.", result.running_servers.join(", "))); }
                if !result.issues.is_empty() { details.push(result.issues.join(" ")); }
                if details.is_empty() { details.push("No registered LazyBuilder Paper process requires recovery attention.".into()); }
                let details = details.join(" ");
                if result.running_servers.is_empty() && result.issues.is_empty() {
                    steps.push(ready_step("server-process-reconciliation", "Background server state reconciled", &details));
                } else {
                    degraded = true;
                    diagnostics::info(&format!("Startup process reconciliation needs attention: {details}"));
                    steps.push(warning_step("server-process-reconciliation", "Background server state needs attention", &details));
                }
            }
            Err(error) => {
                degraded = true;
                diagnostics::error(&format!("Server process reconciliation failed: {error}"));
                steps.push(warning_step("server-process-reconciliation", "Background server state could not be checked", &error));
            }
        }

        match settings.as_ref().map(|value| value.remember_last_server) {
            Some(true) => match reopen_most_recent_workspace() {
                Ok(Some(name)) => steps.push(ready_step("remember-last-server", "Last server reopened", &format!("Reopened {name} without starting Paper."))),
                Ok(None) => steps.push(ready_step("remember-last-server", "No server reopened", "No available saved server could be reopened automatically.")),
                Err(error) => {
                    degraded = true;
                    diagnostics::error(&format!("Remember-last-server recovery failed: {error}"));
                    steps.push(warning_step("remember-last-server", "Last server could not be reopened", &error));
                }
            },
            Some(false) => steps.push(ready_step("remember-last-server", "Automatic server reopen disabled", "The server library will open without selecting a saved server.")),
            None => steps.push(warning_step("remember-last-server", "Automatic server reopen unavailable", "Launcher settings were unavailable during startup.")),
        }
    } else {
        degraded = true;
        steps.push(warning_step("creation-recovery", "Server creation recovery could not be checked", "The server library was unavailable, so LazyBuilder could not safely reconcile interrupted creations."));
        steps.push(warning_step("adoption-recovery", "Server adoption recovery could not be checked", "The server library was unavailable, so LazyBuilder could not safely reconcile interrupted adoptions."));
        steps.push(warning_step("duplicate-recovery", "Server duplicate recovery could not be checked", "The server library was unavailable, so LazyBuilder could not safely reconcile interrupted duplicates."));
        steps.push(warning_step("server-restore-recovery", "Server restore recovery could not be checked", "The server library was unavailable, so LazyBuilder could not safely reconcile interrupted restores."));
        steps.push(warning_step("backup-recovery", "Backup staging could not be checked", "The server library was unavailable, so LazyBuilder could not safely reconcile interrupted backup staging."));
        steps.push(warning_step("server-process-reconciliation", "Background server state could not be checked", "The server library was unavailable, so LazyBuilder could not safely inspect registered Paper process markers."));
        steps.push(warning_step("remember-last-server", "Last server could not be reopened", "The server library was unavailable."));
    }

    let report = StartupReport { ready: true, degraded, started_at_unix_seconds: started_at, completed_at_unix_seconds: now_unix_seconds(), runtime_temp_path: runtime_temp.as_ref().map(|path| path.display().to_string()), steps };
    diagnostics::info(if report.degraded { "LazyBuilder startup coordinator completed in degraded mode" } else { "LazyBuilder startup coordinator completed" });
    report
}

fn reopen_most_recent_workspace() -> Result<Option<String>, String> {
    for entry in workspace_registry::list()? {
        if !std::path::Path::new(&entry.path).is_dir() { continue; }
        match workspace_registry::activate(&entry.id) {
            Ok(active) => return Ok(Some(active.name)),
            Err(error) => diagnostics::info(&format!("Skipping remembered server {} during startup: {error}", entry.name)),
        }
    }
    Ok(None)
}

fn ready_step(key: &str, summary: &str, details: &str) -> StartupStep { StartupStep { key: key.into(), state: StartupStepState::Ready, summary: summary.into(), details: details.into() } }
fn warning_step(key: &str, summary: &str, details: &str) -> StartupStep { StartupStep { key: key.into(), state: StartupStepState::Warning, summary: summary.into(), details: details.into() } }
fn now_unix_seconds() -> u64 { SystemTime::now().duration_since(UNIX_EPOCH).map(|value| value.as_secs()).unwrap_or_default() }

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn startup_steps_preserve_machine_readable_keys() { let step = ready_step("workspace-registry", "ready", "details"); assert_eq!(step.key, "workspace-registry"); assert!(matches!(step.state, StartupStepState::Ready)); }
    #[test] fn warning_steps_are_explicitly_degraded() { let step = warning_step("runtime-environment", "warning", "details"); assert!(matches!(step.state, StartupStepState::Warning)); }
}
