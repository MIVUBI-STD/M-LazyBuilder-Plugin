use crate::engine::server_config;
use serde::{Deserialize, Serialize};
use std::sync::OnceLock;
use sysinfo::System;

const MB: u64 = 1024 * 1024;
const MIN_SERVER_MEMORY_MB: u64 = 1024;
const PERFORMANCE_CAP_MB: u64 = 4096;
const BOOST_CAP_MB: u64 = 6144;

static HARDWARE_CACHE: OnceLock<Hardware> = OnceLock::new();

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ResourcePreset {
    pub name: String,
    pub max_memory_mb: u64,
    pub min_memory_mb: u64,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerResourceProfile {
    pub total_memory_mb: u64,
    pub reserved_system_memory_mb: u64,
    pub safe_max_memory_mb: u64,
    pub logical_processors: u32,
    pub current_max_memory_mb: u64,
    pub current_min_memory_mb: u64,
    pub current_preset: String,
    pub performance: ResourcePreset,
    pub boost: ResourcePreset,
    pub warning: String,
}

#[derive(Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ResourceUpdateRequest {
    pub max_memory_mb: u64,
    pub preset: String,
}

#[derive(Clone, Copy)]
pub struct RuntimeResources {
    pub min_memory_mb: u64,
    pub max_memory_mb: u64,
    /// Temporary internal seam for the existing command builder. Always None:
    /// CPU scheduling is no longer configurable and remains OS/JVM managed.
    pub cpu_threads: Option<u32>,
}

pub fn profile() -> Result<ServerResourceProfile, String> {
    let hardware = hardware();
    let config = server_config::load()?;
    let configured_max = config.max_memory_mb;
    let current_max = configured_max.clamp(MIN_SERVER_MEMORY_MB, hardware.safe_max_memory_mb);
    let current_min = config
        .min_memory_mb
        .clamp(MIN_SERVER_MEMORY_MB, recommended_min_memory(current_max));
    let current_preset = normalize_preset(&config.resource_preset);

    let performance = preset_for(hardware, PresetKind::Performance);
    let boost = preset_for(hardware, PresetKind::Boost);
    let warning = resource_warning(hardware, configured_max);

    Ok(ServerResourceProfile {
        total_memory_mb: hardware.total_memory_mb,
        reserved_system_memory_mb: hardware.reserved_system_memory_mb,
        safe_max_memory_mb: hardware.safe_max_memory_mb,
        logical_processors: hardware.logical_processors,
        current_max_memory_mb: current_max,
        current_min_memory_mb: current_min,
        current_preset,
        performance,
        boost,
        warning,
    })
}

pub fn save(request: ResourceUpdateRequest) -> Result<ServerResourceProfile, String> {
    let hardware = hardware();
    let max_memory_mb = request
        .max_memory_mb
        .clamp(MIN_SERVER_MEMORY_MB, hardware.safe_max_memory_mb);
    let min_memory_mb = recommended_min_memory(max_memory_mb);
    let preset = normalize_preset(&request.preset);

    let mut config = server_config::load()?;
    config.max_memory_mb = max_memory_mb;
    config.min_memory_mb = min_memory_mb;
    config.resource_preset = preset;
    server_config::save(&config)?;
    profile()
}

pub fn apply_preset(name: &str) -> Result<ServerResourceProfile, String> {
    let hardware = hardware();
    let preset = match name.trim().to_ascii_lowercase().as_str() {
        "performance" => preset_for(hardware, PresetKind::Performance),
        "boost" => preset_for(hardware, PresetKind::Boost),
        _ => return Err("Unknown resource preset. Use Performance or Boost.".into()),
    };
    save(ResourceUpdateRequest {
        max_memory_mb: preset.max_memory_mb,
        preset: preset.name,
    })
}

pub fn runtime_resources() -> Result<RuntimeResources, String> {
    let hardware = hardware();
    let config = server_config::load()?;
    let max_memory_mb = config
        .max_memory_mb
        .clamp(MIN_SERVER_MEMORY_MB, hardware.safe_max_memory_mb);
    let min_memory_mb = config
        .min_memory_mb
        .clamp(MIN_SERVER_MEMORY_MB, recommended_min_memory(max_memory_mb));
    Ok(RuntimeResources {
        min_memory_mb,
        max_memory_mb,
        cpu_threads: None,
    })
}

