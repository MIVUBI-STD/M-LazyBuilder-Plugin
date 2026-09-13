use crate::engine::paths;
use serde_json::Value;
use std::fs;
use std::path::{Path, PathBuf};

const DEFAULT_MAX_AUTO_SAVE_CHUNKS_PER_TICK: u32 = 12;
const MIN_MAX_AUTO_SAVE_CHUNKS_PER_TICK: u32 = 1;
const MAX_MAX_AUTO_SAVE_CHUNKS_PER_TICK: u32 = 200;

pub struct PaperPerformanceApplyResult {
    pub changed: bool,
    pub message: String,
}

#[derive(Clone, Copy)]
struct BuildPerformanceSettings {
    max_auto_save_chunks_per_tick: u32,
    disable_armor_stand_tick: bool,
    disable_armor_stand_collision_lookups: bool,
    optimize_explosions: bool,
    disable_mob_spawner_tick: bool,
}

pub fn apply_before_managed_start() -> Result<PaperPerformanceApplyResult, String> {
    let workspace = paths::workspace_root()?;
    let config_path = paths::lazybuilder_config_dir()?.join("server-manager.json");
    let server_directory = if config_path.is_file() {
        let text = fs::read_to_string(&config_path).map_err(|error| error.to_string())?;
        let value: Value = serde_json::from_str(&text).map_err(|error| error.to_string())?;
        value
            .get("serverDirectory")
            .and_then(Value::as_str)
            .filter(|value| !value.trim().is_empty())
            .unwrap_or("server")
            .to_string()
    } else {
        "server".into()
    };
    let relative = paths::safe_relative_path(&server_directory, "serverDirectory")?;
    let server_dir = workspace.join(relative);
    if !server_dir.starts_with(&workspace) {
        return Err("Paper performance configuration escaped the LazyBuilder workspace".into());
    }
    apply_before_start(&server_dir)
}

pub fn apply_before_start(server_dir: &Path) -> Result<PaperPerformanceApplyResult, String> {
    let target = server_dir.join("config").join("paper-world-defaults.yml");
    if !target.is_file() {
        return Ok(PaperPerformanceApplyResult {
            changed: false,
            message: "Paper performance config not generated yet; build optimizations will be applied after Paper has generated its config.".into(),
        });
    }

    let settings = load_settings()?;
    let original = fs::read_to_string(&target)
        .map_err(|error| format!("Failed to read {}: {error}", target.display()))?;
    let updated = patch_build_profile(&original, settings);

    if updated == original {
        return Ok(PaperPerformanceApplyResult {
            changed: false,
            message: "Paper build performance profile already configured.".into(),
        });
    }

    write_with_backup(&target, &updated)?;
    Ok(PaperPerformanceApplyResult {
        changed: true,
        message: "Paper build performance profile applied.".into(),
    })
}

fn load_settings() -> Result<BuildPerformanceSettings, String> {
    let path = paths::lazybuilder_config_dir()?.join("server-manager.json");
    let value = if path.is_file() {
        let text = fs::read_to_string(path).map_err(|error| error.to_string())?;
        serde_json::from_str::<Value>(&text).map_err(|error| error.to_string())?
    } else {
        Value::Null
    };

    let save_chunks = value
        .get("paperMaxAutoSaveChunksPerTick")
        .and_then(Value::as_u64)
        .unwrap_or(DEFAULT_MAX_AUTO_SAVE_CHUNKS_PER_TICK as u64) as u32;

    Ok(BuildPerformanceSettings {
        max_auto_save_chunks_per_tick: save_chunks.clamp(
            MIN_MAX_AUTO_SAVE_CHUNKS_PER_TICK,
            MAX_MAX_AUTO_SAVE_CHUNKS_PER_TICK,
        ),
        disable_armor_stand_tick: bool_setting(&value, "paperDisableArmorStandTick", true),
        disable_armor_stand_collision_lookups: bool_setting(
            &value,
            "paperDisableArmorStandCollisionLookups",
            true,
        ),
        optimize_explosions: bool_setting(&value, "paperOptimizeExplosions", true),
        disable_mob_spawner_tick: bool_setting(&value, "paperDisableMobSpawnerTick", true),
    })
}

fn bool_setting(value: &Value, key: &str, default: bool) -> bool {
    value.get(key).and_then(Value::as_bool).unwrap_or(default)
}

fn patch_build_profile(input: &str, settings: BuildPerformanceSettings) -> String {
    let mut output = input.to_string();
    output = patch_yaml_scalar(
        &output,
        &["chunks", "max-auto-save-chunks-per-tick"],
        &settings.max_auto_save_chunks_per_tick.to_string(),
    );
    output = patch_yaml_scalar(
        &output,
        &["entities", "armor-stands", "tick"],
        if settings.disable_armor_stand_tick { "false" } else { "true" },
    );
    output = patch_yaml_scalar(
        &output,
        &["entities", "armor-stands", "do-collision-entity-lookups"],
        if settings.disable_armor_stand_collision_lookups { "false" } else { "true" },
    );
    output = patch_yaml_scalar(
        &output,
        &["environment", "optimize-explosions"],
        if settings.optimize_explosions { "true" } else { "false" },
    );
    output = patch_yaml_scalar(
        &output,
        &["tick-rates", "mob-spawner"],
        if settings.disable_mob_spawner_tick { "-1" } else { "1" },
    );
    output
}

