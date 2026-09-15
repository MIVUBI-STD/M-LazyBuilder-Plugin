use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

const MAX_LOG_BYTES: u64 = 1024 * 1024;
const LOG_HISTORY_COUNT: usize = 4;
static NEXT_CORRELATION_ID: AtomicU64 = AtomicU64::new(1);

pub fn launcher_log_path() -> Result<PathBuf, String> {
    let base = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())?;
    Ok(base.join("LazyBuilder").join("logs").join("launcher.log"))
}

pub fn new_correlation_id(scope: &str) -> String {
    let sequence = NEXT_CORRELATION_ID.fetch_add(1, Ordering::Relaxed);
    let millis = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|value| value.as_millis())
        .unwrap_or_default();
    let safe_scope: String = scope.chars().filter(|value| value.is_ascii_alphanumeric() || *value == '-').take(24).collect();
    format!("{}-{millis}-{sequence}", if safe_scope.is_empty() { "event" } else { &safe_scope })
}

pub fn log(level: &str, message: &str) { log_with_context(level, "-", message); }

pub fn log_with_context(level: &str, correlation_id: &str, message: &str) {
    let Ok(path) = launcher_log_path() else { return; };
    let Some(parent) = path.parent() else { return; };
    if fs::create_dir_all(parent).is_err() { return; }
    rotate_if_needed(&path);
    let Ok(mut file) = OpenOptions::new().create(true).append(true).open(&path) else { return; };
    let timestamp = SystemTime::now().duration_since(UNIX_EPOCH).map(|value| value.as_secs()).unwrap_or_default();
    let sanitized = message.replace('\r', " ").replace('\n', " ");
    let correlation = correlation_id.replace('\r', "").replace('\n', "").replace(' ', "");
    let _ = writeln!(file, "[{timestamp}] {} [{}] {sanitized}", level.to_ascii_uppercase(), if correlation.is_empty() { "-" } else { &correlation });
}

fn rotate_if_needed(path: &Path) {
    let Ok(metadata) = fs::metadata(path) else { return; };
    if metadata.len() < MAX_LOG_BYTES { return; }
    let _ = fs::remove_file(rotated_path(path, LOG_HISTORY_COUNT));
    for index in (1..LOG_HISTORY_COUNT).rev() {
        let source = rotated_path(path, index);
        let target = rotated_path(path, index + 1);
        if source.exists() { let _ = fs::rename(source, target); }
    }
    let _ = fs::rename(path, rotated_path(path, 1));
}

fn rotated_path(path: &Path, index: usize) -> PathBuf { path.with_file_name(format!("launcher.{index}.log")) }

pub fn info(message: &str) { log("INFO", message); }
pub fn error(message: &str) { log("ERROR", message); }
pub fn info_with_context(correlation_id: &str, message: &str) { log_with_context("INFO", correlation_id, message); }
pub fn error_with_context(correlation_id: &str, message: &str) { log_with_context("ERROR", correlation_id, message); }

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn correlation_ids_are_non_empty_and_scoped() { let id = new_correlation_id("operation"); assert!(id.starts_with("operation-")); }
    #[test] fn rotated_names_are_bounded() { assert_eq!(rotated_path(Path::new("C:/logs/launcher.log"), 4).file_name().unwrap(), "launcher.4.log"); }
}
