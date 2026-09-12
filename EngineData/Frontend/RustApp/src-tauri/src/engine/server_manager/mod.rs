use serde::Serialize;
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerSnapshot {
    pub state: String,
    pub health: String,
    pub cpu_load_percent: f32,
    pub used_memory_bytes: u64,
    pub max_memory_bytes: u64,
}

#[derive(Default)]
pub struct ServerManagerState {
    child: Mutex<Option<Child>>,
}

impl ServerManagerState {
    pub fn snapshot(&self) -> Result<ServerSnapshot, String> {
        let mut guard = self.child.lock().map_err(|_| "server state lock poisoned".to_string())?;
        let online = if let Some(child) = guard.as_mut() {
            match child.try_wait().map_err(|e| e.to_string())? {
                Some(_) => { *guard = None; false }
                None => true,
            }
        } else { false };

        Ok(ServerSnapshot {
            state: if online { "Online" } else { "Offline" }.into(),
            health: if online { "Good" } else { "Offline" }.into(),
            cpu_load_percent: 0.0,
            used_memory_bytes: 0,
            max_memory_bytes: 4 * 1024 * 1024 * 1024,
        })
    }

    pub fn start(&self) -> Result<(), String> {
        let mut guard = self.child.lock().map_err(|_| "server state lock poisoned".to_string())?;
        if guard.is_some() { return Ok(()); }
        let server_dir = std::env::current_dir().map_err(|e| e.to_string())?.join("server");
        let paper = server_dir.join("paper.jar");
        if !paper.is_file() { return Err(format!("Paper server JAR was not found: {}", paper.display())); }
        let child = Command::new("java")
            .current_dir(&server_dir)
            .args(["-Xms1024M", "-Xmx4096M", "-jar", "paper.jar", "nogui"])
            .stdin(Stdio::piped()).stdout(Stdio::null()).stderr(Stdio::null())
            .spawn().map_err(|e| format!("failed to start Paper: {e}"))?;
        *guard = Some(child);
        Ok(())
    }

    pub fn stop(&self) -> Result<(), String> {
        use std::io::Write;
        let mut guard = self.child.lock().map_err(|_| "server state lock poisoned".to_string())?;
        if let Some(child) = guard.as_mut() {
            if let Some(stdin) = child.stdin.as_mut() {
                stdin.write_all(b"stop\n").map_err(|e| e.to_string())?;
                stdin.flush().map_err(|e| e.to_string())?;
            }
        }
        Ok(())
    }

    pub fn restart(&self) -> Result<(), String> {
        self.stop()?;
        let mut guard = self.child.lock().map_err(|_| "server state lock poisoned".to_string())?;
        if let Some(mut child) = guard.take() {
            let _ = child.wait();
        }
        drop(guard);
        self.start()
    }
}
