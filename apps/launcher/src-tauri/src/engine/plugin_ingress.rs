use std::fs::{self, File};
use std::path::Path;
use zip::ZipArchive;

const MAX_PLUGIN_JAR_BYTES: u64 = 1024 * 1024 * 1024;
const MAX_PLUGIN_ARCHIVE_ENTRIES: usize = 100_000;
const MAX_PLUGIN_METADATA_BYTES: u64 = 1024 * 1024;

pub fn validate_selected_jar(path: &Path) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path)
        .map_err(|error| format!("Could not inspect selected plugin JAR: {error}"))?;
    if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
        return Err("LazyBuilder refuses a symbolic link or Windows reparse point as a plugin JAR source.".into());
    }
    if !metadata.file_type().is_file() {
        return Err("Selected plugin JAR is not a regular file.".into());
    }
    if path.extension().and_then(|value| value.to_str()).map(|value| value.eq_ignore_ascii_case("jar")) != Some(true) {
        return Err("Selected plugin file must be a .jar file.".into());
    }
    if metadata.len() == 0 {
        return Err("Selected plugin JAR is empty.".into());
    }
    if metadata.len() > MAX_PLUGIN_JAR_BYTES {
        return Err(format!(
            "Selected plugin JAR is too large for safe inspection ({} MB; maximum {} MB).",
            to_mb(metadata.len()),
            to_mb(MAX_PLUGIN_JAR_BYTES),
        ));
    }

    let file = File::open(path).map_err(|error| format!("Could not open selected plugin JAR: {error}"))?;
    let mut archive = ZipArchive::new(file).map_err(|error| format!("Selected plugin JAR is not a valid ZIP/JAR archive: {error}"))?;
    if archive.len() > MAX_PLUGIN_ARCHIVE_ENTRIES {
        return Err(format!(
            "Selected plugin JAR contains too many archive entries ({}; maximum {}).",
            archive.len(), MAX_PLUGIN_ARCHIVE_ENTRIES
        ));
    }

    let metadata_name = if archive.by_name("plugin.yml").is_ok() {
        "plugin.yml"
    } else if archive.by_name("paper-plugin.yml").is_ok() {
        "paper-plugin.yml"
    } else {
        return Err("JAR does not contain plugin.yml or paper-plugin.yml".into());
    };
    let entry = archive.by_name(metadata_name)
        .map_err(|error| format!("Could not inspect plugin metadata: {error}"))?;
    if entry.size() > MAX_PLUGIN_METADATA_BYTES {
        return Err(format!(
            "Plugin metadata {} is too large for safe parsing ({} bytes; maximum {} bytes).",
            metadata_name,
            entry.size(),
            MAX_PLUGIN_METADATA_BYTES,
        ));
    }
    Ok(())
}

fn to_mb(bytes: u64) -> u64 { bytes.saturating_add(1024 * 1024 - 1) / (1024 * 1024) }

#[cfg(windows)]
fn is_reparse_point(metadata: &fs::Metadata) -> bool {
    use std::os::windows::fs::MetadataExt;
    const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x0400;
    metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0
}

#[cfg(not(windows))]
fn is_reparse_point(_metadata: &fs::Metadata) -> bool { false }

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn plugin_ingress_limits_are_bounded() {
        assert_eq!(MAX_PLUGIN_JAR_BYTES, 1024 * 1024 * 1024);
        assert_eq!(MAX_PLUGIN_METADATA_BYTES, 1024 * 1024);
        assert_eq!(MAX_PLUGIN_ARCHIVE_ENTRIES, 100_000);
    }
}
