use crate::engine::paths;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct JfrStatus {
    pub available: bool,
    pub running: bool,
    pub pid: Option<u32>,
    pub recording_path: String,
    pub message: String,
}

#[derive(Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JfrStartRequest {
    pub duration_seconds: u64,
    pub max_size_mb: u64,
}

#[derive(Deserialize)]
struct ProcessMarker {
    pid: u32,
}

pub fn status() -> Result<JfrStatus, String> {
    let pid = managed_pid()?;
    let Some(pid) = pid else {
        return Ok(JfrStatus {
            available: locate_jcmd().is_ok(),
            running: false,
            pid: None,
            recording_path: String::new(),
            message: "Paper is not running.".into(),
        });
    };
    let jcmd = match locate_jcmd() {
        Ok(path) => path,
        Err(error) => {
            return Ok(JfrStatus {
                available: false,
                running: false,
                pid: Some(pid),
                recording_path: String::new(),
                message: error,
            })
        }
    };
    let output = Command::new(jcmd)
        .arg(pid.to_string())
        .args(["JFR.check"])
        .output()
        .map_err(|error| format!("Failed to query JFR: {error}"))?;
    let text = combined_output(&output.stdout, &output.stderr);
    let running = output.status.success() && !text.to_ascii_lowercase().contains("no available recordings");
    Ok(JfrStatus {
        available: true,
        running,
        pid: Some(pid),
        recording_path: latest_recording_path()?.unwrap_or_default(),
        message: text.trim().to_string(),
    })
}

pub fn start(request: JfrStartRequest) -> Result<JfrStatus, String> {
    let pid = managed_pid()?.ok_or_else(|| "Paper is not running.".to_string())?;
    let duration = request.duration_seconds.clamp(30, 3600);
    let max_size = request.max_size_mb.clamp(16, 1024);
    let jcmd = locate_jcmd()?;
    let directory = diagnostics_dir()?;
    fs::create_dir_all(&directory).map_err(|error| error.to_string())?;
    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|value| value.as_secs())
        .unwrap_or(0);
    let destination = directory.join(format!("paper-{timestamp}.jfr"));
    let output = Command::new(jcmd)
        .arg(pid.to_string())
        .arg("JFR.start")
        .arg("name=LazyBuilder")
        .arg("settings=default")
        .arg(format!("duration={duration}s"))
        .arg(format!("maxsize={max_size}M"))
        .arg(format!("filename={}", destination.display()))
        .output()
        .map_err(|error| format!("Failed to start JFR: {error}"))?;
    let text = combined_output(&output.stdout, &output.stderr);
    if !output.status.success() {
        return Err(format!("JFR start failed: {}", text.trim()));
    }
    Ok(JfrStatus {
        available: true,
        running: true,
        pid: Some(pid),
        recording_path: destination.display().to_string(),
        message: text.trim().to_string(),
    })
}

pub fn stop() -> Result<JfrStatus, String> {
    let pid = managed_pid()?.ok_or_else(|| "Paper is not running.".to_string())?;
    let jcmd = locate_jcmd()?;
    let output = Command::new(jcmd)
        .arg(pid.to_string())
        .args(["JFR.stop", "name=LazyBuilder"])
        .output()
        .map_err(|error| format!("Failed to stop JFR: {error}"))?;
    let text = combined_output(&output.stdout, &output.stderr);
    if !output.status.success() {
        return Err(format!("JFR stop failed: {}", text.trim()));
    }
    Ok(JfrStatus {
        available: true,
        running: false,
        pid: Some(pid),
        recording_path: latest_recording_path()?.unwrap_or_default(),
        message: text.trim().to_string(),
    })
}

fn managed_pid() -> Result<Option<u32>, String> {
    let path = paths::lazybuilder_cache_dir()?.join("server-process.json");
    if !path.is_file() {
        return Ok(None);
    }
    let text = fs::read_to_string(path).map_err(|error| error.to_string())?;
    let marker: ProcessMarker = serde_json::from_str(&text).map_err(|error| error.to_string())?;
    Ok(Some(marker.pid))
}

fn locate_jcmd() -> Result<PathBuf, String> {
    if let Ok(java_home) = std::env::var("JAVA_HOME") {
        let candidate = PathBuf::from(java_home).join("bin").join(executable("jcmd"));
        if candidate.is_file() {
            return Ok(candidate);
        }
    }
    if let Some(path) = std::env::var_os("PATH") {
        for entry in std::env::split_paths(&path) {
            let candidate = entry.join(executable("jcmd"));
            if candidate.is_file() {
                return Ok(candidate);
            }
        }
    }
    Err("Java 21 jcmd was not found. Use a full JDK rather than a JRE-only runtime.".into())
}

fn executable(name: &str) -> String {
    if cfg!(windows) { format!("{name}.exe") } else { name.to_string() }
}

fn diagnostics_dir() -> Result<PathBuf, String> {
    Ok(paths::lazybuilder_logs_dir()?.join("diagnostics"))
}

fn latest_recording_path() -> Result<Option<String>, String> {
    let directory = diagnostics_dir()?;
    if !directory.is_dir() {
        return Ok(None);
    }
    let mut newest: Option<(SystemTime, PathBuf)> = None;
    for entry in fs::read_dir(directory).map_err(|error| error.to_string())? {
        let entry = entry.map_err(|error| error.to_string())?;
        let path = entry.path();
        if path.extension().and_then(|value| value.to_str()) != Some("jfr") {
            continue;
        }
        let modified = entry.metadata().and_then(|meta| meta.modified()).unwrap_or(UNIX_EPOCH);
        if newest.as_ref().is_none_or(|(time, _)| modified > *time) {
            newest = Some((modified, path));
        }
    }
    Ok(newest.map(|(_, path)| path.display().to_string()))
}

fn combined_output(stdout: &[u8], stderr: &[u8]) -> String {
    let mut text = String::from_utf8_lossy(stdout).to_string();
    if !stderr.is_empty() {
        if !text.is_empty() && !text.ends_with('\n') { text.push('\n'); }
        text.push_str(&String::from_utf8_lossy(stderr));
    }
    text
}

#[allow(dead_code)]
fn _is_inside(base: &Path, path: &Path) -> bool {
    path.starts_with(base)
}
