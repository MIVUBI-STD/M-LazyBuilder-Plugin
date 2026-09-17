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
    pub health: server_health::ServerReadinessSnapshot,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerRepairResult {
    pub repaired_checks: Vec<String>,
    pub health: server_health::ServerReadinessSnapshot,
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
                blocked_reason = "The server folder is unavailable. Locate or reconnect it before running repair.".into();
                manual_actions.push(item(&check.key, "Locate server folder", &check.details));
            }
            "workspace-manifest" => {
                blocked_reason = "This server's identity needs attention. LazyBuilder will not change it automatically.".into();
                manual_actions.push(item(&check.key, "Review server identity", &check.details));
            }
            "minecraft-eula" => {
                manual_actions.push(item(&check.key, "Accept Minecraft EULA", "Review and accept the Minecraft EULA before starting this server."));
            }
            "workspace-config" => repairs.push(item(&check.key, "Repair server configuration", "Restore the LazyBuilder files needed to manage this server. Worlds and plugin data will be kept.")),
            "java-runtime" => repairs.push(item(&check.key, "Repair Java 21", "Restore the Java 21 runtime used by this server.")),
            "paper-runtime" => repairs.push(item(&check.key, "Repair Paper", "Restore the supported Paper server runtime.")),
            "core-modules" => repairs.push(item(&check.key, "Repair LazyBuilder components", "Restore the LazyBuilder components required by this server.")),
            _ if check.repairable => repairs.push(item(&check.key, &check.summary, &check.details)),
            _ => manual_actions.push(item(&check.key, &check.summary, &check.details)),
        }
    }

    if health.running {
        blocked_reason = "Stop the server before repairing its components.".into();
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
        return Err("No automatic repair is currently needed for this server.".into());
    }

    let active = workspace_registry::current()?
        .ok_or_else(|| "Open this server before running repair.".to_string())?;
    if active.id != workspace_id {
        return Err("Open this server in LazyBuilder before running repair.".into());
    }

    let repaired_checks: Vec<String> = repair_plan.repairs.iter().map(|item| item.check_key.clone()).collect();
    provisioning::provision_active_tracked(resource_dir, |phase, status, details| {
        on_stage(phase, status, details);
    })?;

    let health = server_health::inspect(workspace_id)?;
    let unresolved_owned: Vec<&str> = health.checks.iter()
        .filter(|check| !check.ready && check.repairable)
        .map(|check| check.summary.as_str())
        .collect();
    if !unresolved_owned.is_empty() {
        return Err(format!("Repair finished, but these items still need attention: {}", unresolved_owned.join(", ")));
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
    fn repair_item_keeps_check_identity() {
        let value = item("paper-runtime", "Repair Paper", "details");
        assert_eq!(value.check_key, "paper-runtime");
    }
}
