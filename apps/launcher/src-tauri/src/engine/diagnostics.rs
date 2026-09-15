use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

const MAX_LOG_BYTES: u64 = 1024 * 1024;

pub fn launcher_log_path() -> Result<PathBuf, String> {
    let base = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())?;
    Ok(base.join("LazyBuilder").join("logs").join("launcher.log"))
}

pub fn log(level: &str, message: &str) {
    let Ok(path) = launcher_log_path() else { return; };
    let Some(parent) = path.parent() else { return; };
    if fs::create_dir_all(parent).is_err() { return; }
    rotate_if_needed(&path);
    let Ok(mut file) = OpenOptions::new().create(true).append(true).open(&path) else { return; };
    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|value| value.as_secs())
        .unwrap_or_default();
    let sanitized = message.replace('\r', " ").replace('\n', " ");
    let _ = writeln!(file, "[{timestamp}] {} {sanitized}", level.to_ascii_uppercase());
}

fn rotate_if_needed(path: &std::path::Path) {
    let Ok(metadata) = fs::metadata(path) else { return; };
    if metadata.len() < MAX_LOG_BYTES { return; }
    let previous = path.with_file_name("launcher.previous.log");
    let _ = fs::remove_file(&previous);
    let _ = fs::rename(path, previous);
}

pub fn info(message: &str) { log("INFO", message); }
pub fn error(message: &str) { log("ERROR", message); }
