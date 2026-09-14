use serde_json::Value;
use sha2::{Digest, Sha256};
use std::fs;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};

const PAPER_VERSION: &str = "1.21.4";
const USER_AGENT: &str = "LazyBuilder/0.1.0 (https://github.com/halokaryamedia-source/LazyBuilder-Plugin)";
const BUILDS_URL: &str = "https://fill.papermc.io/v3/projects/paper/versions/1.21.4/builds";

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
    fs::copy(&cache, &temporary).map_err(|e| e.to_string())?;
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
    let response = ureq::get(url)
        .set("User-Agent", USER_AGENT)
        .call()
        .map_err(|e| format!("Paper download failed: {e}"))?;
    let mut reader = response.into_reader();
    let mut output = fs::File::create(&temp).map_err(|e| e.to_string())?;
    std::io::copy(&mut reader, &mut output).map_err(|e| e.to_string())?;
    output.flush().map_err(|e| e.to_string())?;
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
    if destination.exists() {
        let backup = destination.with_extension("previous");
        let _ = fs::remove_file(&backup);
        fs::rename(destination, &backup).map_err(|e| e.to_string())?;
        match fs::rename(source, destination) {
            Ok(()) => { let _ = fs::remove_file(backup); Ok(()) }
            Err(error) => { let _ = fs::rename(&backup, destination); Err(error.to_string()) }
        }
    } else {
        fs::rename(source, destination).map_err(|e| e.to_string())
    }
}
