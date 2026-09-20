use std::fs::{self, File, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Mutex, OnceLock};
use std::time::{SystemTime, UNIX_EPOCH};

const MAX_LOG_BYTES: u64 = 1024 * 1024;
const LOG_HISTORY_COUNT: usize = 4;
static NEXT_CORRELATION_ID: AtomicU64 = AtomicU64::new(1);
static LOG_WRITER: OnceLock<Mutex<Option<LogWriter>>> = OnceLock::new();

struct LogWriter {
    path: PathBuf,
    file: Option<File>,
    bytes_written: u64,
}

impl LogWriter {
    fn open() -> Result<Self, String> {
        let path = launcher_log_path()?;
        let parent = path.parent().ok_or_else(|| "Launcher log directory is unavailable".to_string())?;
        fs::create_dir_all(parent).map_err(|error| format!("Could not create Launcher log directory: {error}"))?;
        rotate_if_needed(&path);
        let bytes_written = fs::metadata(&path).map(|metadata| metadata.len()).unwrap_or(0);
        let file = OpenOptions::new().create(true).append(true).open(&path)
            .map_err(|error| format!("Could not open Launcher log: {error}"))?;
        Ok(Self { path, file: Some(file), bytes_written })
    }

    fn write_line(&mut self, line: &str) {
        if self.bytes_written >= MAX_LOG_BYTES {
            self.file.take();
            rotate_if_needed(&self.path);
            self.file = OpenOptions::new().create(true).append(true).open(&self.path).ok();
            self.bytes_written = 0;
        }
        let Some(file) = self.file.as_mut() else { return; };
        if writeln!(file, "{line}").is_ok() {
            self.bytes_written = self.bytes_written.saturating_add(line.len() as u64 + 1);
        }
    }
}

pub fn launcher_log_path() -> Result<PathBuf, String> {
    let base = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())?;
    Ok(base.join("LazyBuilder").join("logs").join("launcher.log"))
}

pub fn launcher_log_paths() -> Vec<PathBuf> {
    let Ok(primary) = launcher_log_path() else { return Vec::new(); };
    let mut paths = Vec::with_capacity(LOG_HISTORY_COUNT + 1);
    paths.push(primary.clone());
    for index in 1..=LOG_HISTORY_COUNT {
        paths.push(rotated_path(&primary, index));
    }
    paths
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
    let writer = LOG_WRITER.get_or_init(|| Mutex::new(None));
    let Ok(mut guard) = writer.lock() else { return; };
    if guard.is_none() {
        *guard = LogWriter::open().ok();
    }
    let Some(writer) = guard.as_mut() else { return; };
    let timestamp = SystemTime::now().duration_since(UNIX_EPOCH).map(|value| value.as_secs()).unwrap_or_default();
    let sanitized = message.replace('\r', " ").replace('\n', " ");
    let correlation = correlation_id.replace('\r', "").replace('\n', "").replace(' ', "");
    let line = format!("[{timestamp}] {} [{}] {sanitized}", level.to_ascii_uppercase(), if correlation.is_empty() { "-" } else { &correlation });
    writer.write_line(&line);
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
    #[test] fn launcher_log_set_is_bounded() { assert!(launcher_log_paths().len() <= LOG_HISTORY_COUNT + 1); }
}
