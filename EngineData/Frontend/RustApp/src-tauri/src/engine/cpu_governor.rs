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

pub fn start_for_paper(pid: u32) {
    thread::spawn(move || run(pid));
}

fn run(pid: u32) {
    let paper_pid = Pid::from_u32(pid);
    let mut system = System::new_all();
    let mut current = PaperPriority::Normal;
    let mut high_samples = 0u8;
    let mut low_samples = 0u8;

    let _ = set_paper_priority(pid, PaperPriority::Normal);

    loop {
        thread::sleep(SAMPLE_INTERVAL);
        system.refresh_all();

        if system.process(paper_pid).is_none() {
            return;
        }

        let client_active = minecraft_client_running(&system, paper_pid);
        if !client_active {
            high_samples = 0;
            low_samples = 0;
            if current != PaperPriority::Normal && set_paper_priority(pid, PaperPriority::Normal) {
                current = PaperPriority::Normal;
            }
            continue;
        }

        let cpu = system.global_cpu_info().cpu_usage();
        if cpu >= HIGH_PRESSURE_PERCENT {
            high_samples = high_samples.saturating_add(1);
            low_samples = 0;
            if high_samples >= HIGH_SAMPLES_REQUIRED && current != PaperPriority::BelowNormal {
                if set_paper_priority(pid, PaperPriority::BelowNormal) {
                    current = PaperPriority::BelowNormal;
                }
                high_samples = 0;
            }
        } else if cpu <= LOW_PRESSURE_PERCENT {
            low_samples = low_samples.saturating_add(1);
            high_samples = 0;
            if low_samples >= LOW_SAMPLES_REQUIRED && current != PaperPriority::Normal {
                if set_paper_priority(pid, PaperPriority::Normal) {
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
    use windows_sys::Win32::Foundation::CloseHandle;
    use windows_sys::Win32::System::Threading::{
        OpenProcess, SetPriorityClass, BELOW_NORMAL_PRIORITY_CLASS, NORMAL_PRIORITY_CLASS,
        PROCESS_SET_INFORMATION,
    };

    let class = match priority {
        PaperPriority::Normal => NORMAL_PRIORITY_CLASS,
        PaperPriority::BelowNormal => BELOW_NORMAL_PRIORITY_CLASS,
    };

    unsafe {
        let handle = OpenProcess(PROCESS_SET_INFORMATION, 0, pid);
        if handle == 0 {
            return false;
        }
        let changed = SetPriorityClass(handle, class) != 0;
        CloseHandle(handle);
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
