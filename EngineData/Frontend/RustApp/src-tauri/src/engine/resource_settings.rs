use crate::engine::paths;
use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};
use std::fs;
use std::path::PathBuf;
use sysinfo::System;

const MB: u64 = 1024 * 1024;
const MIN_SERVER_MEMORY_MB: u64 = 1024;

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ResourcePreset {
    pub name: String,
    pub max_memory_mb: u64,
    pub min_memory_mb: u64,
    pub cpu_threads: u32,
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
    pub current_cpu_threads: u32,
    pub current_preset: String,
    pub performance: ResourcePreset,
    pub boost: ResourcePreset,
    pub warning: String,
}

#[derive(Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ResourceUpdateRequest {
    pub max_memory_mb: u64,
    pub cpu_threads: u32,
    pub preset: String,
}

#[derive(Clone, Copy)]
pub struct RuntimeResources {
    pub min_memory_mb: u64,
    pub max_memory_mb: u64,
    pub cpu_threads: u32,
}

pub fn profile() -> Result<ServerResourceProfile, String> {
    let hardware = Hardware::detect();
    let config = read_config()?;
    let current_max = config_u64(&config, "maxMemoryMb").unwrap_or(4096);
    let current_min = config_u64(&config, "minMemoryMb").unwrap_or(1024);
    let current_cpu = config_u64(&config, "cpuThreads")
        .map(|value| value as u32)
        .unwrap_or(hardware.logical_processors)
        .clamp(1, hardware.logical_processors);
    let current_preset = config_string(&config, "resourcePreset").unwrap_or_else(|| "Custom".into());

    let performance = preset_for(&hardware, PresetKind::Performance);
    let boost = preset_for(&hardware, PresetKind::Boost);
    let warning = resource_warning(&hardware, current_max, current_cpu);

    Ok(ServerResourceProfile {
        total_memory_mb: hardware.total_memory_mb,
        reserved_system_memory_mb: hardware.reserved_system_memory_mb,
        safe_max_memory_mb: hardware.safe_max_memory_mb,
        logical_processors: hardware.logical_processors,
        current_max_memory_mb: current_max,
        current_min_memory_mb: current_min,
        current_cpu_threads: current_cpu,
        current_preset,
        performance,
        boost,
        warning,
    })
}

pub fn save(request: ResourceUpdateRequest) -> Result<ServerResourceProfile, String> {
    let hardware = Hardware::detect();
    let max_memory_mb = request.max_memory_mb.clamp(MIN_SERVER_MEMORY_MB, hardware.safe_max_memory_mb);
    let cpu_threads = request.cpu_threads.clamp(1, hardware.logical_processors);
    let min_memory_mb = recommended_min_memory(max_memory_mb);
    let preset = normalize_preset(&request.preset);

    let mut config = read_config()?;
    config.insert("maxMemoryMb".into(), Value::from(max_memory_mb));
    config.insert("minMemoryMb".into(), Value::from(min_memory_mb));
    config.insert("cpuThreads".into(), Value::from(cpu_threads));
    config.insert("resourcePreset".into(), Value::from(preset));
    write_config(&config)?;
    profile()
}

pub fn apply_preset(name: &str) -> Result<ServerResourceProfile, String> {
    let hardware = Hardware::detect();
    let preset = match name.trim().to_ascii_lowercase().as_str() {
        "performance" => preset_for(&hardware, PresetKind::Performance),
        "boost" => preset_for(&hardware, PresetKind::Boost),
        _ => return Err("Unknown resource preset. Use Performance or Boost.".into()),
    };
    save(ResourceUpdateRequest {
        max_memory_mb: preset.max_memory_mb,
        cpu_threads: preset.cpu_threads,
        preset: preset.name,
    })
}

pub fn runtime_resources(configured_min_memory_mb: u64, configured_max_memory_mb: u64) -> Result<RuntimeResources, String> {
    let hardware = Hardware::detect();
    let config = read_config()?;
    let max_memory_mb = configured_max_memory_mb.clamp(MIN_SERVER_MEMORY_MB, hardware.safe_max_memory_mb);
    let min_memory_mb = configured_min_memory_mb
        .max(MIN_SERVER_MEMORY_MB)
        .min(max_memory_mb);
    let cpu_threads = config_u64(&config, "cpuThreads")
        .map(|value| value as u32)
        .unwrap_or(hardware.logical_processors)
        .clamp(1, hardware.logical_processors);
    Ok(RuntimeResources { min_memory_mb, max_memory_mb, cpu_threads })
}

