use crate::engine::{paths, resource_settings, server_config, world_manager};
use serde::{Deserialize, Serialize};
use std::fs;
use std::fs::OpenOptions;
use std::io::{BufRead, BufReader, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
#[cfg(windows)]
use std::os::windows::process::CommandExt;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};
use sysinfo::{Pid, Process, System};

mod console;

#[cfg(windows)]
const CREATE_NO_WINDOW: u32 = 0x0800_0000;
type ServerManagerOptions = server_config::ServerConfig;

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerSnapshot { pub state: String, pub health: String, pub cpu_load_percent: f32, pub used_memory_bytes: u64, pub max_memory_bytes: u64, pub pid: Option<u32>, pub log_path: String }
#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerPreflight { pub ready: bool, pub workspace: String, pub server_directory: String, pub paper_jar: String, pub worlds_directory: String, pub java_path: String, pub java_version: String, pub log_directory: String, pub issues: Vec<String> }
#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DetachedRecoveryResult { pub pid: u32, pub stopped: bool, pub message: String }
#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ServerProcessMarker { pid: u32, #[serde(default)] process_start_time: u64 }

pub struct ServerManagerState {
    workspace_root: PathBuf,
    child: Mutex<Option<Child>>,
    system: Mutex<System>,
    runtime_state: Arc<Mutex<String>>,
    expected_stop: Arc<AtomicBool>,
    startup_started_at: Mutex<Option<Instant>>,
    active_log_path: Arc<Mutex<String>>,
    max_memory_bytes: Mutex<u64>,
    startup_timeout_seconds: Mutex<u64>,
    graceful_stop_timeout_seconds: Mutex<u64>,
}

impl Default for ServerManagerState {
    fn default() -> Self { Self::for_workspace(PathBuf::new()) }
}

impl ServerManagerState {
    pub fn for_workspace(workspace_root: PathBuf) -> Self {
        Self {
            workspace_root,
            child: Mutex::new(None),
            system: Mutex::new(System::new_all()),
            runtime_state: Arc::new(Mutex::new("Offline".into())),
            expected_stop: Arc::new(AtomicBool::new(false)),
            startup_started_at: Mutex::new(None),
            active_log_path: Arc::new(Mutex::new(String::new())),
            max_memory_bytes: Mutex::new(0),
            startup_timeout_seconds: Mutex::new(0),
            graceful_stop_timeout_seconds: Mutex::new(0),
        }
    }

    fn workspace_root(&self) -> Result<PathBuf, String> {
        if self.workspace_root.as_os_str().is_empty() { paths::workspace_root() } else { Ok(self.workspace_root.clone()) }
    }

    pub fn preflight(&self) -> ServerPreflight {
        let mut issues = Vec::new(); let mut workspace_display = String::new(); let mut server_display = String::new(); let mut paper_display = String::new(); let mut worlds_display = String::new(); let mut java_display = String::new(); let mut java_version = String::new(); let mut logs_display = String::new();
        let workspace = match self.workspace_root() { Ok(value) => { workspace_display = value.display().to_string(); Some(value) }, Err(error) => { issues.push(error); None } };
        let options = match load_options() { Ok(value) => Some(value), Err(error) => { issues.push(format!("Server configuration: {error}")); None } };
        if let (Some(workspace), Some(options)) = (&workspace, &options) {
            match resolve_server_paths(workspace, options) {
                Ok((server_dir, paper)) => { server_display = server_dir.display().to_string(); paper_display = paper.display().to_string(); logs_display = server_dir.join("logs").display().to_string(); if !server_dir.is_dir() { issues.push(format!("Server directory was not found: {}", server_dir.display())); } if !paper.is_file() { issues.push(format!("Paper server JAR was not found: {}", paper.display())); } }
                Err(error) => issues.push(error),
            }
            match resolve_java(&options.java_path) {
                Ok(java) => { java_display = java.display().to_string(); match java_major_and_text(&java) { Ok((major, text)) => { java_version = text; if major != 21 { issues.push(format!("Paper 1.21.4 requires Java 21; detected Java {major}.")); } }, Err(error) => issues.push(error) } }
                Err(error) => issues.push(error),
            }
            if let Ok(profile) = resource_settings::profile() { if !profile.warning.is_empty() { issues.push(profile.warning); } }
            worlds_display = workspace.join("world-system").join("worlds").display().to_string();
        }
        match self.detached_process() { Ok(Some(pid)) => issues.push(format!("A previous managed Paper process for this workspace is still running as PID {pid}. Recover or stop it before starting this workspace again.")), Ok(None) => {}, Err(error) => issues.push(format!("Process recovery check: {error}")) }
        ServerPreflight { ready: issues.is_empty(), workspace: workspace_display, server_directory: server_display, paper_jar: paper_display, worlds_directory: worlds_display, java_path: java_display, java_version, log_directory: logs_display, issues }
    }

