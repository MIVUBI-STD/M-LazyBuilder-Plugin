use crate::engine::workspace_registry;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::BTreeMap;
use std::fs::{self, File, OpenOptions};
use std::io::{BufReader, BufWriter, Read, Write};
use std::path::{Component, Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};
use sysinfo::Disks;

const BACKUP_SCHEMA_VERSION: u32 = 2;
const MIN_SUPPORTED_BACKUP_SCHEMA_VERSION: u32 = 1;
const MIN_BACKUP_HEADROOM_BYTES: u64 = 512 * 1024 * 1024;
const COPY_BUFFER_BYTES: usize = 1024 * 1024;
static NEXT_BACKUP_ID: AtomicU64 = AtomicU64::new(1);

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerBackupSummary {
    pub id: String,
    pub workspace_id: String,
    pub workspace_name: String,
    pub path: String,
    pub created_unix_seconds: u64,
    pub source_bytes: u64,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerBackupEstimate {
    pub source_bytes: u64,
    pub required_bytes: u64,
    pub available_bytes: Option<u64>,
}

#[derive(Clone, Debug, Serialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum BackupIntegrityStatus { Verified, LegacyUnverified }

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BackupIntegrityReport {
    pub status: BackupIntegrityStatus,
    pub files_verified: u64,
    pub bytes_verified: u64,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct BackupIntegrityEntry {
    path: String,
    bytes: u64,
    sha256: String,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct BackupManifest {
    schema_version: u32,
    id: String,
    workspace_id: String,
    workspace_name: String,
    created_unix_seconds: u64,
    source_bytes: u64,
    #[serde(default)]
    integrity_algorithm: Option<String>,
    #[serde(default)]
    integrity_files: Vec<BackupIntegrityEntry>,
}

pub fn list(workspace_id: &str) -> Result<Vec<ServerBackupSummary>, String> {
    let entry = workspace_registry::get(workspace_id)?;
    let root = validated_workspace_root(&entry)?;
    let backup_root = backup_root_for(&root, workspace_id)?;
    if !backup_root.exists() {
        return Ok(Vec::new());
    }
    reject_reparse_point(&backup_root)?;
    if !backup_root.is_dir() {
        return Err("Server backup location is not a directory".into());
    }

    let mut backups = Vec::new();
    for item in fs::read_dir(&backup_root).map_err(|error| format!("Could not read server backups: {error}"))? {
        let item = item.map_err(|error| error.to_string())?;
        let metadata = fs::symlink_metadata(item.path()).map_err(|error| error.to_string())?;
        if !metadata.file_type().is_dir() || is_reparse_point(&metadata) {
            continue;
        }
        let backup_path = item.path();
        let manifest = match read_backup_manifest(&backup_path) {
            Ok(value) => value,
            Err(_) => continue,
        };
        if manifest.workspace_id != workspace_id || !supported_backup_schema(manifest.schema_version) {
            continue;
        }
        let snapshot = backup_path.join("workspace");
        if !snapshot.is_dir() || !backup_snapshot_identity_matches(&snapshot, workspace_id)? {
            continue;
        }
        backups.push(summary_from_manifest(&backup_path, manifest));
    }
    backups.sort_by(|left, right| right.created_unix_seconds.cmp(&left.created_unix_seconds));
    Ok(backups)
}

pub fn estimate(workspace_id: &str) -> Result<ServerBackupEstimate, String> {
    let entry = workspace_registry::get(workspace_id)?;
    let source = validated_workspace_root(&entry)?;
    let destination = backup_root_for(&source, workspace_id)?;
    fs::create_dir_all(&destination).map_err(|error| format!("Could not prepare backup storage: {error}"))?;
    reject_reparse_point(&destination)?;
    let source_bytes = directory_size_filtered(&source, Path::new(""))?;
    let margin = (source_bytes / 10).max(MIN_BACKUP_HEADROOM_BYTES);
    let required_bytes = source_bytes.saturating_add(margin);
    let available_bytes = available_space_for(&destination);
    Ok(ServerBackupEstimate { source_bytes, required_bytes, available_bytes })
}

pub fn create_tracked<F>(workspace_id: &str, mut progress: F) -> Result<ServerBackupSummary, String>
where
    F: FnMut(&str, &str, &str, Option<(u64, u64)>),
{
    let entry = workspace_registry::get(workspace_id)?;
    let source = validated_workspace_root(&entry)?;
    let backup_root = backup_root_for(&source, workspace_id)?;
    fs::create_dir_all(&backup_root).map_err(|error| format!("Could not create backup storage: {error}"))?;
    reject_reparse_point(&backup_root)?;

    progress("preflight", "Checking backup storage", "Calculating server size and available disk space.", None);
    let source_bytes = directory_size_filtered(&source, Path::new(""))?;
    let margin = (source_bytes / 10).max(MIN_BACKUP_HEADROOM_BYTES);
    let required_bytes = source_bytes.saturating_add(margin);
    if let Some(available) = available_space_for(&backup_root) {
        if available < required_bytes {
            return Err(format!(
                "Not enough storage to back up this server. Required approximately {} MB; available {} MB.",
                bytes_to_mb(required_bytes),
                bytes_to_mb(available)
            ));
        }
    }

    let created = now_unix_seconds();
    let sequence = NEXT_BACKUP_ID.fetch_add(1, Ordering::Relaxed);
    let id = format!("backup-{}-{}-{sequence}", now_unix_millis(), std::process::id());
    let staging = backup_root.join(format!(".creating-{id}"));
    let final_root = backup_root.join(&id);
    if staging.exists() || final_root.exists() {
        return Err("LazyBuilder backup staging path already exists; retry in a moment".into());
    }
    let snapshot = staging.join("workspace");
    fs::create_dir_all(&snapshot).map_err(|error| format!("Could not create backup staging directory: {error}"))?;

    let staged_result = (|| -> Result<BackupManifest, String> {
        let mut integrity_files = Vec::new();
        copy_directory_filtered(
            &source,
            &snapshot,
            Path::new(""),
            source_bytes,
            &mut integrity_files,
            &mut progress,
        )?;

        progress("validating", "Validating backup", "Verifying the staged workspace identity and integrity metadata before publishing the restore point.", None);
        if !backup_snapshot_identity_matches(&snapshot, workspace_id)? {
            return Err("Backup validation failed because the staged workspace identity does not match the source server".into());
        }

        let manifest = BackupManifest {
            schema_version: BACKUP_SCHEMA_VERSION,
            id: id.clone(),
            workspace_id: workspace_id.to_string(),
            workspace_name: entry.name.clone(),
            created_unix_seconds: created,
            source_bytes,
            integrity_algorithm: Some("sha256".into()),
            integrity_files,
        };
        validate_integrity_manifest_shape(&manifest)?;
        write_backup_manifest(&staging, &manifest)?;
        Ok(manifest)
    })();

    let manifest = match staged_result {
        Ok(value) => value,
        Err(error) => {
            let _ = fs::remove_dir_all(&staging);
            return Err(error);
        }
    };

    progress("publishing", "Publishing backup", "Publishing the validated restore point atomically.", Some((source_bytes, source_bytes)));
    if let Err(error) = fs::rename(&staging, &final_root) {
        let _ = fs::remove_dir_all(&staging);
        return Err(format!("Could not publish server backup: {error}"));
    }

    Ok(summary_from_manifest(&final_root, manifest))
}

pub fn verify(workspace_id: &str, backup_id: &str) -> Result<BackupIntegrityReport, String> {
    validate_backup_id(backup_id)?;
    let entry = workspace_registry::get(workspace_id)?;
    let root = validated_workspace_root(&entry)?;
    let backup_root = backup_root_for(&root, workspace_id)?;
    if !backup_root.exists() { return Err("Server backup was not found".into()); }
    reject_reparse_point(&backup_root)?;

    let target = backup_root.join(backup_id);
    if !target.is_dir() { return Err("Server backup was not found".into()); }
    reject_reparse_point(&target)?;
    let manifest = read_backup_manifest(&target)?;
    validate_backup_manifest_identity(&manifest, workspace_id, backup_id)?;
    let snapshot = target.join("workspace");
    if !snapshot.is_dir() || !backup_snapshot_identity_matches(&snapshot, workspace_id)? {
        return Err("Backup workspace identity does not match the selected server".into());
    }
    verify_snapshot_integrity(&snapshot, &manifest)
}

pub fn delete(workspace_id: &str, backup_id: &str) -> Result<(), String> {
    validate_backup_id(backup_id)?;
    let entry = workspace_registry::get(workspace_id)?;
    let root = validated_workspace_root(&entry)?;
    let backup_root = backup_root_for(&root, workspace_id)?;
    if !backup_root.exists() {
        return Err("Server backup was not found".into());
    }
    reject_reparse_point(&backup_root)?;
    let target = backup_root.join(backup_id);
    if !target.exists() {
        return Err("Server backup was not found".into());
    }
    reject_reparse_point(&target)?;
    if !target.is_dir() {
        return Err("Server backup is not a directory".into());
    }
    let manifest = read_backup_manifest(&target)?;
    validate_backup_manifest_identity(&manifest, workspace_id, backup_id)?;
    let canonical_root = backup_root.canonicalize().map_err(|error| error.to_string())?;
    let canonical_target = target.canonicalize().map_err(|error| error.to_string())?;
    if canonical_target.parent() != Some(canonical_root.as_path()) {
        return Err("LazyBuilder refused an unsafe backup deletion path".into());
    }
    fs::remove_dir_all(canonical_target).map_err(|error| format!("Could not delete server backup: {error}"))
}

/// Startup-only recovery for unpublished backup staging left by an interrupted Launcher.
/// No backup operation is active when startup reconciliation runs, so matching staging
/// directories can be removed without racing a live copy.
pub fn recover_staging() -> Result<u32, String> {
    let mut removed = 0u32;
    for entry in workspace_registry::list()? {
        let Ok(root) = validated_workspace_root(&entry) else { continue; };
        let backup_root = backup_root_for(&root, &entry.id)?;
        if !backup_root.exists() {
            continue;
        }
        reject_reparse_point(&backup_root)?;
        for item in fs::read_dir(&backup_root).map_err(|error| error.to_string())? {
            let item = item.map_err(|error| error.to_string())?;
            let name = item.file_name().to_string_lossy().to_string();
            if !name.starts_with(".creating-backup-") {
                continue;
            }
            let metadata = fs::symlink_metadata(item.path()).map_err(|error| error.to_string())?;
            if is_reparse_point(&metadata) {
                return Err(format!("Unsafe reparse point found in backup staging: {}", item.path().display()));
            }
            if metadata.file_type().is_dir() {
                fs::remove_dir_all(item.path()).map_err(|error| format!("Could not clean interrupted backup staging: {error}"))?;
                removed = removed.saturating_add(1);
            }
        }
    }
    Ok(removed)
}

fn backup_root_for(workspace: &Path, workspace_id: &str) -> Result<PathBuf, String> {
    let parent = workspace.parent().ok_or_else(|| "Server workspace has no parent directory for backup storage".to_string())?;
    Ok(parent.join(".lazybuilder-backups").join(workspace_id))
}

fn validated_workspace_root(entry: &workspace_registry::WorkspaceEntry) -> Result<PathBuf, String> {
    let root = PathBuf::from(&entry.path)
        .canonicalize()
        .map_err(|error| format!("Could not resolve registered server workspace: {error}"))?;
    if !root.is_dir() {
        return Err("Registered server workspace is not a directory".into());
    }
    if !backup_snapshot_identity_matches(&root, &entry.id)? {
        return Err("Registered server identity does not match its workspace manifest".into());
    }
    Ok(root)
}

fn backup_snapshot_identity_matches(root: &Path, workspace_id: &str) -> Result<bool, String> {
    let path = root.join("tools").join("lazybuilder").join("config").join("workspace.json");
    let text = fs::read_to_string(path).map_err(|error| format!("Could not read workspace manifest: {error}"))?;
    let value: serde_json::Value = serde_json::from_str(&text).map_err(|error| format!("Could not parse workspace manifest: {error}"))?;
    Ok(value.get("workspaceId").and_then(serde_json::Value::as_str) == Some(workspace_id))
}

fn validate_backup_manifest_identity(manifest: &BackupManifest, workspace_id: &str, backup_id: &str) -> Result<(), String> {
    if !supported_backup_schema(manifest.schema_version) || manifest.workspace_id != workspace_id || manifest.id != backup_id {
        return Err("LazyBuilder refused a backup whose identity or schema does not match the selected server".into());
    }
    Ok(())
}

fn supported_backup_schema(schema: u32) -> bool {
    (MIN_SUPPORTED_BACKUP_SCHEMA_VERSION..=BACKUP_SCHEMA_VERSION).contains(&schema)
}

fn write_backup_manifest(root: &Path, manifest: &BackupManifest) -> Result<(), String> {
    let path = root.join("backup.json");
    let temp = root.join("backup.json.tmp");
    let text = serde_json::to_string_pretty(manifest).map_err(|error| error.to_string())?;
    {
        let mut file = OpenOptions::new().create(true).truncate(true).write(true).open(&temp)
            .map_err(|error| format!("Could not write backup metadata staging file: {error}"))?;
        file.write_all(text.as_bytes()).map_err(|error| error.to_string())?;
        file.sync_all().map_err(|error| format!("Could not flush backup metadata: {error}"))?;
    }
    fs::rename(temp, path).map_err(|error| error.to_string())
}

fn read_backup_manifest(root: &Path) -> Result<BackupManifest, String> {
    let text = fs::read_to_string(root.join("backup.json")).map_err(|error| format!("Could not read backup metadata: {error}"))?;
    serde_json::from_str(&text).map_err(|error| format!("Could not parse backup metadata: {error}"))
}

fn summary_from_manifest(root: &Path, manifest: BackupManifest) -> ServerBackupSummary {
    ServerBackupSummary {
        id: manifest.id,
        workspace_id: manifest.workspace_id,
        workspace_name: manifest.workspace_name,
        path: root.display().to_string(),
        created_unix_seconds: manifest.created_unix_seconds,
        source_bytes: manifest.source_bytes,
    }
}

fn copy_directory_filtered<F>(
    source: &Path,
    destination: &Path,
    relative: &Path,
    total: u64,
    integrity_files: &mut Vec<BackupIntegrityEntry>,
    progress: &mut F,
) -> Result<u64, String>
where
    F: FnMut(&str, &str, &str, Option<(u64, u64)>),
{
    copy_directory_filtered_inner(source, destination, relative, total, 0, integrity_files, progress)
}

fn copy_directory_filtered_inner<F>(
    source: &Path,
    destination: &Path,
    relative: &Path,
    total: u64,
    mut copied: u64,
    integrity_files: &mut Vec<BackupIntegrityEntry>,
    progress: &mut F,
) -> Result<u64, String>
where
    F: FnMut(&str, &str, &str, Option<(u64, u64)>),
{
    for item in fs::read_dir(source).map_err(|error| format!("Could not read {}: {error}", source.display()))? {
        let item = item.map_err(|error| error.to_string())?;
        let metadata = fs::symlink_metadata(item.path()).map_err(|error| error.to_string())?;
        let rel = relative.join(item.file_name());
        if should_skip_backup_path(&rel) { continue; }
        if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
            return Err(format!("Cannot safely back up a server containing a symbolic link or Windows reparse point: {}", item.path().display()));
        }
        let target = destination.join(item.file_name());
        if metadata.file_type().is_dir() {
            fs::create_dir(&target).map_err(|error| format!("Could not create {}: {error}", target.display()))?;
            copied = copy_directory_filtered_inner(&item.path(), &target, &rel, total, copied, integrity_files, progress)?;
        } else if metadata.file_type().is_file() {
            let (bytes, sha256) = copy_file_with_hash(&item.path(), &target, &rel, total, &mut copied, progress)?;
            fs::set_permissions(&target, metadata.permissions()).map_err(|error| format!("Could not preserve file permissions for {}: {error}", rel.display()))?;
            integrity_files.push(BackupIntegrityEntry {
                path: normalized_relative_path(&rel)?,
                bytes,
                sha256,
            });
        }
    }
    Ok(copied)
}

fn copy_file_with_hash<F>(
    source: &Path,
    destination: &Path,
    relative: &Path,
    total: u64,
    copied: &mut u64,
    progress: &mut F,
) -> Result<(u64, String), String>
where
    F: FnMut(&str, &str, &str, Option<(u64, u64)>),
{
    let source_file = File::open(source).map_err(|error| format!("Could not open {} for backup: {error}", source.display()))?;
    let destination_file = OpenOptions::new().create_new(true).write(true).open(destination)
        .map_err(|error| format!("Could not create backup file {}: {error}", destination.display()))?;
    let mut reader = BufReader::with_capacity(COPY_BUFFER_BYTES, source_file);
    let mut writer = BufWriter::with_capacity(COPY_BUFFER_BYTES, destination_file);
    let mut hasher = Sha256::new();
    let mut buffer = vec![0u8; COPY_BUFFER_BYTES];
    let mut file_bytes = 0u64;

    loop {
        let read = reader.read(&mut buffer).map_err(|error| format!("Could not read {} during backup: {error}", source.display()))?;
        if read == 0 { break; }
        writer.write_all(&buffer[..read]).map_err(|error| format!("Could not write {} during backup: {error}", destination.display()))?;
        hasher.update(&buffer[..read]);
        let read = read as u64;
        file_bytes = file_bytes.saturating_add(read);
        *copied = copied.saturating_add(read);
        progress("copying", "Copying server files", &relative.to_string_lossy(), Some(((*copied).min(total), total)));
    }
    writer.flush().map_err(|error| format!("Could not flush backup file {}: {error}", destination.display()))?;
    writer.get_ref().sync_all().map_err(|error| format!("Could not sync backup file {}: {error}", destination.display()))?;
    Ok((file_bytes, format!("{:x}", hasher.finalize())))
}

fn verify_snapshot_integrity(snapshot: &Path, manifest: &BackupManifest) -> Result<BackupIntegrityReport, String> {
    if manifest.schema_version == 1 {
        return Ok(BackupIntegrityReport { status: BackupIntegrityStatus::LegacyUnverified, files_verified: 0, bytes_verified: 0 });
    }
    validate_integrity_manifest_shape(manifest)?;
    if manifest.integrity_algorithm.as_deref() != Some("sha256") {
        return Err("Backup integrity algorithm is unsupported".into());
    }

    let mut expected = BTreeMap::new();
    for item in &manifest.integrity_files {
        validate_integrity_path(&item.path)?;
        if expected.insert(item.path.clone(), item).is_some() {
            return Err(format!("Backup integrity metadata contains a duplicate path: {}", item.path));
        }
    }

    let actual = collect_snapshot_files(snapshot, Path::new(""))?;
    if actual.len() != expected.len() {
        return Err(format!("Backup integrity check failed: expected {} files, found {}", expected.len(), actual.len()));
    }

    let mut bytes_verified = 0u64;
    for (relative, path) in actual {
        let item = expected.get(&relative).ok_or_else(|| format!("Backup contains an unexpected file: {relative}"))?;
        let metadata = fs::symlink_metadata(&path).map_err(|error| error.to_string())?;
        if metadata.len() != item.bytes {
            return Err(format!("Backup integrity check failed for {relative}: expected {} bytes, found {}", item.bytes, metadata.len()));
        }
        let hash = hash_file(&path)?;
        if !hash.eq_ignore_ascii_case(&item.sha256) {
            return Err(format!("Backup integrity check failed for {relative}: SHA-256 mismatch"));
        }
        bytes_verified = bytes_verified.saturating_add(item.bytes);
    }

    if bytes_verified != manifest.source_bytes {
        return Err(format!("Backup integrity check failed: manifest source size is {} bytes but verified files total {} bytes", manifest.source_bytes, bytes_verified));
    }

    Ok(BackupIntegrityReport {
        status: BackupIntegrityStatus::Verified,
        files_verified: expected.len() as u64,
        bytes_verified,
    })
}

fn validate_integrity_manifest_shape(manifest: &BackupManifest) -> Result<(), String> {
    if manifest.schema_version == 1 { return Ok(()); }
    if manifest.schema_version != BACKUP_SCHEMA_VERSION {
        return Err(format!("Backup schema {} is unsupported", manifest.schema_version));
    }
    if manifest.integrity_algorithm.as_deref() != Some("sha256") {
        return Err("Backup schema 2 requires SHA-256 integrity metadata".into());
    }
    let mut total = 0u64;
    let mut paths = BTreeMap::new();
    for item in &manifest.integrity_files {
        validate_integrity_path(&item.path)?;
        if item.sha256.len() != 64 || !item.sha256.chars().all(|character| character.is_ascii_hexdigit()) {
            return Err(format!("Backup integrity metadata has an invalid SHA-256 value for {}", item.path));
        }
        if paths.insert(item.path.clone(), ()).is_some() {
            return Err(format!("Backup integrity metadata contains a duplicate path: {}", item.path));
        }
        total = total.saturating_add(item.bytes);
    }
    if total != manifest.source_bytes {
        return Err(format!("Backup integrity metadata totals {total} bytes but sourceBytes is {}", manifest.source_bytes));
    }
    Ok(())
}

fn validate_integrity_path(value: &str) -> Result<(), String> {
    if value.is_empty() { return Err("Backup integrity path may not be empty".into()); }
    let path = Path::new(value);
    if path.is_absolute() { return Err(format!("Backup integrity path must be relative: {value}")); }
    for component in path.components() {
        if !matches!(component, Component::Normal(_)) {
            return Err(format!("Backup integrity path is unsafe: {value}"));
        }
    }
    Ok(())
}

fn normalized_relative_path(path: &Path) -> Result<String, String> {
    let value = path.to_string_lossy().replace('\\', "/");
    validate_integrity_path(&value)?;
    Ok(value)
}

fn collect_snapshot_files(root: &Path, relative: &Path) -> Result<BTreeMap<String, PathBuf>, String> {
    let mut files = BTreeMap::new();
    for item in fs::read_dir(root).map_err(|error| format!("Could not inspect backup snapshot {}: {error}", root.display()))? {
        let item = item.map_err(|error| error.to_string())?;
        let metadata = fs::symlink_metadata(item.path()).map_err(|error| error.to_string())?;
        if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
            return Err(format!("Backup integrity check refused a symbolic link or Windows reparse point: {}", item.path().display()));
        }
        let rel = relative.join(item.file_name());
        if metadata.file_type().is_dir() {
            for (path, target) in collect_snapshot_files(&item.path(), &rel)? {
                if files.insert(path.clone(), target).is_some() {
                    return Err(format!("Backup snapshot contains a duplicate normalized path: {path}"));
                }
            }
        } else if metadata.file_type().is_file() {
            let normalized = normalized_relative_path(&rel)?;
            if files.insert(normalized.clone(), item.path()).is_some() {
                return Err(format!("Backup snapshot contains a duplicate normalized path: {normalized}"));
            }
        }
    }
    Ok(files)
}

fn hash_file(path: &Path) -> Result<String, String> {
    let file = File::open(path).map_err(|error| format!("Could not open backup file {} for verification: {error}", path.display()))?;
    let mut reader = BufReader::with_capacity(COPY_BUFFER_BYTES, file);
    let mut buffer = vec![0u8; COPY_BUFFER_BYTES];
    let mut hasher = Sha256::new();
    loop {
        let read = reader.read(&mut buffer).map_err(|error| format!("Could not read backup file {} for verification: {error}", path.display()))?;
        if read == 0 { break; }
        hasher.update(&buffer[..read]);
    }
    Ok(format!("{:x}", hasher.finalize()))
}

fn directory_size_filtered(root: &Path, relative: &Path) -> Result<u64, String> {
    let mut total = 0u64;
    for item in fs::read_dir(root).map_err(|error| format!("Could not inspect {}: {error}", root.display()))? {
        let item = item.map_err(|error| error.to_string())?;
        let metadata = fs::symlink_metadata(item.path()).map_err(|error| error.to_string())?;
        let rel = relative.join(item.file_name());
        if should_skip_backup_path(&rel) { continue; }
        if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
            return Err(format!("Cannot safely back up a server containing a symbolic link or Windows reparse point: {}", item.path().display()));
        }
        if metadata.file_type().is_dir() {
            total = total.saturating_add(directory_size_filtered(&item.path(), &rel)?);
        } else if metadata.file_type().is_file() {
            total = total.saturating_add(metadata.len());
        }
    }
    Ok(total)
}

