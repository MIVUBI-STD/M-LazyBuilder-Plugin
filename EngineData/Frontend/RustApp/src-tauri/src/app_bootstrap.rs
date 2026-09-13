use crate::commands;
use crate::engine::plugin_manager::PluginManagerState;
use crate::engine::server_manager::ServerManagerState;
use crate::engine::workspace_registry;

pub fn run() {
    let _ = workspace_registry::initialize();
    let _ = commands::server_tools::maintain_logs();

    tauri::Builder::default()
        .manage(ServerManagerState::default())
        .manage(PluginManagerState::default())
        .invoke_handler(tauri::generate_handler![
            commands::workspace::workspace_state,
            commands::workspace::workspace_pick_parent,
            commands::workspace::workspace_create,
            commands::workspace::workspace_open_picker,
            commands::workspace::workspace_activate,
            commands::workspace::workspace_close,
            commands::server_manager::server_preflight,
            commands::server_manager::server_snapshot,
            commands::server_manager::server_start,
            commands::server_manager::server_stop,
            commands::server_manager::server_restart,
            commands::server_tools::server_log_tail,
            commands::server_tools::server_recover_detached,
            commands::resource_settings::server_resource_profile,
            commands::resource_settings::server_resource_save,
            commands::resource_settings::server_resource_preset,
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
            commands::world_manager::world_restore,
            commands::world_manager::world_backup,
            commands::world_manager::world_clone,
            commands::world_manager::world_export,
            commands::world_manager::world_delete,
            commands::world_manager::world_import_pick,
            commands::world_manager::world_import_upload,
            commands::world_manager::world_import
        ])
        .run(tauri::generate_context!())
        .expect("failed to run LazyBuilder desktop runtime");
}