fn patch_yaml_scalar(input: &str, path: &[&str], value: &str) -> String {
    let trailing_newline = input.ends_with('\n');
    let mut lines: Vec<String> = input.lines().map(str::to_string).collect();
    ensure_path(&mut lines, path, value);
    join_lines(lines, trailing_newline || input.is_empty())
}

fn ensure_path(lines: &mut Vec<String>, path: &[&str], value: &str) {
    let mut start = 0usize;
    let mut end = lines.len();

    for (depth, key) in path.iter().enumerate() {
        let indent = depth * 2;
        let is_leaf = depth + 1 == path.len();
        let mut found = None;

        for index in start..end {
            let line = &lines[index];
            let trimmed = line.trim_start();
            if trimmed.is_empty() || trimmed.starts_with('#') {
                continue;
            }
            let current_indent = line.len() - trimmed.len();
            if current_indent < indent {
                break;
            }
            if current_indent == indent && trimmed.starts_with(&format!("{key}:")) {
                found = Some(index);
                break;
            }
        }

        if is_leaf {
            if let Some(index) = found {
                let comment = lines[index]
                    .split_once('#')
                    .map(|(_, suffix)| format!(" #{}", suffix))
                    .unwrap_or_default();
                lines[index] = format!("{}{key}: {value}{comment}", " ".repeat(indent));
            } else {
                lines.insert(end, format!("{}{key}: {value}", " ".repeat(indent)));
            }
            return;
        }

        let section_index = if let Some(index) = found {
            index
        } else {
            lines.insert(end, format!("{}{key}:", " ".repeat(indent)));
            end
        };

        start = section_index + 1;
        end = lines
            .iter()
            .enumerate()
            .skip(start)
            .find(|(_, line)| {
                let trimmed = line.trim_start();
                if trimmed.is_empty() || trimmed.starts_with('#') {
                    return false;
                }
                line.len() - trimmed.len() <= indent
            })
            .map(|(index, _)| index)
            .unwrap_or(lines.len());
    }
}

fn join_lines(lines: Vec<String>, trailing_newline: bool) -> String {
    let mut output = lines.join("\n");
    if trailing_newline {
        output.push('\n');
    }
    output
}

fn write_with_backup(path: &Path, content: &str) -> Result<(), String> {
    let backup = backup_path(path);
    let temporary = path.with_extension("yml.lazybuilder.tmp");
    fs::write(&temporary, content).map_err(|error| error.to_string())?;
    let _ = fs::remove_file(&backup);
    fs::copy(path, &backup)
        .map_err(|error| format!("Failed to back up {}: {error}", path.display()))?;
    match fs::rename(&temporary, path) {
        Ok(()) => Ok(()),
        Err(error) => {
            let _ = fs::remove_file(&temporary);
            Err(format!("Failed to update {}: {error}", path.display()))
        }
    }
}

fn backup_path(path: &Path) -> PathBuf {
    path.with_extension("yml.lazybuilder.previous")
}

#[cfg(test)]
mod tests {
    use super::{patch_build_profile, patch_yaml_scalar, BuildPerformanceSettings};

    fn defaults() -> BuildPerformanceSettings {
        BuildPerformanceSettings {
            max_auto_save_chunks_per_tick: 12,
            disable_armor_stand_tick: true,
            disable_armor_stand_collision_lookups: true,
            optimize_explosions: true,
            disable_mob_spawner_tick: true,
        }
    }

    #[test]
    fn patches_existing_nested_values_without_touching_neighbors() {
        let input = "chunks:\n  max-auto-save-chunks-per-tick: 24\nentities:\n  armor-stands:\n    do-collision-entity-lookups: true\n    tick: true\n  markers:\n    tick: true\nenvironment:\n  optimize-explosions: false\ntick-rates:\n  mob-spawner: 1\n";
        let output = patch_build_profile(input, defaults());
        assert!(output.contains("max-auto-save-chunks-per-tick: 12"));
        assert!(output.contains("do-collision-entity-lookups: false"));
        assert!(output.contains("    tick: false"));
        assert!(output.contains("markers:\n    tick: true"));
        assert!(output.contains("optimize-explosions: true"));
        assert!(output.contains("mob-spawner: -1"));
    }

    #[test]
    fn creates_missing_nested_path_without_rewriting_document() {
        let input = "_version: 31\ncollisions:\n  max-entity-collisions: 8\n";
        let output = patch_yaml_scalar(input, &["entities", "armor-stands", "tick"], "false");
        assert!(output.contains("collisions:\n  max-entity-collisions: 8\n"));
        assert!(output.contains("entities:\n  armor-stands:\n    tick: false"));
    }
}