    pub fn snapshot(&self) -> Result<ServerSnapshot, String> {
        let max_memory = self.runtime_max_memory_bytes();
        let mut child_guard = self.child.lock().map_err(|_| "server state lock poisoned".to_string())?;
        let Some(child) = child_guard.as_mut() else {
            if let Some(pid) = self.detached_process()? { let (cpu, memory) = self.process_usage(pid)?; return Ok(ServerSnapshot { state: "Detached".into(), health: "Warning".into(), cpu_load_percent: cpu, used_memory_bytes: memory, max_memory_bytes: max_memory, pid: Some(pid), log_path: self.current_log_path() }); }
            let state = self.runtime_state.lock().map_err(|_| "server runtime state lock poisoned".to_string())?.clone(); return Ok(offline_like_snapshot(state, self.current_log_path(), max_memory));
        };
        if child.try_wait().map_err(|error| error.to_string())?.is_some() {
            let pid = child.id(); *child_guard = None; let _ = self.remove_process_marker_if_matches(pid); let state = if self.expected_stop.load(Ordering::SeqCst) { "Offline" } else { "Crashed" }; set_runtime_state(&self.runtime_state, state)?; *self.startup_started_at.lock().map_err(|_| "startup state lock poisoned".to_string())? = None; return Ok(offline_like_snapshot(state.into(), self.current_log_path(), max_memory));
        }
        let pid = child.id(); let state = self.runtime_state.lock().map_err(|_| "server runtime state lock poisoned".to_string())?.clone();
        if state == "Crashed" && self.detached_process()?.is_some() { let (cpu, memory) = self.process_usage(pid)?; return Ok(ServerSnapshot { state: "Detached".into(), health: "Warning".into(), cpu_load_percent: cpu, used_memory_bytes: memory, max_memory_bytes: max_memory, pid: Some(pid), log_path: self.current_log_path() }); }
        let (cpu, memory) = self.process_usage(pid)?; let memory_ratio = if max_memory == 0 { 0.0 } else { memory as f64 / max_memory as f64 };
        let startup_timeout = self.cached_startup_timeout();
        let startup_timed_out = if state == "Starting" { self.startup_started_at.lock().map_err(|_| "startup state lock poisoned".to_string())?.as_ref().map(|started| started.elapsed() >= Duration::from_secs(startup_timeout)).unwrap_or(false) } else { false };
        let health = if state == "Crashed" { "Critical" } else if cpu >= 90.0 || memory_ratio >= 0.90 { "Critical" } else if cpu >= 75.0 || memory_ratio >= 0.75 || state == "Starting" || state == "Stopping" || startup_timed_out { "Warning" } else { "Good" };
        Ok(ServerSnapshot { state, health: health.into(), cpu_load_percent: cpu, used_memory_bytes: memory, max_memory_bytes: max_memory, pid: Some(pid), log_path: self.current_log_path() })
    }

