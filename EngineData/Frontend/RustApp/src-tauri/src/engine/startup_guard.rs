use crate::engine::resource_settings;
use sysinfo::System;

const MB: u64 = 1024 * 1024;

/// One-shot startup safety check. This deliberately validates only the startup heap (Xms)
/// plus free-system headroom; Xmx is a ceiling and is not reserved up front by the JVM.
pub fn ensure_memory_headroom() -> Result<(), String> {
    let profile = resource_settings::profile()?;
    let mut system = System::new_all();
    system.refresh_memory();

    let available_mb = system.available_memory() / MB;
    let total_mb = (system.total_memory() / MB).max(1);
    let reserve_mb = startup_reserve_mb(total_mb);
    let required_mb = profile.current_min_memory_mb.saturating_add(reserve_mb);

    if available_mb < required_mb {
        return Err(format!(
            "Not enough available RAM to start Paper safely. Available: {} MB; startup heap: {} MB; required free reserve: {} MB. Close memory-heavy applications or lower the server RAM profile, then try again.",
            available_mb, profile.current_min_memory_mb, reserve_mb
        ));
    }

    Ok(())
}

fn startup_reserve_mb(total_mb: u64) -> u64 {
    if total_mb <= 8192 {
        1024
    } else if total_mb <= 16384 {
        1536
    } else {
        2048
    }
}

#[cfg(test)]
mod tests {
    use super::startup_reserve_mb;

    #[test]
    fn reserve_scales_conservatively_with_host_memory() {
        assert_eq!(startup_reserve_mb(8192), 1024);
        assert_eq!(startup_reserve_mb(16384), 1536);
        assert_eq!(startup_reserve_mb(32768), 2048);
    }
}
