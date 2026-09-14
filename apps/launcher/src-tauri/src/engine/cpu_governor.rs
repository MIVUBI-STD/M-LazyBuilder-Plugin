use crate::engine::paths;
use std::thread;
use std::time::Duration;
use sysinfo::{Pid, System};

const SAMPLE_INTERVAL: Duration = Duration::from_secs(2);
const HIGH_PRESSURE_PERCENT: f32 = 80.0;
const LOW_PRESSURE_PERCENT: f32 = 60.0;
const HIGH_SAMPLES_REQUIRED: u8 = 3;
const LOW_SAMPLES_REQUIRED: u8 = 5;

#[derive(Clone, Copy, PartialEq, Eq)]
enum PaperPriority {
    Normal,
    BelowNormal,
}

pub fn start() {
    thread::spawn(run);
}

fn run() {
    let mut system = System::new_all();
    let mut managed_pid: Option<Pid> = None;
    let mut current = PaperPriority::Normal;
    let mut high_samples = 0u8;
    let mut low_samples = 0u8;

    loop {
        thread::sleep(SAMPLE_INTERVAL);
        system.refresh_all();

        let Some(paper_pid) = managed_paper_pid(&system) else {
            managed_pid = None;
            current = PaperPriority::Normal;
            high_samples = 0;
            low_samples = 0;
            continue;
        };

        if managed_pid != Some(paper_pid) {
            managed_pid = Some(paper_pid);
            current = PaperPriority::Normal;
            high_samples = 0;
            low_samples = 0;
            let _ = set_paper_priority(paper_pid.as_u32(), PaperPriority::Normal);
        }

        let client_active = minecraft_client_running(&system, paper_pid);
        if !client_active {
            high_samples = 0;
            low_samples = 0;
            if current != PaperPriority::Normal
                && set_paper_priority(paper_pid.as_u32(), PaperPriority::Normal)
            {
                current = PaperPriority::Normal;
            }
            continue;
        }

        let cpu = system.global_cpu_info().cpu_usage();
        if cpu >= HIGH_PRESSURE_PERCENT {
            high_samples = high_samples.saturating_add(1);
            low_samples = 0;
            if high_samples >= HIGH_SAMPLES_REQUIRED && current != PaperPriority::BelowNormal {
                if set_paper_priority(paper_pid.as_u32(), PaperPriority::BelowNormal) {
                    current = PaperPriority::BelowNormal;
                }
                high_samples = 0;
            }
        } else if cpu <= LOW_PRESSURE_PERCENT {
            low_samples = low_samples.saturating_add(1);
            high_samples = 0;
            if low_samples >= LOW_SAMPLES_REQUIRED && current != PaperPriority::Normal {
                if set_paper_priority(paper_pid.as_u32(), PaperPriority::Normal) {
                    current = PaperPriority::Normal;
                }
                low_samples = 0;
            }
        } else {
            high_samples = 0;
            low_samples = 0;
        }
    }
}

fn managed_paper_pid(system: &System) -> Option<Pid> {
    let worlds = paths::worlds_dir().ok()?.display().to_string().to_ascii_lowercase();
    system.processes().iter().find_map(|(pid, process)| {
        let name = process.name().to_ascii_lowercase();
        if !name.contains("java") {
            return None;
        }
        let command = process.cmd().join(" ").to_ascii_lowercase();
        if command.contains("-jar")
            && command.contains("--universe")
            && command.contains("nogui")
            && command.contains(&worlds)
        {
            Some(*pid)
        } else {
            None
        }
    })
}

fn minecraft_client_running(system: &System, paper_pid: Pid) -> bool {
    system.processes().iter().any(|(pid, process)| {
        if *pid == paper_pid {
            return false;
        }
        let name = process.name().to_ascii_lowercase();
        if !name.contains("java") {
            return false;
        }
        let command = process.cmd().join(" ").to_ascii_lowercase();
        command.contains("net.minecraft.client.main.main")
            || command.contains("net.fabricmc.loader.impl.launch.knot.knotclient")
            || command.contains("net.fabricmc.loader.impl.launch.knotclient")
            || (command.contains("--gamedir") && command.contains("--assetsdir"))
    })
}

#[cfg(windows)]
fn set_paper_priority(pid: u32, priority: PaperPriority) -> bool {
    type Handle = *mut core::ffi::c_void;
    const PROCESS_SET_INFORMATION: u32 = 0x0200;
    const NORMAL_PRIORITY_CLASS: u32 = 0x0000_0020;
    const BELOW_NORMAL_PRIORITY_CLASS: u32 = 0x0000_4000;

    #[link(name = "kernel32")]
    extern "system" {
        fn OpenProcess(desired_access: u32, inherit_handle: i32, process_id: u32) -> Handle;
        fn SetPriorityClass(process: Handle, priority_class: u32) -> i32;
        fn CloseHandle(object: Handle) -> i32;
    }

    let class = match priority {
        PaperPriority::Normal => NORMAL_PRIORITY_CLASS,
        PaperPriority::BelowNormal => BELOW_NORMAL_PRIORITY_CLASS,
    };

    unsafe {
        let handle = OpenProcess(PROCESS_SET_INFORMATION, 0, pid);
        if handle.is_null() {
            return false;
        }
        let changed = SetPriorityClass(handle, class) != 0;
        let _ = CloseHandle(handle);
        changed
    }
}

#[cfg(not(windows))]
fn set_paper_priority(_pid: u32, _priority: PaperPriority) -> bool {
    false
}

#[cfg(test)]
mod tests {
    use super::{HIGH_PRESSURE_PERCENT, LOW_PRESSURE_PERCENT};

    #[test]
    fn pressure_thresholds_leave_hysteresis_gap() {
        assert!(LOW_PRESSURE_PERCENT < HIGH_PRESSURE_PERCENT);
    }
}
