use crate::engine::{paths, world_manager};
use serde::{Deserialize, Serialize};
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use std::thread;
use std::time::{Duration, Instant};
use sysinfo::{Pid, System};

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct ServerManagerOptions {
    pub java_path: String,
    pub server_directory: String,
    pub paper_jar: String,
    pub min_memory_mb: u64,
    pub max_memory_mb: u64,
    pub graceful_stop_timeout_seconds: u64,
}

impl Default for ServerManagerOptions {
    fn default() -> Self {
        Self {
            java_path: String::new(),
            server_directory: "server".into(),
            paper_jar: "paper.jar".into(),
            min_memory_mb: 1024,
            max_memory_mb: 4096,
            graceful_stop_timeout_seconds: 30,
        }
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerSnapshot {
    pub state: String,
    pub health: String,
    pub cpu_load_percent: f32,
    pub used_memory_bytes: u64,
    pub max_memory_bytes: u64,
}

pub struct ServerManagerState {
    child: Mutex<Option<Child>>,
    system: Mutex<System>,
}

impl Default for ServerManagerState {
    fn default() -> Self {
        Self {
            child: Mutex::new(None),
            system: Mutex::new(System::new_all()),
        }
    }
}

impl ServerManagerState {
    pub fn snapshot(&self) -> Result<ServerSnapshot, String> {
        let options = load_options()?;
        let mut child_guard = self.child.lock().map_err(|_| "server state lock poisoned".to_string())?;
        let Some(child) = child_guard.as_mut() else {
            return Ok(offline_snapshot(&options));
        };

        if child.try_wait().map_err(|error| error.to_string())?.is_some() {
            *child_guard = None;
            return Ok(offline_snapshot(&options));
        }

        let pid = Pid::from_u32(child.id());
        let mut system = self.system.lock().map_err(|_| "system monitor lock poisoned".to_string())?;
        system.refresh_process(pid);
        let (cpu, memory) = system
            .process(pid)
            .map(|process| (process.cpu_usage(), process.memory()))
            .unwrap_or((0.0, 0));
        let max_memory = options.max_memory_mb * 1024 * 1024;
        let memory_ratio = if max_memory == 0 { 0.0 } else { memory as f64 / max_memory as f64 };
        let health = if cpu >= 90.0 || memory_ratio >= 0.90 {
            "Critical"
        } else if cpu >= 75.0 || memory_ratio >= 0.75 {
            "Warning"
        } else {
            "Good"
        };

        Ok(ServerSnapshot {
            state: "Online".into(),
            health: health.into(),
            cpu_load_percent: cpu,
            used_memory_bytes: memory,
            max_memory_bytes: max_memory,
        })
    }

    pub fn start(&self) -> Result<(), String> {
        let mut guard = self.child.lock().map_err(|_| "server state lock poisoned".to_string())?;
        if let Some(child) = guard.as_mut() {
            if child.try_wait().map_err(|error| error.to_string())?.is_none() {
                return Ok(());
            }
            *guard = None;
        }

        let workspace = paths::workspace_root()?;
        let options = load_options()?;
        validate_options(&options)?;
        let server_dir = workspace.join(&options.server_directory);
        let paper = server_dir.join(&options.paper_jar);
        if !paper.is_file() {
            return Err(format!("Paper server JAR was not found: {}", paper.display()));
        }

        let java = resolve_java(&options.java_path)?;
        validate_java_21(&java)?;
        let control = world_manager::load_or_create_control_options()?;

        let child = Command::new(java)
            .current_dir(&server_dir)
            .arg(format!("-Xms{}M", options.min_memory_mb))
            .arg(format!("-Xmx{}M", options.max_memory_mb))
            .args(["-jar", &options.paper_jar, "nogui"])
            .env(world_manager::TOKEN_ENV, &control.token)
            .env(world_manager::PORT_ENV, control.port.to_string())
            .stdin(Stdio::piped())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .spawn()
            .map_err(|error| format!("failed to start Paper: {error}"))?;
        *guard = Some(child);
        Ok(())
    }

    pub fn stop(&self) -> Result<(), String> {
        let options = load_options()?;
        let mut guard = self.child.lock().map_err(|_| "server state lock poisoned".to_string())?;
        let Some(child) = guard.as_mut() else { return Ok(()); };

        if let Some(stdin) = child.stdin.as_mut() {
            stdin.write_all(b"stop\n").map_err(|error| error.to_string())?;
            stdin.flush().map_err(|error| error.to_string())?;
        }

        let deadline = Instant::now() + Duration::from_secs(options.graceful_stop_timeout_seconds);
        while Instant::now() < deadline {
            if child.try_wait().map_err(|error| error.to_string())?.is_some() {
                *guard = None;
                return Ok(());
            }
            thread::sleep(Duration::from_millis(100));
        }

        child.kill().map_err(|error| error.to_string())?;
        let _ = child.wait();
        *guard = None;
        Ok(())
    }

    pub fn restart(&self) -> Result<(), String> {
        self.stop()?;
        self.start()
    }
}

fn offline_snapshot(options: &ServerManagerOptions) -> ServerSnapshot {
    ServerSnapshot {
        state: "Offline".into(),
        health: "Offline".into(),
        cpu_load_percent: 0.0,
        used_memory_bytes: 0,
        max_memory_bytes: options.max_memory_mb * 1024 * 1024,
    }
}

fn options_path() -> Result<PathBuf, String> {
    Ok(paths::lazybuilder_tools_dir()?.join("server-manager.json"))
}

fn load_options() -> Result<ServerManagerOptions, String> {
    let path = options_path()?;
    if !path.is_file() {
        let defaults = ServerManagerOptions::default();
        save_options(&path, &defaults)?;
        return Ok(defaults);
    }
    let text = fs::read_to_string(path).map_err(|error| error.to_string())?;
    let options: ServerManagerOptions = serde_json::from_str(&text).map_err(|error| error.to_string())?;
    validate_options(&options)?;
    Ok(options)
}

fn save_options(path: &Path, options: &ServerManagerOptions) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    let text = serde_json::to_string_pretty(options).map_err(|error| error.to_string())?;
    fs::write(path, text).map_err(|error| error.to_string())
}

fn validate_options(options: &ServerManagerOptions) -> Result<(), String> {
    if options.min_memory_mb < 256 { return Err("minMemoryMb must be at least 256 MB".into()); }
    if options.max_memory_mb < options.min_memory_mb { return Err("maxMemoryMb must be >= minMemoryMb".into()); }
    if options.graceful_stop_timeout_seconds < 5 { return Err("gracefulStopTimeoutSeconds must be at least 5".into()); }
    if options.server_directory.trim().is_empty() || options.paper_jar.trim().is_empty() { return Err("serverDirectory and paperJar are required".into()); }
    Ok(())
}

fn resolve_java(configured: &str) -> Result<PathBuf, String> {
    if !configured.trim().is_empty() {
        let path = PathBuf::from(configured);
        if path.is_file() { return Ok(path); }
        return Err(format!("Configured Java executable was not found: {}", path.display()));
    }

    if let Ok(java_home) = std::env::var("JAVA_HOME") {
        let candidate = PathBuf::from(java_home).join("bin").join(if cfg!(windows) { "java.exe" } else { "java" });
        if candidate.is_file() { return Ok(candidate); }
    }

    if let Some(path) = std::env::var_os("PATH") {
        for entry in std::env::split_paths(&path) {
            let candidate = entry.join(if cfg!(windows) { "java.exe" } else { "java" });
            if candidate.is_file() { return Ok(candidate); }
        }
    }

    Err("Java 21 executable could not be located. Configure javaPath or JAVA_HOME.".into())
}

fn validate_java_21(java: &Path) -> Result<(), String> {
    let output = Command::new(java).arg("--version").output().map_err(|error| error.to_string())?;
    let text = format!("{}\n{}", String::from_utf8_lossy(&output.stdout), String::from_utf8_lossy(&output.stderr));
    let major = text.split_whitespace()
        .find_map(|part| part.trim_matches(|ch: char| !ch.is_ascii_digit() && ch != '.').split('.').next()?.parse::<u32>().ok())
        .ok_or_else(|| "Could not determine Java runtime version".to_string())?;
    if major != 21 {
        return Err(format!("LazyBuilder Paper 1.21.4 requires Java 21. Detected Java {major}."));
    }
    Ok(())
}
