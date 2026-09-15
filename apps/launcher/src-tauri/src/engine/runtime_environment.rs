use std::fs;
use std::path::PathBuf;

/// Prepare a launcher-owned temporary directory before any Java/Gradle/Paper child process starts.
///
/// Local PC validation showed that the inherited Windows TEMP/TMP could make Java loopback pipes fail
/// with `Unable to establish loopback connection` / `Invalid argument: connect`. Keeping the runtime
/// temp inside LazyBuilder makes child-process behavior deterministic without modifying the user's
/// global Windows environment.
pub fn prepare() -> Result<PathBuf, String> {
    let base = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())?;

    let temp = base.join("LazyBuilder").join("temp");
    fs::create_dir_all(&temp)
        .map_err(|error| format!("Could not prepare LazyBuilder runtime temp directory: {error}"))?;

    // This runs at process bootstrap, before Tauri starts worker threads. Child processes inherit it.
    std::env::set_var("TEMP", &temp);
    std::env::set_var("TMP", &temp);
    Ok(temp)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn runtime_temp_is_lazybuilder_scoped() {
        let root = PathBuf::from("C:/Users/Test/AppData/Local");
        let expected = root.join("LazyBuilder").join("temp");
        assert_eq!(expected, PathBuf::from("C:/Users/Test/AppData/Local/LazyBuilder/temp"));
    }
}