    pub fn start(&self, paper_port: u16) -> Result<(), String> {
        let mut guard = self.child.lock().map_err(|_| "server state lock poisoned".to_string())?;
        if let Some(child) = guard.as_mut() {
            if child.try_wait().map_err(|error| error.to_string())?.is_none() { let state = self.runtime_state.lock().map_err(|_| "server runtime state lock poisoned".to_string())?.clone(); if state == "Crashed" { return Err("A Paper process is still running in recovery state. Stop it before starting again.".into()); } return Ok(()); }
            let pid = child.id(); *guard = None; let _ = self.remove_process_marker_if_matches(pid);
        }
        if let Some(pid) = self.detached_process()? { return Err(format!("Refusing to start another Paper process for this workspace. Previous managed process PID {pid} is still running.")); }
        let workspace = self.workspace_root()?;
        let active_workspace = paths::workspace_root()?;
        if active_workspace != workspace { return Err("Server runtime target no longer matches the active workspace. Re-open the server and retry.".into()); }
        paths::ensure_runtime_layout()?; let worlds_dir = paths::worlds_dir()?; let options = load_options()?; let resources = resource_settings::runtime_resources()?; let (server_dir, paper) = resolve_server_paths(&workspace, &options)?;
        if !server_dir.is_dir() { return Err(format!("Server directory was not found: {}", server_dir.display())); }
        if !paper.is_file() { return Err(format!("Paper server JAR was not found: {}", paper.display())); }
        let java = resolve_java(&options.java_path)?; validate_java_21(&java)?; let control = world_manager::load_or_create_control_options()?;
        *self.max_memory_bytes.lock().map_err(|_| "server memory state lock poisoned".to_string())? = resources.max_memory_mb * 1024 * 1024;
        *self.startup_timeout_seconds.lock().map_err(|_| "startup timeout state lock poisoned".to_string())? = options.startup_timeout_seconds;
        *self.graceful_stop_timeout_seconds.lock().map_err(|_| "stop timeout state lock poisoned".to_string())? = options.graceful_stop_timeout_seconds;
        let paper_log = server_dir.join("logs").join("latest.log");
        let startup_log = paths::lazybuilder_logs_dir()?.join("paper-startup.log");
        fs::write(&startup_log, b"").map_err(|error| format!("Could not initialize Paper startup log: {error}"))?;
        *self.active_log_path.lock().map_err(|_| "log path lock poisoned".to_string())? = startup_log.display().to_string();
        self.expected_stop.store(false, Ordering::SeqCst); set_runtime_state(&self.runtime_state, "Starting")?; *self.startup_started_at.lock().map_err(|_| "startup state lock poisoned".to_string())? = Some(Instant::now());

        let mut command = Command::new(java); hide_windows_console(&mut command);
        command.current_dir(&server_dir).arg(format!("-Xms{}M", resources.min_memory_mb)).arg(format!("-Xmx{}M", resources.max_memory_mb));
        let mut child = command.args(["-jar", &options.paper_jar]).arg("--universe").arg(&worlds_dir).arg("--port").arg(paper_port.to_string()).arg("nogui")
            .env(paths::WORKSPACE_ENV, &workspace).env(world_manager::TOKEN_ENV, &control.token).env(world_manager::PORT_ENV, control.port.to_string())
            .stdin(Stdio::piped()).stdout(Stdio::piped()).stderr(Stdio::piped()).spawn().map_err(|error| { let _ = set_runtime_state(&self.runtime_state, "Crashed"); format!("failed to start Paper: {error}") })?;

        if let Err(marker_error) = self.write_process_marker(child.id()) {
            let pid = child.id();
            match child.kill() {
                Ok(()) => { let _ = child.wait(); let _ = set_runtime_state(&self.runtime_state, "Crashed"); if let Ok(mut startup) = self.startup_started_at.lock() { *startup = None; } return Err(format!("Paper PID {pid} was terminated because LazyBuilder could not persist its process marker: {marker_error}")); }
                Err(kill_error) => { drain_pipe_to_log(child.stdout.take(), startup_log.clone()); drain_pipe_to_log(child.stderr.take(), startup_log.clone()); *guard = Some(child); let _ = set_runtime_state(&self.runtime_state, "Crashed"); return Err(format!("LazyBuilder could not persist the process marker for Paper PID {pid}: {marker_error}; termination also failed: {kill_error}. The process remains owned by this controller and must be stopped before retrying.")); }
            }
        }

        if let Some(stderr) = child.stderr.take() {
            let log = startup_log.clone();
            thread::spawn(move || { let reader = BufReader::new(stderr); for line in reader.lines().map_while(Result::ok) { let _ = append_startup_line(&log, "stderr", &line); } });
        }
        if let Some(stdout) = child.stdout.take() {
            let runtime_state = Arc::clone(&self.runtime_state); let expected_stop = Arc::clone(&self.expected_stop); let active_log_path = Arc::clone(&self.active_log_path); let startup_log_for_stdout = startup_log.clone(); let paper_log_for_stdout = paper_log.clone();
            thread::spawn(move || {
                let reader = BufReader::new(stdout);
                for line in reader.lines().map_while(Result::ok) {
                    let _ = append_startup_line(&startup_log_for_stdout, "stdout", &line);
                    if line.contains("Done (") { let _ = set_runtime_state(&runtime_state, "Online"); if let Ok(mut path) = active_log_path.lock() { *path = paper_log_for_stdout.display().to_string(); } }
                }
                if !expected_stop.load(Ordering::SeqCst) { let _ = set_runtime_state(&runtime_state, "Crashed"); }
            });
        }
        *guard = Some(child); Ok(())
    }

