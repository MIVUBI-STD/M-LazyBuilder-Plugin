use super::ServerManagerState;
use std::io::Write;

impl ServerManagerState {
    /// Send one command to the stdin of the Paper process currently owned by
    /// this launcher session. Detached processes are intentionally excluded:
    /// after a launcher restart we can verify/stop the process, but we no
    /// longer own its stdin handle and must not invent a second control path.
    pub fn send_console_command(&self, command: &str) -> Result<(), String> {
        let command = command.trim();
        if command.is_empty() {
            return Err("Server command cannot be empty.".into());
        }

        let state = self
            .runtime_state
            .lock()
            .map_err(|_| "server runtime state lock poisoned".to_string())?
            .clone();
        if state != "Online" {
            return Err(format!(
                "Server console is available only while Paper is Online; current state is {state}."
            ));
        }

        let mut child_guard = self
            .child
            .lock()
            .map_err(|_| "server state lock poisoned".to_string())?;
        let child = child_guard
            .as_mut()
            .ok_or_else(|| "LazyBuilder does not currently own a Paper stdin handle.".to_string())?;

        if child
            .try_wait()
            .map_err(|error| format!("Could not inspect Paper before sending the command: {error}"))?
            .is_some()
        {
            return Err("Paper exited before the command could be sent.".into());
        }

        let stdin = child
            .stdin
            .as_mut()
            .ok_or_else(|| "LazyBuilder does not currently own a Paper stdin handle.".to_string())?;
        let line = format!("{command}\n");
        stdin
            .write_all(line.as_bytes())
            .map_err(|error| format!("Could not write the command to Paper stdin: {error}"))?;
        stdin
            .flush()
            .map_err(|error| format!("Could not flush the command to Paper stdin: {error}"))?;
        Ok(())
    }
}
