use crate::commands;
use crate::engine::server_manager::ServerManagerState;

pub fn run() {
    tauri::Builder::default()
        .manage(ServerManagerState::default())
        .invoke_handler(tauri::generate_handler![
            commands::server_manager::server_snapshot,
            commands::server_manager::server_start,
            commands::server_manager::server_stop,
            commands::server_manager::server_restart,
            commands::plugin_manager::plugin_list,
            commands::world_manager::world_list
        ])
        .run(tauri::generate_context!())
        .expect("failed to run LazyBuilder desktop runtime");
}
