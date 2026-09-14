use crate::engine::paths;
use serde::Serialize;
use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use std::path::PathBuf;

const MAX_LOG_TAIL_BYTES: u64 = 256 * 1024;
const MAX_LOG_LINES: usize = 300;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerLogTail {
    pub path: String,
    pub content: String,
    pub truncated: bool,
}

#[tauri::command]
pub fn server_log_tail(path: String) -> Result<ServerLogTail, String> {
    let workspace = canonical_or_normalized(paths::workspace_root()?);
    let log_root = canonical_or_normalized(workspace.join("server").join("logs"));
    let requested = if path.trim().is_empty() {
        log_root.join("latest.log")
    } else {
        PathBuf::from(path.trim())
    };
    let requested = canonical_or_normalized(requested);

    let is_log = requested
        .extension()
        .and_then(|value| value.to_str())
        .map(|value| value.eq_ignore_ascii_case("log")) == Some(true);
    let is_direct_server_log = requested.parent() == Some(log_root.as_path());
    if !requested.starts_with(&log_root) || !is_direct_server_log || !is_log {
        return Err("Refusing to read a log outside this server's log directory.".into());
    }
    if !requested.is_file() {
        return Ok(ServerLogTail {
            path: requested.display().to_string(),
            content: String::new(),
            truncated: false,
        });
    }

    let mut file = File::open(&requested).map_err(|error| error.to_string())?;
    let length = file.metadata().map_err(|error| error.to_string())?.len();
    let start = length.saturating_sub(MAX_LOG_TAIL_BYTES);
    file.seek(SeekFrom::Start(start)).map_err(|error| error.to_string())?;
    let mut bytes = Vec::with_capacity((length - start) as usize);
    file.read_to_end(&mut bytes).map_err(|error| error.to_string())?;
    let text = String::from_utf8_lossy(&bytes);
    let mut lines = text.lines().collect::<Vec<_>>();
    let truncated_by_lines = lines.len() > MAX_LOG_LINES;
    if truncated_by_lines {
        lines = lines.split_off(lines.len() - MAX_LOG_LINES);
    }

    Ok(ServerLogTail {
        path: requested.display().to_string(),
        content: lines.join("\n"),
        truncated: start > 0 || truncated_by_lines,
    })
}

fn canonical_or_normalized(path: PathBuf) -> PathBuf {
    path.canonicalize().unwrap_or_else(|_| {
        if path.is_absolute() {
            path
        } else {
            std::env::current_dir().map(|root| root.join(&path)).unwrap_or(path)
        }
    })
}
