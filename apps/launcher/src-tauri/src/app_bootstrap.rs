use crate::commands;
use crate::engine::operations::OperationRegistry;
use crate::engine::plugin_manager::PluginManagerState;
use crate::engine::server_manager::ServerManagerState;
use crate::engine::{diagnostics, server_restore, startup};

pub fn run() {
    match server_restore::recover_pending_restores() {
        Ok(report) => {
            if report.recovered > 0 {
                diagnostics::info(&format!("Reconciled {} interrupted server restore(s) before startup.", report.recovered));
            }
            for issue in report.issues {
                diagnostics::error(&format!("Server restore recovery needs attention: {issue}"));
            }
        }
        Err(error) => diagnostics::error(&format!("Server restore recovery could not run before startup: {error}")),
    }

    let startup_report = startup::coordinate();

    tauri::Builder::default()
        .manage(ServerManagerState::default())
        .manage(PluginManagerState::default())
        .manage(OperationRegistry::default())
        .manage(startup_report)
        .invoke_handler(tauri::generate_handler![
            commands::diagnostics::diagnostics_summary,
            commands::startup::launcher_startup_status,
            commands::launcher_settings::launcher_settings_get,
            commands::launcher_settings::launcher_settings_save,
            commands::server_health::launcher_server_health,
            commands::operations::launcher_operation_list,
            commands::operations::launcher_operation,
            commands::operations::launcher_operation_cancel,
            commands::server_backups::server_backup_list,
            commands::server_backups::server_backup_estimate,
            commands::server_backups::server_backup_create,
            commands::server_backups::server_backup_restore,
            commands::server_backups::server_backup_delete,
            commands::workspace::workspace_state,
            commands::workspace::workspace_provisioning_status,
            commands::workspace::workspace_provision,
            commands::workspace::workspace_runtime_update_status,
            commands::workspace::workspace_update_paper,
            commands::workspace::workspace_accept_eula,
            commands::workspace::workspace_pick_parent,
            commands::workspace::workspace_create,
            commands::workspace::workspace_adoption_pick,
            commands::workspace::workspace_adopt,
            commands::workspace::workspace_activate,
            commands::workspace::workspace_close,
            commands::workspace::workspace_open_folder,
            commands::workspace::workspace_duplicate_estimate,
            commands::workspace::workspace_duplicate,
            commands::workspace::workspace_remove_from_library,
            commands::workspace::workspace_delete,
            commands::server_manager::server_preflight,
            commands::server_manager::server_snapshot,
            commands::server_manager::server_start,
            commands::server_manager::server_stop,
            commands::server_manager::server_restart,
            commands::server_manager::server_recover_detached,
            commands::server_tools::server_log_tail,
            commands::resource_settings::server_resource_profile,
            commands::resource_settings::server_resource_save,
            commands::plugin_manager::plugin_list,
            commands::plugin_manager::plugin_pick_jar,
            commands::plugin_manager::plugin_install,
            commands::plugin_manager::plugin_update,
            commands::plugin_manager::plugin_set_enabled,
            commands::plugin_manager::plugin_remove,
            commands::plugin_manager::plugin_remove_problem,
            commands::plugin_manager::plugin_resolve_duplicates,
            commands::client_integration::client_integration_status,
            commands::client_integration::client_integration_select_profile,
            commands::client_integration::client_integration_pick_profile,
            commands::client_integration::client_integration_sync,
            commands::world_manager::world_list,
            commands::world_manager::world_create,
            commands::world_manager::world_settings,
            commands::world_manager::world_update_settings,
            commands::world_manager::world_task_list,
            commands::world_manager::world_task,
            commands::world_manager::world_archive,
            commands::world_manager::world_restore,
            commands::world_manager::world_backup,
            commands::world_manager::world_duplicate,
            commands::world_manager::world_export,
            commands::world_manager::world_delete,
            commands::world_manager::world_import_pick,
            commands::world_manager::world_import_upload,
            commands::world_manager::world_import
        ])
        .run(tauri::generate_context!())
        .expect("failed to run LazyBuilder desktop runtime");
}