fn should_skip_backup_path(relative: &Path) -> bool {
    let normalized = relative.to_string_lossy().replace('\\', "/").to_ascii_lowercase();
    if normalized == "world-system/work" || normalized.starts_with("world-system/work/") { return true; }
    if normalized == "tools/lazybuilder/cache" || normalized.starts_with("tools/lazybuilder/cache/") { return true; }
    if normalized == "tools/lazybuilder/logs" || normalized.starts_with("tools/lazybuilder/logs/") { return true; }
    let file_name = relative.file_name().and_then(|value| value.to_str()).unwrap_or("").to_ascii_lowercase();
    file_name.ends_with(".tmp")
        || file_name.ends_with(".incoming")
        || file_name.ends_with(".lock")
        || file_name == "server-process.json"
        || file_name == "server-start.lock"
}

fn validate_backup_id(value: &str) -> Result<(), String> {
    if value.starts_with("backup-") && value.chars().all(|character| character.is_ascii_alphanumeric() || character == '-') {
        Ok(())
    } else {
        Err("Invalid server backup identity".into())
    }
}

fn available_space_for(path: &Path) -> Option<u64> {
    let disks = Disks::new_with_refreshed_list();
    disks.list().iter().filter(|disk| path.starts_with(disk.mount_point())).max_by_key(|disk| disk.mount_point().as_os_str().len()).map(|disk| disk.available_space())
}