    pub fn stop(&self) -> Result<(), String> {
        let mut guard = self.child.lock().map_err(|_| "server state lock poisoned".to_string())?;
        let Some(child) = guard.as_mut() else { if let Some(pid) = self.detached_process()? { return Err(format!("Paper PID {pid} is still running but this controller no longer owns its stdin. Stop that process explicitly before continuing.")); } set_runtime_state(&self.runtime_state, "Offline")?; return Ok(()); };
        let timeout_seconds = self.cached_graceful_stop_timeout();
        self.expected_stop.store(true, Ordering::SeqCst); set_runtime_state(&self.runtime_state, "Stopping")?;
        if let Some(stdin) = child.stdin.as_mut() { stdin.write_all(b"stop\n").map_err(|error| error.to_string())?; stdin.flush().map_err(|error| error.to_string())?; }
        let pid = child.id(); let deadline = Instant::now() + Duration::from_secs(timeout_seconds);
        while Instant::now() < deadline { if child.try_wait().map_err(|error| error.to_string())?.is_some() { *guard = None; let _ = self.remove_process_marker_if_matches(pid); set_runtime_state(&self.runtime_state, "Offline")?; *self.startup_started_at.lock().map_err(|_| "startup state lock poisoned".to_string())? = None; return Ok(()); } thread::sleep(Duration::from_millis(100)); }
        child.kill().map_err(|error| error.to_string())?; let _ = child.wait(); *guard = None; let _ = self.remove_process_marker_if_matches(pid); set_runtime_state(&self.runtime_state, "Offline")?; *self.startup_started_at.lock().map_err(|_| "startup state lock poisoned".to_string())? = None; Ok(())
    }

    pub fn recover_detached(&self) -> Result<DetachedRecoveryResult, String> {
        let Some(pid_value) = self.detached_process()? else { return Err("No detached LazyBuilder Paper process marker exists for this workspace.".into()); };
        let pid = Pid::from_u32(pid_value); let mut system = self.system.lock().map_err(|_| "system monitor lock poisoned".to_string())?; system.refresh_process(pid);
        let Some(process) = system.process(pid) else { drop(system); self.remove_process_marker_if_matches(pid_value)?; self.clear_legacy_process_identity(); return Ok(DetachedRecoveryResult { pid: pid_value, stopped: true, message: "The previous Paper process is no longer running. Its stale recovery marker was cleared.".into() }); };
        if !self.looks_like_managed_paper(process)? { return Err(format!("PID {pid_value} no longer matches the LazyBuilder-managed Paper command line for this workspace. Recovery was blocked to avoid terminating an unrelated process.")); }
        self.expected_stop.store(true, Ordering::SeqCst); set_runtime_state(&self.runtime_state, "Stopping")?;
        if !process.kill() { self.expected_stop.store(false, Ordering::SeqCst); return Err(format!("Windows refused to terminate detached Paper PID {pid_value}.")); }
        let deadline = Instant::now() + Duration::from_secs(10);
        while Instant::now() < deadline { thread::sleep(Duration::from_millis(100)); system.refresh_process(pid); if system.process(pid).is_none() { drop(system); self.remove_process_marker_if_matches(pid_value)?; self.clear_legacy_process_identity(); set_runtime_state(&self.runtime_state, "Offline")?; *self.startup_started_at.lock().map_err(|_| "startup state lock poisoned".to_string())? = None; return Ok(DetachedRecoveryResult { pid: pid_value, stopped: true, message: format!("Detached Paper PID {pid_value} was terminated and this workspace controller is ready to start a new managed instance.") }); } }
        Err(format!("Detached Paper PID {pid_value} did not exit after the recovery termination request."))
    }

    fn runtime_max_memory_bytes(&self) -> u64 {
        self.max_memory_bytes.lock().map(|value| *value).unwrap_or(0)
    }

    fn cached_startup_timeout(&self) -> u64 {
        let cached = self.startup_timeout_seconds.lock().map(|value| *value).unwrap_or(0);
        if cached != 0 { cached } else { 90 }
    }

    fn cached_graceful_stop_timeout(&self) -> u64 {
        let cached = self.graceful_stop_timeout_seconds.lock().map(|value| *value).unwrap_or(0);
        if cached != 0 { cached } else { 30 }
    }

