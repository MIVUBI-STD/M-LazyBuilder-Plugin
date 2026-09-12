use std::path::{Path, PathBuf};

pub fn workspace_root() -> Result<PathBuf, String> {
    if let Ok(configured) = std::env::var("LAZYBUILDER_WORKSPACE_ROOT") {
        let path = PathBuf::from(configured);
        if path.is_dir() {
            return Ok(path);
        }
        return Err("LAZYBUILDER_WORKSPACE_ROOT does not point to a directory".into());
    }

    if let Ok(executable) = std::env::current_exe() {
        if let Some(parent) = executable.parent() {
            if let Some(root) = find_workspace(parent) {
                return Ok(root);
            }
        }
    }

    let current = std::env::current_dir().map_err(|error| error.to_string())?;
    if let Some(root) = find_workspace(&current) {
        return Ok(root);
    }

    Ok(current)
}

fn find_workspace(start: &Path) -> Option<PathBuf> {
    for ancestor in start.ancestors() {
        let runtime_layout = ancestor.join("server").is_dir();
        let source_layout = ancestor.join("pom.xml").is_file() && ancestor.join("modules").is_dir();
        if runtime_layout || source_layout {
            return Some(ancestor.to_path_buf());
        }
    }
    None
}

pub fn lazybuilder_tools_dir() -> Result<PathBuf, String> {
    Ok(workspace_root()?.join("tools").join("lazybuilder"))
}