fn bytes_to_mb(bytes: u64) -> u64 { bytes.saturating_add(1024 * 1024 - 1) / (1024 * 1024) }
fn now_unix_seconds() -> u64 { SystemTime::now().duration_since(UNIX_EPOCH).map(|value| value.as_secs()).unwrap_or_default() }
fn now_unix_millis() -> u128 { SystemTime::now().duration_since(UNIX_EPOCH).map(|value| value.as_millis()).unwrap_or_default() }

#[cfg(windows)]
fn is_reparse_point(metadata: &fs::Metadata) -> bool {
    use std::os::windows::fs::MetadataExt;
    const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x0400;
    metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0
}

#[cfg(not(windows))]
fn is_reparse_point(_metadata: &fs::Metadata) -> bool { false }

fn reject_reparse_point(path: &Path) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path).map_err(|error| error.to_string())?;
    if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
        Err(format!("LazyBuilder refused a symbolic link or Windows reparse point for backup safety: {}", path.display()))
    } else {
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn backup_filter_excludes_transient_runtime_paths() {
        assert!(should_skip_backup_path(Path::new("world-system/work/job.tmp")));
        assert!(should_skip_backup_path(Path::new("tools/lazybuilder/cache/server-process.json")));
        assert!(should_skip_backup_path(Path::new("tools/lazybuilder/logs/launcher.log")));
        assert!(!should_skip_backup_path(Path::new("server/plugins/Example/config.yml")));
        assert!(!should_skip_backup_path(Path::new("world-system/worlds/build/region/r.0.0.mca")));
    }

    #[test]
    fn backup_id_validation_is_strict() {
        assert!(validate_backup_id("backup-123-4-1").is_ok());
        assert!(validate_backup_id("../backup-123").is_err());
        assert!(validate_backup_id("backup_123").is_err());
    }

    #[test]
    fn integrity_paths_reject_traversal_and_absolute_paths() {
        assert!(validate_integrity_path("server/paper.jar").is_ok());
        assert!(validate_integrity_path("../server/paper.jar").is_err());
        assert!(validate_integrity_path("/server/paper.jar").is_err());
    }

    #[test]
    fn legacy_backup_schema_remains_supported() {
        assert!(supported_backup_schema(1));
        assert!(supported_backup_schema(2));
        assert!(!supported_backup_schema(3));
    }
}