fn preset_for(hardware: &Hardware, kind: PresetKind) -> ResourcePreset {
    let (memory_ratio, cpu_ratio, name) = match kind {
        PresetKind::Performance => (0.50_f64, 0.75_f64, "Performance"),
        PresetKind::Boost => (0.65_f64, 0.90_f64, "Boost"),
    };
    let target_memory = ((hardware.total_memory_mb as f64) * memory_ratio).round() as u64;
    let target_memory = target_memory
        .max(MIN_SERVER_MEMORY_MB)
        .min(hardware.safe_max_memory_mb);
    let target_cpu = ((hardware.logical_processors as f64) * cpu_ratio).round() as u32;
    let target_cpu = target_cpu.clamp(1, hardware.logical_processors);

    ResourcePreset {
        name: name.into(),
        max_memory_mb: round_memory_step(target_memory),
        min_memory_mb: recommended_min_memory(target_memory),
        cpu_threads: target_cpu,
    }
}

fn resource_warning(hardware: &Hardware, max_memory_mb: u64, cpu_threads: u32) -> String {
    if max_memory_mb > hardware.safe_max_memory_mb {
        return format!(
            "Configured RAM exceeds the safe limit for this PC. LazyBuilder will clamp runtime RAM to {} MB so Windows keeps {} MB reserved.",
            hardware.safe_max_memory_mb, hardware.reserved_system_memory_mb
        );
    }
    if max_memory_mb + hardware.reserved_system_memory_mb > hardware.total_memory_mb {
        return "Configured RAM leaves too little memory for Windows and background applications.".into();
    }
    if cpu_threads > hardware.logical_processors {
        return "Configured CPU allocation exceeds the available logical processors.".into();
    }
    String::new()
}

fn recommended_min_memory(max_memory_mb: u64) -> u64 {
    (max_memory_mb / 2).clamp(1024, 4096).min(max_memory_mb)
}

fn round_memory_step(value: u64) -> u64 {
    let step = if value >= 8192 { 512 } else { 256 };
    ((value / step) * step).max(MIN_SERVER_MEMORY_MB)
}

fn normalize_preset(value: &str) -> String {
    match value.trim().to_ascii_lowercase().as_str() {
        "performance" => "Performance".into(),
        "boost" => "Boost".into(),
        _ => "Custom".into(),
    }
}

fn config_path() -> Result<PathBuf, String> {
    Ok(paths::lazybuilder_config_dir()?.join("server-manager.json"))
}

fn read_config() -> Result<Map<String, Value>, String> {
    let path = config_path()?;
    if !path.is_file() {
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).map_err(|error| error.to_string())?;
        }
        return Ok(Map::new());
    }
    let text = fs::read_to_string(&path).map_err(|error| error.to_string())?;
    match serde_json::from_str::<Value>(&text).map_err(|error| error.to_string())? {
        Value::Object(map) => Ok(map),
        _ => Err("server-manager.json must contain a JSON object".into()),
    }
}

fn write_config(config: &Map<String, Value>) -> Result<(), String> {
    let path = config_path()?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    let text = serde_json::to_string_pretty(config).map_err(|error| error.to_string())?;
    let temporary = path.with_extension("json.resources.tmp");
    fs::write(&temporary, text).map_err(|error| error.to_string())?;
    if path.exists() {
        let backup = path.with_extension("json.resources.previous");
        let _ = fs::remove_file(&backup);
        fs::rename(&path, &backup).map_err(|error| error.to_string())?;
        match fs::rename(&temporary, &path) {
            Ok(()) => {
                let _ = fs::remove_file(backup);
                Ok(())
            }
            Err(error) => {
                let _ = fs::rename(&backup, &path);
                Err(error.to_string())
            }
        }
    } else {
        fs::rename(temporary, path).map_err(|error| error.to_string())
    }
}

fn config_u64(config: &Map<String, Value>, key: &str) -> Option<u64> {
    config.get(key).and_then(Value::as_u64)
}

fn config_string(config: &Map<String, Value>, key: &str) -> Option<String> {
    config.get(key).and_then(Value::as_str).map(str::to_string)
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
        let reserved_by_ratio = if total_memory_mb <= 8192 {
            (total_memory_mb as f64 * 0.35) as u64
        } else if total_memory_mb <= 16384 {
            (total_memory_mb as f64 * 0.30) as u64
        } else if total_memory_mb <= 32768 {
            (total_memory_mb as f64 * 0.25) as u64
        } else {
            (total_memory_mb as f64 * 0.20) as u64
        };
        let minimum_reserve = if total_memory_mb <= 8192 {
            3072
        } else if total_memory_mb <= 16384 {
            4096
        } else if total_memory_mb <= 32768 {
            6144
        } else {
            8192
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
