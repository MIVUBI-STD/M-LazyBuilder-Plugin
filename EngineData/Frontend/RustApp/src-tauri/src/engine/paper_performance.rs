use crate::engine::{paths, server_config};
use std::fs;
use std::path::{Path, PathBuf};

const MAX_AUTO_SAVE_CHUNKS_PER_TICK: u32 = 12;

pub struct PaperPerformanceApplyResult {
    pub changed: bool,
    pub message: String,
}

pub fn apply_before_managed_start() -> Result<PaperPerformanceApplyResult, String> {
    let workspace = paths::workspace_root()?;
    let config = server_config::load()?;
    let relative = paths::safe_relative_path(&config.server_directory, "serverDirectory")?;
    let server_dir = workspace.join(relative);
    if !server_dir.starts_with(&workspace) {
        return Err("Paper performance configuration escaped the LazyBuilder workspace".into());
    }
    apply_before_start(&server_dir)
}

pub fn apply_before_start(server_dir: &Path) -> Result<PaperPerformanceApplyResult, String> {
    let config_dir = server_dir.join("config");
    let world_target = config_dir.join("paper-world-defaults.yml");
    let global_target = config_dir.join("paper-global.yml");
    let mut changed = false;
    let mut found_config = false;

    if world_target.is_file() {
        found_config = true;
        let original = fs::read_to_string(&world_target)
            .map_err(|error| format!("Failed to read {}: {error}", world_target.display()))?;
        let updated = patch_build_profile(&original);
        if updated != original {
            write_with_backup(&world_target, &updated)?;
            changed = true;
        }
    }

    if global_target.is_file() {
        found_config = true;
        let original = fs::read_to_string(&global_target)
            .map_err(|error| format!("Failed to read {}: {error}", global_target.display()))?;
        let updated = patch_shared_pc_global_profile(&original);
        if updated != original {
            write_with_backup(&global_target, &updated)?;
            changed = true;
        }
    }

    if !found_config {
        return Ok(PaperPerformanceApplyResult {
            changed: false,
            message: "Paper performance config not generated yet; shared-PC optimizations will be applied after Paper has generated its config.".into(),
        });
    }

    Ok(PaperPerformanceApplyResult {
        changed,
        message: if changed {
            "Paper shared-PC build performance profile applied.".into()
        } else {
            "Paper shared-PC build performance profile already configured.".into()
        },
    })
}

fn patch_build_profile(input: &str) -> String {
    let mut output = input.to_string();
    output = patch_yaml_scalar(
        &output,
        &["chunks", "max-auto-save-chunks-per-tick"],
        &MAX_AUTO_SAVE_CHUNKS_PER_TICK.to_string(),
    );
    output = patch_yaml_scalar(&output, &["entities", "armor-stands", "tick"], "false");
    output = patch_yaml_scalar(
        &output,
        &["entities", "armor-stands", "do-collision-entity-lookups"],
        "false",
    );
    output = patch_yaml_scalar(
        &output,
        &["entities", "behavior", "update-pathfinding-on-block-update"],
        "false",
    );
    output = patch_yaml_scalar(&output, &["environment", "optimize-explosions"], "true");
    output = patch_yaml_scalar(&output, &["tick-rates", "mob-spawner"], "-1");
    output
}

fn patch_shared_pc_global_profile(input: &str) -> String {
    patch_yaml_scalar(
        input,
        &["chunk-system", "worker-threads"],
        &shared_pc_chunk_workers().to_string(),
    )
}

fn shared_pc_chunk_workers() -> usize {
    let logical = std::thread::available_parallelism()
        .map(|value| value.get())
        .unwrap_or(4);
    match logical {
        0..=4 => 1,
        5..=8 => 1,
        9..=12 => 2,
        13..=16 => 2,
        17..=24 => 3,
        _ => 4,
    }
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
    let previous_active = path.with_extension("yml.lazybuilder.swap");
    let staged_backup = path.with_extension("yml.lazybuilder.backup.tmp");

    fs::write(&temporary, content).map_err(|error| error.to_string())?;
    let _ = fs::remove_file(&staged_backup);
    fs::copy(path, &staged_backup)
        .map_err(|error| format!("Failed to stage backup for {}: {error}", path.display()))?;

    replace_snapshot(&staged_backup, &backup)
        .map_err(|error| format!("Failed to publish backup for {}: {error}", path.display()))?;

    let _ = fs::remove_file(&previous_active);
    fs::rename(path, &previous_active)
        .map_err(|error| format!("Failed to stage active Paper config for replacement: {error}"))?;
    match fs::rename(&temporary, path) {
        Ok(()) => {
            let _ = fs::remove_file(previous_active);
            Ok(())
        }
        Err(error) => {
            let _ = fs::rename(&previous_active, path);
            let _ = fs::remove_file(&temporary);
            Err(format!("Failed to update {}: {error}", path.display()))
        }
    }
}

fn replace_snapshot(source: &Path, destination: &Path) -> Result<(), String> {
    if !destination.exists() {
        return fs::rename(source, destination).map_err(|error| error.to_string());
    }

    let previous = PathBuf::from(format!("{}.swap", destination.display()));
    let _ = fs::remove_file(&previous);
    fs::rename(destination, &previous).map_err(|error| error.to_string())?;
    match fs::rename(source, destination) {
        Ok(()) => {
            let _ = fs::remove_file(previous);
            Ok(())
        }
        Err(error) => {
            let _ = fs::rename(&previous, destination);
            Err(error.to_string())
        }
    }
}

fn backup_path(path: &Path) -> PathBuf {
    path.with_extension("yml.lazybuilder.previous")
}

#[cfg(test)]
mod tests {
    use super::{patch_build_profile, patch_shared_pc_global_profile, patch_yaml_scalar};

    #[test]
    fn patches_existing_nested_values_without_touching_neighbors() {
        let input = "chunks:\n  max-auto-save-chunks-per-tick: 24\nentities:\n  armor-stands:\n    do-collision-entity-lookups: true\n    tick: true\n  behavior:\n    update-pathfinding-on-block-update: true\n  markers:\n    tick: true\nenvironment:\n  optimize-explosions: false\ntick-rates:\n  mob-spawner: 1\n";
        let output = patch_build_profile(input);
        assert!(output.contains("max-auto-save-chunks-per-tick: 12"));
        assert!(output.contains("do-collision-entity-lookups: false"));
        assert!(output.contains("    tick: false"));
        assert!(output.contains("update-pathfinding-on-block-update: false"));
        assert!(output.contains("markers:\n    tick: true"));
        assert!(output.contains("optimize-explosions: true"));
        assert!(output.contains("mob-spawner: -1"));
    }

    #[test]
    fn patches_global_chunk_workers_without_touching_io_threads() {
        let input = "chunk-system:\n  io-threads: -1\n  worker-threads: -1\n";
        let output = patch_shared_pc_global_profile(input);
        assert!(output.contains("io-threads: -1"));
        assert!(!output.contains("worker-threads: -1"));
    }

    #[test]
    fn creates_missing_nested_path_without_rewriting_document() {
        let input = "_version: 31\ncollisions:\n  max-entity-collisions: 8\n";
        let output = patch_yaml_scalar(input, &["entities", "armor-stands", "tick"], "false");
        assert!(output.contains("collisions:\n  max-entity-collisions: 8\n"));
        assert!(output.contains("entities:\n  armor-stands:\n    tick: false"));
    }
}
