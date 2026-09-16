use serde_json::Value;
use sha2::{Digest, Sha256};
use std::fs::{self, OpenOptions};
use std::io::Read;
use std::path::{Path, PathBuf};

const PAPER_VERSION: &str = "1.21.4";
const USER_AGENT: &str = concat!("LazyBuilder/", env!("CARGO_PKG_VERSION"), " (https://github.com/halokaryamedia-source/LazyBuilder-Plugin)");
const BUILDS_URL: &str = "https://fill.papermc.io/v3/projects/paper/versions/1.21.4/builds";
const MAX_PAPER_JAR_BYTES: u64 = 256 * 1024 * 1024;

#[derive(Clone, Debug)]
pub struct PaperRelease {
    pub build: u64,
    pub url: String,
    pub sha256: String,
}

pub fn ensure_for_workspace(workspace: &Path) -> Result<u64, String> {
    let release = latest_stable()?;
    ensure_release_for_workspace(workspace, &release)?;
    Ok(release.build)
}

pub fn ensure_release_for_workspace(workspace: &Path, release: &PaperRelease) -> Result<(), String> {
    let cache = cache_path(release)?;
    if !cache.is_file() || sha256_file(&cache)? != release.sha256 {
        download_verified(&release.url, &release.sha256, &cache)?;
    }

    let server_dir = workspace.join("server");
    fs::create_dir_all(&server_dir).map_err(|e| e.to_string())?;
    let target = server_dir.join("paper.jar");
    if target.is_file() && sha256_file(&target)? == release.sha256 {
        return Ok(());
    }

    let temporary = target.with_extension("jar.tmp");
    remove_regular_file_if_exists(&temporary, "Paper publish staging file")?;
    let mut source = fs::File::open(&cache).map_err(|e| e.to_string())?;
    let mut output = OpenOptions::new().create_new(true).write(true).open(&temporary).map_err(|e| e.to_string())?;
    std::io::copy(&mut source, &mut output).map_err(|e| e.to_string())?;
    output.sync_all().map_err(|e| e.to_string())?;
    drop(output);
    replace_file(&temporary, &target)
}

pub fn latest_stable() -> Result<PaperRelease, String> {
    let response = ureq::get(BUILDS_URL)
        .set("User-Agent", USER_AGENT)
        .call()
        .map_err(|e| format!("PaperMC build lookup failed: {e}"))?;
    let value: Value = response.into_json().map_err(|e| e.to_string())?;
    let builds = value.as_array().ok_or_else(|| "Unexpected PaperMC response".to_string())?;
    let stable = builds.iter().find(|item| item.get("channel").and_then(Value::as_str) == Some("STABLE"))
        .ok_or_else(|| format!("No stable Paper build is available for Minecraft {PAPER_VERSION}"))?;

    let build = stable.get("id").and_then(Value::as_u64)
        .or_else(|| stable.get("number").and_then(Value::as_u64))
        .ok_or_else(|| "Paper build number is missing".to_string())?;
    let download = stable.get("downloads")
        .and_then(|v| v.get("server:default"))
        .ok_or_else(|| "Paper server download metadata is missing".to_string())?;
    let url = download.get("url").and_then(Value::as_str)
        .ok_or_else(|| "Paper download URL is missing".to_string())?
        .to_string();
    let sha256 = download.get("checksums")
        .and_then(|v| v.get("sha256"))
        .and_then(Value::as_str)
        .ok_or_else(|| "Paper SHA-256 is missing".to_string())?
        .to_lowercase();
    Ok(PaperRelease { build, url, sha256 })
}

fn cache_path(release: &PaperRelease) -> Result<PathBuf, String> {
    let base = app_data_root()?.join("cache").join("paper").join(PAPER_VERSION);
    fs::create_dir_all(&base).map_err(|e| e.to_string())?;
    Ok(base.join(format!("paper-{PAPER_VERSION}-{}.jar", release.build)))
}

fn app_data_root() -> Result<PathBuf, String> {
    std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .map(|p| p.join("LazyBuilder"))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())
}

fn download_verified(url: &str, expected: &str, destination: &Path) -> Result<(), String> {
    if let Some(parent) = destination.parent() {
        fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    let temp = destination.with_extension("download");
    remove_regular_file_if_exists(&temp, "Paper download staging file")?;
    let response = ureq::get(url)
        .set("User-Agent", USER_AGENT)
        .call()
        .map_err(|e| format!("Paper download failed: {e}"))?;
    if let Some(length) = response.header("Content-Length").and_then(|value| value.parse::<u64>().ok()) {
        if length > MAX_PAPER_JAR_BYTES {
            return Err(format!("Paper download is unexpectedly large ({length} bytes)"));
        }
    }
    let mut reader = response.into_reader().take(MAX_PAPER_JAR_BYTES.saturating_add(1));
    let mut output = OpenOptions::new().create_new(true).write(true).open(&temp).map_err(|e| e.to_string())?;
    let copied = std::io::copy(&mut reader, &mut output).map_err(|e| e.to_string())?;
    if copied > MAX_PAPER_JAR_BYTES {
        drop(output);
        let _ = fs::remove_file(&temp);
        return Err("Paper download exceeded the maximum allowed JAR size".into());
    }
    output.sync_all().map_err(|e| e.to_string())?;
    drop(output);
    let actual = sha256_file(&temp)?;
    if actual != expected {
        let _ = fs::remove_file(&temp);
        return Err("Paper download failed SHA-256 verification".into());
    }
    replace_file(&temp, destination)
}

fn sha256_file(path: &Path) -> Result<String, String> {
    let mut file = fs::File::open(path).map_err(|e| e.to_string())?;
    let mut digest = Sha256::new();
    let mut buffer = [0u8; 64 * 1024];
    loop {
        let count = file.read(&mut buffer).map_err(|e| e.to_string())?;
        if count == 0 { break; }
        digest.update(&buffer[..count]);
    }
    Ok(format!("{:x}", digest.finalize()))
}

fn replace_file(source: &Path, destination: &Path) -> Result<(), String> {
    ensure_regular_file(source, "Paper staging file")?;
    if destination.exists() {
        ensure_regular_file(destination, "Paper JAR")?;
        let backup = destination.with_extension("previous");
        remove_regular_file_if_exists(&backup, "previous Paper JAR")?;
        fs::rename(destination, &backup).map_err(|e| e.to_string())?;
        match fs::rename(source, destination) {
            Ok(()) => { let _ = fs::remove_file(backup); Ok(()) }
            Err(error) => { let _ = fs::rename(&backup, destination); Err(error.to_string()) }
        }
    } else {
        fs::rename(source, destination).map_err(|e| e.to_string())
    }
}

fn ensure_regular_file(path: &Path, label: &str) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path).map_err(|e| format!("Could not inspect {label}: {e}"))?;
    if metadata.file_type().is_symlink() || !metadata.file_type().is_file() {
        return Err(format!("LazyBuilder expected {label} to be a regular file"));
    }
    Ok(())
}

fn remove_regular_file_if_exists(path: &Path, label: &str) -> Result<(), String> {
    match fs::symlink_metadata(path) {
        Ok(_) => {
            ensure_regular_file(path, label)?;
            fs::remove_file(path).map_err(|e| format!("Could not remove {label}: {e}"))
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(format!("Could not inspect {label}: {error}")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn paper_download_bound_is_finite() {
        assert!(MAX_PAPER_JAR_BYTES > 0);
    }

    #[test]
    fn user_agent_tracks_launcher_version() {
        assert!(USER_AGENT.contains(env!("CARGO_PKG_VERSION")));
    }
}