    fn process_usage(&self, pid: u32) -> Result<(f32, u64), String> { let pid = Pid::from_u32(pid); let mut system = self.system.lock().map_err(|_| "system monitor lock poisoned".to_string())?; system.refresh_process(pid); Ok(system.process(pid).map(|process| (process.cpu_usage(), process.memory())).unwrap_or((0.0, 0))) }
    fn detached_process(&self) -> Result<Option<u32>, String> {
        let Some(mut marker) = self.read_process_marker()? else { return Ok(None); }; let pid = Pid::from_u32(marker.pid); let mut system = self.system.lock().map_err(|_| "system monitor lock poisoned".to_string())?; system.refresh_process(pid);
        let Some(process) = system.process(pid) else { drop(system); let _ = self.remove_process_marker_if_matches(marker.pid); self.clear_legacy_process_identity(); return Ok(None); };
        if marker.process_start_time != 0 { if process.start_time() != marker.process_start_time { drop(system); let _ = self.remove_process_marker_if_matches(marker.pid); self.clear_legacy_process_identity(); return Ok(None); } return Ok(Some(marker.pid)); }
        if !self.looks_like_managed_paper(process)? { drop(system); let _ = self.remove_process_marker_if_matches(marker.pid); self.clear_legacy_process_identity(); return Ok(None); }
        marker.process_start_time = process.start_time(); drop(system); self.write_process_marker_value(&marker)?; self.clear_legacy_process_identity(); Ok(Some(marker.pid))
    }
    fn current_log_path(&self) -> String { self.active_log_path.lock().map(|value| value.clone()).unwrap_or_default() }

    fn process_marker_path(&self) -> Result<PathBuf, String> { Ok(self.workspace_root()?.join("tools").join("lazybuilder").join("cache").join("server-process.json")) }
    fn write_process_marker(&self, pid: u32) -> Result<(), String> { let pid_value = Pid::from_u32(pid); let mut system = System::new_all(); system.refresh_process(pid_value); let process = system.process(pid_value).ok_or_else(|| format!("Paper PID {pid} exited before its process marker could be recorded"))?; let marker = ServerProcessMarker { pid, process_start_time: process.start_time() }; self.write_process_marker_value(&marker)?; self.clear_legacy_process_identity(); Ok(()) }
    fn write_process_marker_value(&self, marker: &ServerProcessMarker) -> Result<(), String> { let path = self.process_marker_path()?; if let Some(parent) = path.parent() { fs::create_dir_all(parent).map_err(|error| error.to_string())?; } let text = serde_json::to_string_pretty(marker).map_err(|error| error.to_string())?; let temporary = path.with_extension("json.tmp"); fs::write(&temporary, text).map_err(|error| error.to_string())?; replace_file(&temporary, &path) }
    fn read_process_marker(&self) -> Result<Option<ServerProcessMarker>, String> { let path = self.process_marker_path()?; if !path.is_file() { self.clear_legacy_process_identity(); return Ok(None); } let text = fs::read_to_string(&path).map_err(|error| error.to_string())?; match serde_json::from_str(&text) { Ok(marker) => Ok(Some(marker)), Err(_) => { let _ = fs::remove_file(path); self.clear_legacy_process_identity(); Ok(None) } } }
    fn remove_process_marker_if_matches(&self, pid: u32) -> Result<(), String> { let path = self.process_marker_path()?; let Some(marker) = self.read_process_marker()? else { return Ok(()); }; if marker.pid == pid { fs::remove_file(path).map_err(|error| error.to_string())?; self.clear_legacy_process_identity(); } Ok(()) }
    fn looks_like_managed_paper(&self, process: &Process) -> Result<bool, String> { let process_name = process.name().to_ascii_lowercase(); let command_lower = process.cmd().join(" ").to_ascii_lowercase(); let worlds = self.workspace_root()?.join("world-system").join("worlds").display().to_string().to_ascii_lowercase(); Ok(process_name.contains("java") && command_lower.contains("-jar") && command_lower.contains("--universe") && command_lower.contains("nogui") && command_lower.contains(&worlds)) }
    fn clear_legacy_process_identity(&self) { if let Ok(root) = self.workspace_root() { let path = root.join("tools").join("lazybuilder").join("cache").join("server-process-identity.json"); let _ = fs::remove_file(&path); let _ = fs::remove_file(path.with_extension("json.previous")); let _ = fs::remove_file(path.with_extension("json.tmp")); } }
}

impl Drop for ServerManagerState { fn drop(&mut self) { let _ = self.stop(); } }

