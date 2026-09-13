use crate::engine::{paths, resource_settings, world_manager};
use serde::{Deserialize, Serialize};
use std::fs;
use std::io::{BufRead, BufReader, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};
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
    pub startup_timeout_seconds: u64,
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
            startup_timeout_seconds: 90,
        }
    }
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerSnapshot {
    pub state: String,
    pub health: String,
    pub cpu_load_percent: f32,
    pub used_memory_bytes: u64,
    pub max_memory_bytes: u64,
    pub pid: Option<u32>,
    pub log_path: String,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerPreflight {
    pub ready: bool,
    pub workspace: String,
    pub server_directory: String,
    pub paper_jar: String,
    pub worlds_directory: String,
    pub java_path: String,
    pub java_version: String,
    pub log_directory: String,
    pub issues: Vec<String>,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ServerProcessMarker {
    pid: u32,
    started_unix_seconds: u64,
}

pub struct ServerManagerState {
    child: Mutex<Option<Child>>,
    system: Mutex<System>,
    runtime_state: Arc<Mutex<String>>,
    expected_stop: Arc<AtomicBool>,
    startup_started_at: Mutex<Option<Instant>>,
    active_log_path: Arc<Mutex<String>>,
}

impl Default for ServerManagerState {
    fn default() -> Self {
        Self {
            child: Mutex::new(None),
            system: Mutex::new(System::new_all()),
            runtime_state: Arc::new(Mutex::new("Offline".into())),
            expected_stop: Arc::new(AtomicBool::new(false)),
            startup_started_at: Mutex::new(None),
            active_log_path: Arc::new(Mutex::new(String::new())),
        }
    }
}

impl ServerManagerState {
    pub fn preflight(&self) -> ServerPreflight {
        let mut issues = Vec::new();
        let mut workspace_display = String::new();
        let mut server_display = String::new();
        let mut paper_display = String::new();
        let mut worlds_display = String::new();
        let mut java_display = String::new();
        let mut java_version = String::new();
        let mut logs_display = String::new();

        let workspace = match paths::workspace_root() {
            Ok(value) => {
                workspace_display = value.display().to_string();
                Some(value)
            }
            Err(error) => {
                issues.push(error);
                None
            }
        };

        let options = match load_options() {
            Ok(value) => Some(value),
            Err(error) => {
                issues.push(format!("Server configuration: {error}"));
                None
            }
        };

        if let (Some(workspace), Some(options)) = (&workspace, &options) {
            match resolve_server_paths(workspace, options) {
                Ok((server_dir, paper)) => {
                    server_display = server_dir.display().to_string();
                    paper_display = paper.display().to_string();
                    logs_display = server_dir.join("logs").display().to_string();
                    if !server_dir.is_dir() {
                        issues.push(format!("Server directory was not found: {}", server_dir.display()));
                    }
                    if !paper.is_file() {
                        issues.push(format!("Paper server JAR was not found: {}", paper.display()));
                    }
                }
                Err(error) => issues.push(error),
            }

            match resolve_java(&options.java_path) {
                Ok(java) => {
                    java_display = java.display().to_string();
                    match java_major_and_text(&java) {
                        Ok((major, text)) => {
                            java_version = text;
                            if major != 21 {
                                issues.push(format!("Paper 1.21.4 requires Java 21; detected Java {major}."));
                            }
                        }
                        Err(error) => issues.push(error),
                    }
                }
                Err(error) => issues.push(error),
            }

            if let Ok(profile) = resource_settings::profile() {
                if !profile.warning.is_empty() {
                    issues.push(profile.warning);
                }
            }
        }

        match paths::worlds_dir() {
            Ok(value) => worlds_display = value.display().to_string(),
            Err(error) => issues.push(error),
        }

        match self.detached_process() {
            Ok(Some(pid)) => issues.push(format!(
                "A previous managed Paper process is still running as PID {pid}. Close it before starting another instance."
            )),
            Ok(None) => {}
            Err(error) => issues.push(format!("Process recovery check: {error}")),
        }

        ServerPreflight {
            ready: issues.is_empty(),
            workspace: workspace_display,
            server_directory: server_display,
            paper_jar: paper_display,
            worlds_directory: worlds_display,
            java_path: java_display,
            java_version,
            log_directory: logs_display,
            issues,
        }
    }

    pub fn snapshot(&self) -> Result<ServerSnapshot, String> {
        let options = load_options()?;
        let runtime_resources = resource_settings::runtime_resources(options.min_memory_mb, options.max_memory_mb)?;
        let mut child_guard = self.child.lock().map_err(|_| "server state lock poisoned".to_string())?;
        let Some(child) = child_guard.as_mut() else {
            if let Some(pid) = self.detached_process()? {
                let (cpu, memory) = self.process_usage(pid)?;
                return Ok(ServerSnapshot {
                    state: "Detached".into(),
                    health: "Warning".into(),
                    cpu_load_percent: cpu,
                    used_memory_bytes: memory,
                    max_memory_bytes: runtime_resources.max_memory_mb * 1024 * 1024,
                    pid: Some(pid),
                    log_path: self.current_log_path(),
                });
            }
            let state = self.runtime_state.lock().map_err(|_| "server runtime state lock poisoned".to_string())?.clone();
            return Ok(offline_like_snapshot(&options, state, self.current_log_path()));
        };

        if child.try_wait().map_err(|error| error.to_string())?.is_some() {
            let pid = child.id();
            *child_guard = None;
            let _ = remove_process_marker_if_matches(pid);
            let state = if self.expected_stop.load(Ordering::SeqCst) { "Offline" } else { "Crashed" };
            set_runtime_state(&self.runtime_state, state)?;
            *self.startup_started_at.lock().map_err(|_| "startup state lock poisoned".to_string())? = None;
            return Ok(offline_like_snapshot(&options, state.into(), self.current_log_path()));
        }

        let pid = child.id();
        let (cpu, memory) = self.process_usage(pid)?;
        let max_memory = runtime_resources.max_memory_mb * 1024 * 1024;
        let memory_ratio = if max_memory == 0 { 0.0 } else { memory as f64 / max_memory as f64 };
        let state = self.runtime_state.lock().map_err(|_| "server runtime state lock poisoned".to_string())?.clone();

        let startup_timed_out = if state == "Starting" {
            self.startup_started_at
                .lock()
                .map_err(|_| "startup state lock poisoned".to_string())?
                .as_ref()
                .map(|started| started.elapsed() >= Duration::from_secs(options.startup_timeout_seconds))
                .unwrap_or(false)
        } else {
            false
        };

        let health = if state == "Crashed" {
            "Critical"
        } else if cpu >= 90.0 || memory_ratio >= 0.90 {
            "Critical"
        } else if cpu >= 75.0 || memory_ratio >= 0.75 || state == "Starting" || state == "Stopping" || startup_timed_out {
            "Warning"
        } else {
            "Good"
        };

        Ok(ServerSnapshot {
            state,
            health: health.into(),
            cpu_load_percent: cpu,
            used_memory_bytes: memory,
            max_memory_bytes: max_memory,
            pid: Some(pid),
            log_path: self.current_log_path(),
        })
    }

    pub fn start(&self) -> Result<(), String> {
        let mut guard = self.child.lock().map_err(|_| "server state lock poisoned".to_string())?;
        if let Some(child) = guard.as_mut() {
            if child.try_wait().map_err(|error| error.to_string())?.is_none() {
                return Ok(());
            }
            let pid = child.id();
            *guard = None;
            let _ = remove_process_marker_if_matches(pid);
        }
        if let Some(pid) = self.detached_process()? {
            return Err(format!(
                "Refusing to start a second Paper instance. Previous managed process PID {pid} is still running."
            ));
        }

        let workspace = paths::workspace_root()?;
        paths::ensure_runtime_layout()?;
        let worlds_dir = paths::worlds_dir()?;
        let options = load_options()?;
        validate_options(&options)?;
        let resources = resource_settings::runtime_resources(options.min_memory_mb, options.max_memory_mb)?;
        let (server_dir, paper) = resolve_server_paths(&workspace, &options)?;
        if !server_dir.is_dir() {
            return Err(format!("Server directory was not found: {}", server_dir.display()));
        }
        if !paper.is_file() {
            return Err(format!("Paper server JAR was not found: {}", paper.display()));
        }

        let java = resolve_java(&options.java_path)?;
        validate_java_21(&java)?;
        let control = world_manager::load_or_create_control_options()?;
        let paper_log = server_dir.join("logs").join("latest.log");
        *self.active_log_path.lock().map_err(|_| "log path lock poisoned".to_string())? = paper_log.display().to_string();

        self.expected_stop.store(false, Ordering::SeqCst);
        set_runtime_state(&self.runtime_state, "Starting")?;
        *self.startup_started_at.lock().map_err(|_| "startup state lock poisoned".to_string())? = Some(Instant::now());

        let mut command = Command::new(java);
        command
            .current_dir(&server_dir)
            .arg(format!("-Xms{}M", resources.min_memory_mb))
            .arg(format!("-Xmx{}M", resources.max_memory_mb));
        if let Some(cpu_threads) = resources.cpu_threads {
            command.arg(format!("-XX:ActiveProcessorCount={cpu_threads}"));
        }
        let mut child = command
            .args(["-jar", &options.paper_jar])
            .arg("--universe")
            .arg(&worlds_dir)
            .arg("nogui")
            .env(paths::WORKSPACE_ENV, &workspace)
            .env(world_manager::TOKEN_ENV, &control.token)
            .env(world_manager::PORT_ENV, control.port.to_string())
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()
            .map_err(|error| {
                let _ = set_runtime_state(&self.runtime_state, "Crashed");
                format!("failed to start Paper: {error}")
            })?;

        write_process_marker(child.id())?;

        // stdout is consumed only to detect Paper readiness and avoid a filled pipe.
        // Paper already persists its own canonical logs under server/logs; duplicating
        // every console line in the controller would add continuous disk I/O.
        if let Some(stdout) = child.stdout.take() {
            let runtime_state = Arc::clone(&self.runtime_state);
            let expected_stop = Arc::clone(&self.expected_stop);
            thread::spawn(move || {
                let reader = BufReader::new(stdout);
                for line in reader.lines().map_while(Result::ok) {
                    if line.contains("Done (") {
                        let _ = set_runtime_state(&runtime_state, "Online");
                    }
                }
                if !expected_stop.load(Ordering::SeqCst) {
                    let _ = set_runtime_state(&runtime_state, "Crashed");
                }
            });
        }

        *guard = Some(child);
        Ok(())
    }

    pub fn stop(&self) -> Result<(), String> {
        let options = load_options()?;
        let mut guard = self.child.lock().map_err(|_| "server state lock poisoned".to_string())?;
        let Some(child) = guard.as_mut() else {
            if let Some(pid) = self.detached_process()? {
                return Err(format!(
                    "Paper PID {pid} is still running but this controller no longer owns its stdin. Stop that process explicitly before continuing."
                ));
            }
            set_runtime_state(&self.runtime_state, "Offline")?;
            return Ok(());
        };

        self.expected_stop.store(true, Ordering::SeqCst);
        set_runtime_state(&self.runtime_state, "Stopping")?;

        if let Some(stdin) = child.stdin.as_mut() {
            stdin.write_all(b"stop\n").map_err(|error| error.to_string())?;
            stdin.flush().map_err(|error| error.to_string())?;
        }

        let pid = child.id();
        let deadline = Instant::now() + Duration::from_secs(options.graceful_stop_timeout_seconds);
        while Instant::now() < deadline {
            if child.try_wait().map_err(|error| error.to_string())?.is_some() {
                *guard = None;
                let _ = remove_process_marker_if_matches(pid);
                set_runtime_state(&self.runtime_state, "Offline")?;
                *self.startup_started_at.lock().map_err(|_| "startup state lock poisoned".to_string())? = None;
                return Ok(());
            }
            thread::sleep(Duration::from_millis(100));
        }

        child.kill().map_err(|error| error.to_string())?;
        let _ = child.wait();
        *guard = None;
        let _ = remove_process_marker_if_matches(pid);
        set_runtime_state(&self.runtime_state, "Offline")?;
        *self.startup_started_at.lock().map_err(|_| "startup state lock poisoned".to_string())? = None;
        Ok(())
    }

    pub fn restart(&self) -> Result<(), String> {
        self.stop()?;
        self.start()
    }

    fn process_usage(&self, pid: u32) -> Result<(f32, u64), String> {
        let pid = Pid::from_u32(pid);
        let mut system = self.system.lock().map_err(|_| "system monitor lock poisoned".to_string())?;
        system.refresh_process(pid);
        Ok(system.process(pid).map(|process| (process.cpu_usage(), process.memory())).unwrap_or((0.0, 0)))
    }

    fn detached_process(&self) -> Result<Option<u32>, String> {
        let Some(marker) = read_process_marker()? else { return Ok(None); };
        let pid = Pid::from_u32(marker.pid);
        let mut system = self.system.lock().map_err(|_| "system monitor lock poisoned".to_string())?;
        system.refresh_process(pid);
        if system.process(pid).is_some() {
            return Ok(Some(marker.pid));
        }
        drop(system);
        let _ = remove_process_marker_if_matches(marker.pid);
        Ok(None)
    }

    fn current_log_path(&self) -> String {
        self.active_log_path.lock().map(|value| value.clone()).unwrap_or_default()
    }
}

impl Drop for ServerManagerState {
    fn drop(&mut self) {
        let _ = self.stop();
    }
}

fn set_runtime_state(state: &Arc<Mutex<String>>, value: &str) -> Result<(), String> {
    *state.lock().map_err(|_| "server runtime state lock poisoned".to_string())? = value.into();
    Ok(())
}

fn offline_like_snapshot(options: &ServerManagerOptions, state: String, log_path: String) -> ServerSnapshot {
    let max_memory_mb = resource_settings::runtime_resources(options.min_memory_mb, options.max_memory_mb)
        .map(|value| value.max_memory_mb)
        .unwrap_or(options.max_memory_mb);
    let health = if state == "Crashed" { "Critical" } else { "Offline" };
    ServerSnapshot {
        state,
        health: health.into(),
        cpu_load_percent: 0.0,
        used_memory_bytes: 0,
        max_memory_bytes: max_memory_mb * 1024 * 1024,
        pid: None,
        log_path,
    }
}

fn resolve_server_paths(workspace: &Path, options: &ServerManagerOptions) -> Result<(PathBuf, PathBuf), String> {
    let server_relative = paths::safe_relative_path(&options.server_directory, "serverDirectory")?;
    let paper_name = paths::safe_file_name(&options.paper_jar, "paperJar")?;
    let server_dir = workspace.join(server_relative);
    let paper = server_dir.join(paper_name);
    if !server_dir.starts_with(workspace) || !paper.starts_with(workspace) {
        return Err("Server configuration escaped the LazyBuilder workspace".into());
    }
    Ok((server_dir, paper))
}

fn options_path() -> Result<PathBuf, String> {
    Ok(paths::lazybuilder_config_dir()?.join("server-manager.json"))
}

fn legacy_options_path() -> Result<PathBuf, String> {
    Ok(paths::lazybuilder_tools_dir()?.join("server-manager.json"))
}

fn process_marker_path() -> Result<PathBuf, String> {
    Ok(paths::lazybuilder_cache_dir()?.join("server-process.json"))
}

fn load_options() -> Result<ServerManagerOptions, String> {
    let path = options_path()?;
    if !path.is_file() {
        let legacy = legacy_options_path()?;
        if legacy.is_file() {
            let text = fs::read_to_string(&legacy).map_err(|error| error.to_string())?;
            let options: ServerManagerOptions = serde_json::from_str(&text).map_err(|error| error.to_string())?;
            validate_options(&options)?;
            save_options(&path, &options)?;
            return Ok(options);
        }
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
    let temporary = path.with_extension("json.tmp");
    fs::write(&temporary, text).map_err(|error| error.to_string())?;
    replace_file(&temporary, path)
}

fn replace_file(source: &Path, destination: &Path) -> Result<(), String> {
    if destination.exists() {
        let backup = destination.with_extension("json.previous");
        let _ = fs::remove_file(&backup);
        fs::rename(destination, &backup).map_err(|error| error.to_string())?;
        match fs::rename(source, destination) {
            Ok(()) => {
                let _ = fs::remove_file(backup);
                Ok(())
            }
            Err(error) => {
                let _ = fs::rename(&backup, destination);
                Err(error.to_string())
            }
        }
    } else {
        fs::rename(source, destination).map_err(|error| error.to_string())
    }
}

fn validate_options(options: &ServerManagerOptions) -> Result<(), String> {
    if options.min_memory_mb < 256 {
        return Err("minMemoryMb must be at least 256 MB".into());
    }
    if options.max_memory_mb < options.min_memory_mb {
        return Err("maxMemoryMb must be >= minMemoryMb".into());
    }
    if options.graceful_stop_timeout_seconds < 5 {
        return Err("gracefulStopTimeoutSeconds must be at least 5".into());
    }
    if options.startup_timeout_seconds < 10 {
        return Err("startupTimeoutSeconds must be at least 10".into());
    }
    paths::safe_relative_path(&options.server_directory, "serverDirectory")?;
    paths::safe_file_name(&options.paper_jar, "paperJar")?;
    Ok(())
}

fn resolve_java(configured: &str) -> Result<PathBuf, String> {
    if !configured.trim().is_empty() {
        let path = PathBuf::from(configured);
        if path.is_file() {
            return path.canonicalize().map_err(|error| error.to_string());
        }
        return Err(format!("Configured Java executable was not found: {}", path.display()));
    }

    if let Ok(java_home) = std::env::var("JAVA_HOME") {
        let candidate = PathBuf::from(java_home).join("bin").join(if cfg!(windows) { "java.exe" } else { "java" });
        if candidate.is_file() {
            return candidate.canonicalize().map_err(|error| error.to_string());
        }
    }

    if let Some(path) = std::env::var_os("PATH") {
        for entry in std::env::split_paths(&path) {
            let candidate = entry.join(if cfg!(windows) { "java.exe" } else { "java" });
            if candidate.is_file() {
                return candidate.canonicalize().map_err(|error| error.to_string());
            }
        }
    }

    Err("Java 21 executable could not be located. Configure javaPath or JAVA_HOME.".into())
}

fn java_major_and_text(java: &Path) -> Result<(u32, String), String> {
    let output = Command::new(java).arg("--version").output().map_err(|error| error.to_string())?;
    let text = format!("{}\n{}", String::from_utf8_lossy(&output.stdout), String::from_utf8_lossy(&output.stderr));
    let normalized = text.lines().find(|line| !line.trim().is_empty()).unwrap_or("Unknown Java").trim().to_string();
    let major = text
        .split_whitespace()
        .find_map(|part| {
            part.trim_matches(|ch: char| !ch.is_ascii_digit() && ch != '.')
                .split('.')
                .next()?
                .parse::<u32>()
                .ok()
        })
        .ok_or_else(|| "Could not determine Java runtime version".to_string())?;
    Ok((major, normalized))
}

fn validate_java_21(java: &Path) -> Result<(), String> {
    let (major, _) = java_major_and_text(java)?;
    if major != 21 {
        return Err(format!("LazyBuilder Paper 1.21.4 requires Java 21. Detected Java {major}."));
    }
    Ok(())
}

fn write_process_marker(pid: u32) -> Result<(), String> {
    let path = process_marker_path()?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    let marker = ServerProcessMarker {
        pid,
        started_unix_seconds: SystemTime::now().duration_since(UNIX_EPOCH).map(|value| value.as_secs()).unwrap_or(0),
    };
    let text = serde_json::to_string_pretty(&marker).map_err(|error| error.to_string())?;
    let temporary = path.with_extension("json.tmp");
    fs::write(&temporary, text).map_err(|error| error.to_string())?;
    replace_file(&temporary, &path)
}

fn read_process_marker() -> Result<Option<ServerProcessMarker>, String> {
    let path = process_marker_path()?;
    if !path.is_file() {
        return Ok(None);
    }
    let text = fs::read_to_string(&path).map_err(|error| error.to_string())?;
    match serde_json::from_str(&text) {
        Ok(marker) => Ok(Some(marker)),
        Err(_) => {
            let _ = fs::remove_file(path);
            Ok(None)
        }
    }
}

fn remove_process_marker_if_matches(pid: u32) -> Result<(), String> {
    let path = process_marker_path()?;
    let Some(marker) = read_process_marker()? else { return Ok(()); };
    if marker.pid == pid {
        fs::remove_file(path).map_err(|error| error.to_string())?;
    }
    Ok(())
}
