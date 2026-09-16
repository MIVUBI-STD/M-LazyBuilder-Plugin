use crate::engine::{provisioning, server_health, workspace_registry};
use serde::Serialize;
use std::path::Path;

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerRepairItem {
    pub check_key: String,
    pub title: String,
    pub details: String,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerRepairPlan {
    pub workspace_id: String,
    pub workspace_name: String,
    pub can_repair: bool,
    pub blocked_reason: String,
    pub repairs: Vec<ServerRepairItem>,
    pub manual_actions: Vec<ServerRepairItem>,
    pub health: server_health::ServerHealthSnapshot,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerRepairResult {
    pub repaired_checks: Vec<String>,
    pub health: server_health::ServerHealthSnapshot,
}

pub fn plan(workspace_id: &str) -> Result<ServerRepairPlan, String> {
    let health = server_health::inspect(workspace_id)?;
    let mut repairs = Vec::new();
    let mut manual_actions = Vec::new();
    let mut blocked_reason = String::new();

    for check in &health.checks {
        if check.ready {
            continue;
        }
        match check.key.as_str() {
            "workspace-location" => {
                blocked_reason = "The server location is unavailable. Locate or reconnect the server folder before repair.".into();
                manual_actions.push(item(&check.key, "Locate server folder", &check.details));
            }
            "workspace-manifest" => {
                blocked_reason = "Workspace identity is missing or ambiguous. LazyBuilder will not regenerate server identity automatically.".into();
                manual_actions.push(item(&check.key, "Review workspace identity", &check.details));
            }
            "minecraft-eula" => {
                manual_actions.push(item(&check.key, "Accept Minecraft EULA", "EULA acceptance always requires explicit user confirmation."));
            }
            "workspace-config" => repairs.push(item(&check.key, "Repair workspace configuration", "Recreate LazyBuilder-owned runtime/configuration layout without replacing user world data.")),
            "java-runtime" => repairs.push(item(&check.key, "Repair Java 21 runtime", "Verify or reinstall the checksum-validated LazyBuilder-managed Java runtime.")),
            "paper-runtime" => repairs.push(item(&check.key, "Repair Paper runtime", "Provision the supported Paper runtime only because paper.jar is missing.")),
            "core-modules" => repairs.push(item(&check.key, "Repair LazyBuilder core modules", "Republish LazyBuilder-owned World Manager and Utilities Manager transactionally.")),
            _ if check.repairable => repairs.push(item(&check.key, &check.summary, &check.details)),
            _ => manual_actions.push(item(&check.key, &check.summary, &check.details)),
        }
    }

    if health.running {
        blocked_reason = "Stop the server before repairing runtime files.".into();
    }

    Ok(ServerRepairPlan {
        workspace_id: health.workspace_id.clone(),
        workspace_name: health.workspace_name.clone(),
        can_repair: !health.running && blocked_reason.is_empty() && !repairs.is_empty(),
        blocked_reason,
        repairs,
        manual_actions,
        health,
    })
}

pub fn execute_tracked<F>(workspace_id: &str, resource_dir: Option<&Path>, mut on_stage: F) -> Result<ServerRepairResult, String>
where
    F: FnMut(&str, &str, &str),
{
    let repair_plan = plan(workspace_id)?;
    if !repair_plan.blocked_reason.is_empty() {
        return Err(repair_plan.blocked_reason);
    }
    if repair_plan.repairs.is_empty() {
        return Err("No LazyBuilder-owned repair is currently required for this server".into());
    }

    let active = workspace_registry::current()?
        .ok_or_else(|| "Open this server before running repair.".to_string())?;
    if active.id != workspace_id {
        return Err("Open the selected server before running repair so LazyBuilder has one authoritative workspace context.".into());
    }

    let repaired_checks: Vec<String> = repair_plan.repairs.iter().map(|item| item.check_key.clone()).collect();
    provisioning::provision_active_tracked(resource_dir, |phase, status, details| {
        on_stage(phase, status, details);
    })?;

    let health = server_health::inspect(workspace_id)?;
    let unresolved_owned: Vec<&str> = health.checks.iter()
        .filter(|check| !check.ready && check.repairable)
        .map(|check| check.key.as_str())
        .collect();
    if !unresolved_owned.is_empty() {
        return Err(format!("Repair completed but these LazyBuilder-owned health checks still need attention: {}", unresolved_owned.join(", ")));
    }

    Ok(ServerRepairResult { repaired_checks, health })
}

fn item(key: &str, title: &str, details: &str) -> ServerRepairItem {
    ServerRepairItem { check_key: key.into(), title: title.into(), details: details.into() }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn repair_item_keeps_machine_readable_health_key() {
        let item = item("paper-runtime", "Repair Paper", "missing");
        assert_eq!(item.check_key, "paper-runtime");
    }
}
