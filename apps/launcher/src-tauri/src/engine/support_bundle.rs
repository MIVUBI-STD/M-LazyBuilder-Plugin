use crate::engine::diagnostics;
use serde::Serialize;
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use zip::write::SimpleFileOptions;
use zip::ZipWriter;

const MAX_LOG_FILES: usize = 5;
const MAX_LOG_FILE_BYTES: u64 = 1024 * 1024;

pub fn write_bundle<S, O, T>(
    destination: &Path,
    summary: &S,
    operations: &O,
    startup: &T,
    sensitive_paths: &[String],
) -> Result<PathBuf, String>
where
    S: Serialize,
    O: Serialize,
    T: Serialize,
{
    let parent = destination
        .parent()
        .ok_or_else(|| "Support bundle destination has no parent directory".to_string())?;
    fs::create_dir_all(parent).map_err(|error| format!("Could not prepare support bundle destination: {error}"))?;

    let staging = destination.with_extension("zip.incoming");
    if staging.exists() {
        fs::remove_file(&staging).map_err(|error| format!("Could not clear stale support bundle staging: {error}"))?;
    }

    let file = fs::File::create(&staging).map_err(|error| format!("Could not create support bundle: {error}"))?;
    let mut zip = ZipWriter::new(file);
    let options = SimpleFileOptions::default();

    write_json(&mut zip, options, "diagnostics.json", summary, sensitive_paths)?;
    write_json(&mut zip, options, "operations.json", operations, sensitive_paths)?;
    write_json(&mut zip, options, "startup.json", startup, sensitive_paths)?;

    let readme = "LazyBuilder Support Bundle\n\nThis archive was created locally for troubleshooting.\nIt is not uploaded automatically.\nIt intentionally excludes worlds, server configuration, plugin configuration/data, authentication material, and signing keys.\nAbsolute workspace/user-data paths are redacted where known.\n";
    zip.start_file("README.txt", options).map_err(|error| error.to_string())?;
    zip.write_all(readme.as_bytes()).map_err(|error| error.to_string())?;

    for (index, path) in diagnostics::launcher_log_paths().into_iter().take(MAX_LOG_FILES).enumerate() {
        if !path.is_file() {
            continue;
        }
        let metadata = fs::metadata(&path).map_err(|error| error.to_string())?;
        if metadata.len() > MAX_LOG_FILE_BYTES {
            return Err(format!("Launcher diagnostic log unexpectedly exceeds its bounded size: {}", path.display()));
        }
        let text = fs::read_to_string(&path).map_err(|error| format!("Could not read launcher diagnostic log: {error}"))?;
        let name = if index == 0 { "logs/launcher.log".to_string() } else { format!("logs/launcher.{index}.log") };
        zip.start_file(name, options).map_err(|error| error.to_string())?;
        zip.write_all(redact(&text, sensitive_paths).as_bytes()).map_err(|error| error.to_string())?;
    }

    zip.finish().map_err(|error| format!("Could not finalize support bundle: {error}"))?;

    if destination.exists() {
        let previous = destination.with_extension("zip.previous");
        let _ = fs::remove_file(&previous);
        fs::rename(destination, &previous).map_err(|error| format!("Could not stage existing support bundle: {error}"))?;
        match fs::rename(&staging, destination) {
            Ok(()) => {
                let _ = fs::remove_file(previous);
            }
            Err(error) => {
                let _ = fs::rename(&previous, destination);
                return Err(format!("Could not publish support bundle: {error}"));
            }
        }
    } else {
        fs::rename(&staging, destination).map_err(|error| format!("Could not publish support bundle: {error}"))?;
    }

    Ok(destination.to_path_buf())
}

fn write_json<T: Serialize>(
    zip: &mut ZipWriter<fs::File>,
    options: SimpleFileOptions,
    name: &str,
    value: &T,
    sensitive_paths: &[String],
) -> Result<(), String> {
    let text = serde_json::to_string_pretty(value).map_err(|error| error.to_string())?;
    zip.start_file(name, options).map_err(|error| error.to_string())?;
    zip.write_all(redact(&text, sensitive_paths).as_bytes()).map_err(|error| error.to_string())
}

fn redact(input: &str, sensitive_paths: &[String]) -> String {
    let mut replacements: Vec<(String, &'static str)> = Vec::new();
    for (key, marker) in [
        ("LOCALAPPDATA", "<LOCALAPPDATA>"),
        ("APPDATA", "<APPDATA>"),
        ("USERPROFILE", "<USERPROFILE>"),
        ("HOME", "<HOME>"),
    ] {
        if let Some(value) = std::env::var_os(key).and_then(|value| value.into_string().ok()) {
            if !value.trim().is_empty() {
                replacements.push((value, marker));
            }
        }
    }
    for value in sensitive_paths {
        if !value.trim().is_empty() {
            replacements.push((value.clone(), "<WORKSPACE>"));
        }
    }
    replacements.sort_by(|left, right| right.0.len().cmp(&left.0.len()));

    let mut output = input.to_string();
    for (value, marker) in replacements {
        output = replace_case_insensitive(&output, &value, marker);
    }
    output
}

fn replace_case_insensitive(input: &str, needle: &str, replacement: &str) -> String {
    if needle.is_empty() {
        return input.to_string();
    }
    let lower_input = input.to_ascii_lowercase();
    let lower_needle = needle.to_ascii_lowercase();
    let mut output = String::with_capacity(input.len());
    let mut cursor = 0usize;
    while let Some(relative) = lower_input[cursor..].find(&lower_needle) {
        let start = cursor + relative;
        output.push_str(&input[cursor..start]);
        output.push_str(replacement);
        cursor = start + needle.len();
    }
    output.push_str(&input[cursor..]);
    output
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn redaction_removes_workspace_paths_case_insensitively() {
        let value = redact("D:/Servers/Build/server/paper.jar d:/servers/build/logs", &["D:/Servers/Build".into()]);
        assert!(!value.to_ascii_lowercase().contains("d:/servers/build"));
        assert!(value.contains("<WORKSPACE>"));
    }
}