fn preset_for(hardware: &Hardware, kind: PresetKind) -> ResourcePreset {
    let (target_memory, name) = match kind {
        PresetKind::Performance => (performance_memory_target(hardware), "Performance"),
        PresetKind::Boost => (boost_memory_target(hardware), "Boost"),
    };

    let target_memory = round_memory_step(
        target_memory
            .max(MIN_SERVER_MEMORY_MB)
            .min(hardware.safe_max_memory_mb),
    );

    ResourcePreset {
        name: name.into(),
        max_memory_mb: target_memory,
        min_memory_mb: recommended_min_memory(target_memory),
    }
}

fn performance_memory_target(hardware: &Hardware) -> u64 {
    let total = hardware.total_memory_mb;
    let target = if total <= 8192 {
        2048
    } else if total <= 16384 {
        3072
    } else {
        PERFORMANCE_CAP_MB
    };
    target.min(hardware.safe_max_memory_mb)
}

fn boost_memory_target(hardware: &Hardware) -> u64 {
    let total = hardware.total_memory_mb;
    let target = if total <= 8192 {
        3072
    } else if total <= 16384 {
        4096
    } else {
        BOOST_CAP_MB
    };
    target.min(hardware.safe_max_memory_mb)
}

fn resource_warning(hardware: &Hardware, max_memory_mb: u64) -> String {
    if max_memory_mb > hardware.safe_max_memory_mb {
        return format!(
            "Configured RAM exceeds the safe limit for this PC. LazyBuilder will clamp runtime RAM to {} MB so Windows, Minecraft and background applications keep about {} MB available.",
            hardware.safe_max_memory_mb, hardware.reserved_system_memory_mb
        );
    }
    if max_memory_mb + hardware.reserved_system_memory_mb > hardware.total_memory_mb {
        return "Configured RAM leaves too little memory for Windows, Minecraft and background applications.".into();
    }
    String::new()
}

fn recommended_min_memory(max_memory_mb: u64) -> u64 {
    match max_memory_mb {
        0..=4096 => 1024,
        4097..=6144 => 1536,
        _ => 2048,
    }
    .min(max_memory_mb)
}

fn round_memory_step(value: u64) -> u64 {
    let step = 256;
    ((value / step) * step).max(MIN_SERVER_MEMORY_MB)
}

fn normalize_preset(value: &str) -> String {
    match value.trim().to_ascii_lowercase().as_str() {
        "performance" => "Performance".into(),
        "boost" => "Boost".into(),
        _ => "Custom".into(),
    }
}

fn hardware() -> &'static Hardware {
    HARDWARE_CACHE.get_or_init(Hardware::detect)
}

struct Hardware {
    total_memory_mb: u64,
    reserved_system_memory_mb: u64,
    safe_max_memory_mb: u64,
    logical_processors: u32,
}

impl Hardware {
    fn detect() -> Self {
        let system = System::new_all();
        let total_memory_mb = (system.total_memory() / MB).max(2048);
        let logical_processors = system.cpus().len().max(1) as u32;

        // Reserve memory for the real desktop workload, not just the Paper process.
        // Builders commonly run Windows, Minecraft and creative tools alongside LazyBuilder.
        let reserved_by_ratio = if total_memory_mb <= 8192 {
            (total_memory_mb as f64 * 0.55) as u64
        } else if total_memory_mb <= 16384 {
            (total_memory_mb as f64 * 0.45) as u64
        } else if total_memory_mb <= 32768 {
            (total_memory_mb as f64 * 0.35) as u64
        } else {
            (total_memory_mb as f64 * 0.30) as u64
        };
        let minimum_reserve = if total_memory_mb <= 8192 {
            5120
        } else if total_memory_mb <= 16384 {
            7168
        } else if total_memory_mb <= 32768 {
            10240
        } else {
            12288
        };
        let reserved_system_memory_mb = reserved_by_ratio
            .max(minimum_reserve)
            .min(total_memory_mb.saturating_sub(MIN_SERVER_MEMORY_MB));
        let safe_max_memory_mb = total_memory_mb
            .saturating_sub(reserved_system_memory_mb)
            .max(MIN_SERVER_MEMORY_MB);
        Self {
            total_memory_mb,
            reserved_system_memory_mb,
            safe_max_memory_mb,
            logical_processors,
        }
    }
}

enum PresetKind {
    Performance,
    Boost,
}
