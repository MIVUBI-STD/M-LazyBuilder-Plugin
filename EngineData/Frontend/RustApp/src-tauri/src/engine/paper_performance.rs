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
            message: "Paper performance config not generated yet; save smoothing will be applied after Paper has generated its config.".into(),
        });
    }

    let desired = configured_max_auto_save_chunks_per_tick()?;
    let original = fs::read_to_string(&target).map_err(|error| {
        format!("Failed to read {}: {error}", target.display())
    })?;
    let updated = patch_max_auto_save_chunks_per_tick(&original, desired);
    if updated == original {
        return Ok(PaperPerformanceApplyResult {
            changed: false,
            message: format!("Paper save smoothing already configured at {desired} chunks/tick."),
        });
    }

    write_with_backup(&target, &updated)?;
    Ok(PaperPerformanceApplyResult {
        changed: true,
        message: format!("Paper save smoothing set to {desired} chunks/tick."),
    })
}

fn configured_max_auto_save_chunks_per_tick() -> Result<u32, String> {
    let path = paths::lazybuilder_config_dir()?.join("server-manager.json");
    if !path.is_file() {
        return Ok(DEFAULT_MAX_AUTO_SAVE_CHUNKS_PER_TICK);
    }
    let text = fs::read_to_string(path).map_err(|error| error.to_string())?;
    let value: Value = serde_json::from_str(&text).map_err(|error| error.to_string())?;
    let configured = value
        .get("paperMaxAutoSaveChunksPerTick")
        .and_then(Value::as_u64)
        .unwrap_or(DEFAULT_MAX_AUTO_SAVE_CHUNKS_PER_TICK as u64);
    Ok((configured as u32).clamp(
        MIN_MAX_AUTO_SAVE_CHUNKS_PER_TICK,
        MAX_MAX_AUTO_SAVE_CHUNKS_PER_TICK,
    ))
}

fn patch_max_auto_save_chunks_per_tick(input: &str, desired: u32) -> String {
    let mut lines: Vec<String> = input.lines().map(str::to_string).collect();
    let trailing_newline = input.ends_with('\n');
    let chunks_index = lines
        .iter()
        .position(|line| line.trim_end() == "chunks:" && !line.starts_with(char::is_whitespace));

    if let Some(chunks_index) = chunks_index {
        let section_end = lines
            .iter()
            .enumerate()
            .skip(chunks_index + 1)
            .find(|(_, line)| {
                let trimmed = line.trim();
                !trimmed.is_empty()
                    && !trimmed.starts_with('#')
                    && !line.starts_with(' ')
                    && !line.starts_with('\t')
            })
            .map(|(index, _)| index)
            .unwrap_or(lines.len());

        for line in &mut lines[chunks_index + 1..section_end] {
            let trimmed = line.trim_start();
            if trimmed.starts_with("max-auto-save-chunks-per-tick:") {
                let indent_len = line.len() - trimmed.len();
                let indent = &line[..indent_len];
                let comment = trimmed
                    .split_once('#')
                    .map(|(_, suffix)| format!(" #{}", suffix))
                    .unwrap_or_default();
                *line = format!("{indent}max-auto-save-chunks-per-tick: {desired}{comment}");
                return join_lines(lines, trailing_newline);
            }
        }

        lines.insert(
            chunks_index + 1,
            format!("  max-auto-save-chunks-per-tick: {desired}"),
        );
        return join_lines(lines, trailing_newline);
    }

    if !lines.is_empty() && !lines.last().is_some_and(|line| line.trim().is_empty()) {
        lines.push(String::new());
    }
    lines.push("chunks:".into());
    lines.push(format!("  max-auto-save-chunks-per-tick: {desired}"));
    join_lines(lines, true)
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
    fs::copy(path, &backup).map_err(|error| {
        format!("Failed to back up {}: {error}", path.display())
    })?;
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
    use super::patch_max_auto_save_chunks_per_tick;

    #[test]
    fn updates_existing_chunks_value_without_touching_other_settings() {
        let input = "_version: 31\nchunks:\n  auto-save-interval: default\n  max-auto-save-chunks-per-tick: 24\ncollisions:\n  max-entity-collisions: 8\n";
        let output = patch_max_auto_save_chunks_per_tick(input, 12);
        assert!(output.contains("  max-auto-save-chunks-per-tick: 12\n"));
        assert!(output.contains("collisions:\n  max-entity-collisions: 8\n"));
    }

    #[test]
    fn inserts_missing_value_only_inside_chunks_section() {
        let input = "_version: 31\nchunks:\n  auto-save-interval: default\ncollisions:\n  max-entity-collisions: 8\n";
        let output = patch_max_auto_save_chunks_per_tick(input, 12);
        assert!(output.contains("chunks:\n  max-auto-save-chunks-per-tick: 12\n  auto-save-interval: default\n"));
    }

    #[test]
    fn appends_chunks_section_when_missing() {
        let input = "_version: 31\ncollisions:\n  max-entity-collisions: 8\n";
        let output = patch_max_auto_save_chunks_per_tick(input, 12);
        assert!(output.ends_with("chunks:\n  max-auto-save-chunks-per-tick: 12\n"));
    }
}
