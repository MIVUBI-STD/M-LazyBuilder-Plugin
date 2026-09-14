use crate::engine::server_config;
use serde::{Deserialize, Serialize};
use std::sync::OnceLock;
use sysinfo::System;

const MB: u64 = 1024 * 1024;
const MIN_SERVER_MEMORY_MB: u64 = 1024;
const RECOMMENDED_CAP_MB: u64 = 4096;
const MIN_HOST_RESERVE_MB: u64 = 2048;

static HARDWARE_CACHE: OnceLock<Hardware> = OnceLock::new();

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerResourceProfile {
    pub total_memory_mb: u64,
    pub safe_max_memory_mb: u64,
    pub current_max_memory_mb: u64,
    pub current_min_memory_mb: u64,
    pub recommended_max_memory_mb: u64,
    pub warning: String,
}

#[derive(Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ResourceUpdateRequest {
    pub max_memory_mb: u64,
}

#[derive(Clone, Copy)]
pub struct RuntimeResources {
    pub min_memory_mb: u64,
    pub max_memory_mb: u64,
}

pub fn profile() -> Result<ServerResourceProfile, String> {
    let hardware = hardware();
    let config = server_config::load()?;
    let current_max = config
        .max_memory_mb
        .clamp(MIN_SERVER_MEMORY_MB, hardware.safe_max_memory_mb);
    Ok(ServerResourceProfile {
        total_memory_mb: hardware.total_memory_mb,
        safe_max_memory_mb: hardware.safe_max_memory_mb,
        current_max_memory_mb: current_max,
        current_min_memory_mb: MIN_SERVER_MEMORY_MB.min(current_max),
        recommended_max_memory_mb: hardware.recommended_max_memory_mb,
        warning: resource_warning(hardware, config.max_memory_mb),
    })
}

pub fn save(request: ResourceUpdateRequest) -> Result<ServerResourceProfile, String> {
    let hardware = hardware();
    let mut config = server_config::load()?;
    config.max_memory_mb = request
        .max_memory_mb
        .clamp(MIN_SERVER_MEMORY_MB, hardware.safe_max_memory_mb);
    config.min_memory_mb = MIN_SERVER_MEMORY_MB.min(config.max_memory_mb);
    server_config::save(&config)?;
    profile()
}

pub fn runtime_resources() -> Result<RuntimeResources, String> {
    let hardware = hardware();
    let config = server_config::load()?;
    let max_memory_mb = config
        .max_memory_mb
        .clamp(MIN_SERVER_MEMORY_MB, hardware.safe_max_memory_mb);
    Ok(RuntimeResources {
        min_memory_mb: MIN_SERVER_MEMORY_MB.min(max_memory_mb),
        max_memory_mb,
    })
}

fn resource_warning(hardware: &Hardware, configured_max_memory_mb: u64) -> String {
    if configured_max_memory_mb > hardware.safe_max_memory_mb {
        return format!(
            "Configured RAM is higher than the safe limit for this PC. LazyBuilder will use at most {} MB.",
            hardware.safe_max_memory_mb
        );
    }
    String::new()
}

fn hardware() -> &'static Hardware {
    HARDWARE_CACHE.get_or_init(Hardware::detect)
}

struct Hardware {
    total_memory_mb: u64,
    safe_max_memory_mb: u64,
    recommended_max_memory_mb: u64,
}

impl Hardware {
    fn detect() -> Self {
        let system = System::new_all();
        let total_memory_mb = (system.total_memory() / MB).max(2048);

        // Keep one simple safety rule: reserve the larger of 2 GB or 25% of host RAM
        // for Windows, Minecraft, and other applications. Do not attempt to tune CPU or
        // predict workload-specific memory needs before runtime evidence exists.
        let reserve_mb = MIN_HOST_RESERVE_MB
            .max(total_memory_mb / 4)
            .min(total_memory_mb.saturating_sub(MIN_SERVER_MEMORY_MB));
        let safe_max_memory_mb = total_memory_mb
            .saturating_sub(reserve_mb)
            .max(MIN_SERVER_MEMORY_MB);
        let recommended_max_memory_mb = RECOMMENDED_CAP_MB
            .min(safe_max_memory_mb)
            .max(MIN_SERVER_MEMORY_MB);

        Self {
            total_memory_mb,
            safe_max_memory_mb,
            recommended_max_memory_mb,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn recommendation_never_exceeds_safe_max() {
        let hardware = Hardware {
            total_memory_mb: 8192,
            safe_max_memory_mb: 6144,
            recommended_max_memory_mb: 4096,
        };
        assert!(hardware.recommended_max_memory_mb <= hardware.safe_max_memory_mb);
    }
}