fn append_startup_line(path: &Path, stream: &str, line: &str) -> Result<(), String> {
    let mut file = OpenOptions::new().create(true).append(true).open(path).map_err(|error| error.to_string())?;
    writeln!(file, "[{stream}] {line}").map_err(|error| error.to_string())
}
fn drain_pipe_to_log<R: std::io::Read + Send + 'static>(pipe: Option<R>, path: PathBuf) {
    if let Some(pipe) = pipe { thread::spawn(move || { let reader = BufReader::new(pipe); for line in reader.lines().map_while(Result::ok) { let _ = append_startup_line(&path, "process", &line); } }); }
}
fn set_runtime_state(state: &Arc<Mutex<String>>, value: &str) -> Result<(), String> { *state.lock().map_err(|_| "server runtime state lock poisoned".to_string())? = value.into(); Ok(()) }
fn offline_like_snapshot(state: String, log_path: String, max_memory_bytes: u64) -> ServerSnapshot { let health = if state == "Crashed" { "Critical" } else { "Offline" }; ServerSnapshot { state, health: health.into(), cpu_load_percent: 0.0, used_memory_bytes: 0, max_memory_bytes, pid: None, log_path } }
fn resolve_server_paths(workspace: &Path, options: &ServerManagerOptions) -> Result<(PathBuf, PathBuf), String> { let server_relative = paths::safe_relative_path(&options.server_directory, "serverDirectory")?; let paper_name = paths::safe_file_name(&options.paper_jar, "paperJar")?; let server_dir = workspace.join(server_relative); let paper = server_dir.join(paper_name); if !server_dir.starts_with(workspace) || !paper.starts_with(workspace) { return Err("Server configuration escaped the LazyBuilder workspace".into()); } Ok((server_dir, paper)) }
fn load_options() -> Result<ServerManagerOptions, String> { server_config::load() }
fn resolve_java(configured: &str) -> Result<PathBuf, String> {
    if !configured.trim().is_empty() { let path = PathBuf::from(configured); if path.is_file() { return path.canonicalize().map_err(|error| error.to_string()); } return Err(format!("Configured Java executable was not found: {}", path.display())); }
    if let Ok(java_home) = std::env::var("JAVA_HOME") { let candidate = PathBuf::from(java_home).join("bin").join(if cfg!(windows) { "java.exe" } else { "java" }); if candidate.is_file() { return candidate.canonicalize().map_err(|error| error.to_string()); } }
    if let Some(path) = std::env::var_os("PATH") { for entry in std::env::split_paths(&path) { let candidate = entry.join(if cfg!(windows) { "java.exe" } else { "java" }); if candidate.is_file() { return candidate.canonicalize().map_err(|error| error.to_string()); } } }
    Err("Java 21 executable could not be located. Configure javaPath or JAVA_HOME.".into())
}
fn java_major_and_text(java: &Path) -> Result<(u32, String), String> { let mut command = Command::new(java); hide_windows_console(&mut command); let output = command.arg("--version").output().map_err(|error| error.to_string())?; let text = format!("{}\n{}", String::from_utf8_lossy(&output.stdout), String::from_utf8_lossy(&output.stderr)); let normalized = text.lines().find(|line| !line.trim().is_empty()).unwrap_or("Unknown Java").trim().to_string(); let major = text.split_whitespace().find_map(|part| part.trim_matches(|ch: char| !ch.is_ascii_digit() && ch != '.').split('.').next()?.parse::<u32>().ok()).ok_or_else(|| "Could not determine Java runtime version".to_string())?; Ok((major, normalized)) }
fn validate_java_21(java: &Path) -> Result<(), String> { let (major, _) = java_major_and_text(java)?; if major != 21 { return Err(format!("LazyBuilder Paper 1.21.4 requires Java 21. Detected Java {major}.")); } Ok(()) }
fn hide_windows_console(command: &mut Command) { #[cfg(windows)] { command.creation_flags(CREATE_NO_WINDOW); } }
fn replace_file(source: &Path, destination: &Path) -> Result<(), String> { if destination.exists() { let backup = destination.with_extension("json.previous"); let _ = fs::remove_file(&backup); fs::rename(destination, &backup).map_err(|error| error.to_string())?; match fs::rename(source, destination) { Ok(()) => { let _ = fs::remove_file(backup); Ok(()) }, Err(error) => { let _ = fs::rename(&backup, destination); Err(error.to_string()) } } } else { fs::rename(source, destination).map_err(|error| error.to_string()) } }
