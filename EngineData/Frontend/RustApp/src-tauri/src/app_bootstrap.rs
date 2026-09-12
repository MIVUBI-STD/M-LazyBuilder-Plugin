use crate::commands;
use crate::engine::plugin_manager::PluginManagerState;
use crate::engine::server_manager::ServerManagerState;

pub fn run() {
    tauri::Builder::default()
        .manage(ServerManagerState::default())
        .manage(PluginManagerState::default())
        .invoke_handler(tauri::generate_handler![
            commands::server_manager::server_snapshot,
            commands::server_manager::server_start,
            commands::server_manager::server_stop,
            commands::server_manager::server_restart,
            commands::plugin_manager::plugin_list,
            commands::plugin_manager::plugin_pick_jar,
            commands::plugin_manager::plugin_install,
            commands::plugin_manager::plugin_update,
            commands::plugin_manager::plugin_set_enabled,
            commands::plugin_manager::plugin_remove,
            commands::plugin_manager::plugin_resolve_duplicates,
            commands::plugin_manager::plugin_set_category,
            commands::world_manager::world_list,
            commands::world_manager::world_create,
            commands::world_manager::world_load,
            commands::world_manager::world_unload,
            commands::world_manager::world_settings,
            commands::world_manager::world_update_settings,
            commands::world_manager::world_task_list,
            commands::world_manager::world_task,
            commands::world_manager::world_archive,
            commands::world_manager::world_restore
        ])
        .run(tauri::generate_context!())
        .expect("failed to run LazyBuilder desktop runtime");
}
