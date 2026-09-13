use crate::engine::paths;
use serde_yaml::Value;
use std::fs::{self, File};
use std::io::Read;
use std::path::{Path, PathBuf};
use zip::ZipArchive;

pub fn remove_invalid(problem_id: &str, jar_file_name: &str) -> Result<(), String> {
    let workspace = paths::workspace_root()?;
    let requested = validate_file_name(jar_file_name)?;
    let expected_problem_id = problem_id_from_file_name(&requested);
    if normalize_id(problem_id) != normalize_id(&expected_problem_id) {
        return Err("Problem plugin identity does not match the selected JAR.".into());
    }
    if looks_like_core_jar(&requested) {
        return Err("LazyBuilder core modules are repaired automatically and cannot be removed through Plugin Manager.".into());
    }

    let candidates = [
        workspace.join("server").join("plugins").join(&requested),
        workspace.join("tools").join("lazybuilder").join("disabled-plugins").join(&requested),
        workspace.join("server").join("plugins-disabled").join(&requested),
    ];
    let existing = candidates.into_iter().filter(|path| path.is_file()).collect::<Vec<_>>();
    if existing.is_empty() {
        return Ok(());
    }
    if existing.len() > 1 {
        return Err("Multiple problem JARs share this filename. Resolve the duplicate files manually before cleanup.".into());
    }

    let source = &existing[0];
    if plugin_metadata_is_valid(source)? {
        return Err("Selected JAR now contains valid plugin metadata and is no longer eligible for problem cleanup.".into());
    }

    let backups = workspace.join("tools").join("lazybuilder").join("plugin-backups");
    fs::create_dir_all(&backups).map_err(|error| error.to_string())?;
    let backup = backups.join(format!("{}.invalid.previous.jar", normalize_id(problem_id)));
    publish_backup(source, &backup)?;
    if let Err(error) = fs::remove_file(source) {
        let rollback = restore_from_backup(&backup, source);
        return match rollback {
            Ok(()) => Err(format!("Could not remove invalid plugin JAR; previous file was restored: {error}")),
            Err(rollback_error) => Err(format!(
                "Could not remove invalid plugin JAR: {error}; rollback also failed: {rollback_error}"
            )),
        };
    }
    Ok(())
}

fn validate_file_name(value: &str) -> Result<String, String> {
    let path = Path::new(value);
    let name = path.file_name().and_then(|value| value.to_str())
        .ok_or_else(|| "Invalid plugin filename.".to_string())?;
    if name != value || !name.to_ascii_lowercase().ends_with(".jar") {
        return Err("Problem cleanup accepts one JAR filename only.".into());
    }
    Ok(name.to_string())
}

fn plugin_metadata_is_valid(path: &Path) -> Result<bool, String> {
    let file = File::open(path).map_err(|error| error.to_string())?;
    let mut archive = match ZipArchive::new(file) {
        Ok(value) => value,
        Err(_) => return Ok(false),
    };
    let entry_name = if archive.file_names().any(|name| name == "plugin.yml") {
        "plugin.yml"
    } else if archive.file_names().any(|name| name == "paper-plugin.yml") {
        "paper-plugin.yml"
    } else {
        return Ok(false);
    };
    let mut entry = archive.by_name(entry_name).map_err(|error| error.to_string())?;
    let mut text = String::new();
    if entry.read_to_string(&mut text).is_err() {
        return Ok(false);
    }
    let parsed: Value = match serde_yaml::from_str(&text) {
        Ok(value) => value,
        Err(_) => return Ok(false),
    };
    let Some(mapping) = parsed.as_mapping() else { return Ok(false); };
    let name = mapping.get(Value::String("name".into())).and_then(Value::as_str).unwrap_or("").trim();
    let version = mapping.get(Value::String("version".into()));
    Ok(!name.is_empty() && version.is_some())
}

fn problem_id_from_file_name(file_name: &str) -> String {
    let stem = Path::new(file_name).file_stem().and_then(|value| value.to_str()).unwrap_or("plugin");
    format!("invalid-{}", normalize_id(stem))
}

fn looks_like_core_jar(file_name: &str) -> bool {
    let value = file_name.to_ascii_lowercase();
    value.starts_with("world-manager-") || value.starts_with("utilities-manager-")
}

fn normalize_id(value: &str) -> String {
    let normalized: String = value.trim().to_lowercase().chars()
        .filter(|ch| ch.is_ascii_alphanumeric() || *ch == '-' || *ch == '_')
        .map(|ch| if ch == '_' { '-' } else { ch }).collect();
    if normalized.is_empty() { "plugin".into() } else { normalized }
}

fn publish_backup(source: &Path, destination: &Path) -> Result<(), String> {
    if let Some(parent) = destination.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    let staged = destination.with_extension("jar.incoming");
    if staged.exists() {
        fs::remove_file(&staged).map_err(|error| error.to_string())?;
    }
    fs::copy(source, &staged).map_err(|error| error.to_string())?;
    if !files_equal(source, &staged)? {
        let _ = fs::remove_file(&staged);
        return Err("Invalid-plugin rollback backup verification failed.".into());
    }
    replace_file(&staged, destination)
}

fn replace_file(source: &Path, destination: &Path) -> Result<(), String> {
    if destination.exists() {
        let previous = destination.with_extension("swap.previous");
        let _ = fs::remove_file(&previous);
        fs::rename(destination, &previous).map_err(|error| error.to_string())?;
        match fs::rename(source, destination) {
            Ok(()) => { let _ = fs::remove_file(previous); Ok(()) }
            Err(error) => { let _ = fs::rename(&previous, destination); Err(error.to_string()) }
        }
    } else {
        fs::rename(source, destination).map_err(|error| error.to_string())
    }
}

fn restore_from_backup(backup: &Path, destination: &Path) -> Result<(), String> {
    if !backup.is_file() {
        return Err(format!("Plugin rollback backup is missing: {}", backup.display()));
    }
    if destination.exists() {
        fs::remove_file(destination).map_err(|error| error.to_string())?;
    }
    fs::copy(backup, destination).map_err(|error| error.to_string())?;
    if !files_equal(backup, destination)? {
        return Err("Plugin rollback verification failed.".into());
    }
    Ok(())
}

fn files_equal(left: &Path, right: &Path) -> Result<bool, String> {
    let left_meta = fs::metadata(left).map_err(|error| error.to_string())?;
    let right_meta = fs::metadata(right).map_err(|error| error.to_string())?;
    if left_meta.len() != right_meta.len() { return Ok(false); }
    let left_bytes = fs::read(left).map_err(|error| error.to_string())?;
    let right_bytes = fs::read(right).map_err(|error| error.to_string())?;
    Ok(left_bytes == right_bytes)
}
