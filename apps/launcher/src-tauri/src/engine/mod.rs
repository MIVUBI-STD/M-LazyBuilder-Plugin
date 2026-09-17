// Application lifecycle and launcher-wide state.
pub mod app_data_migrations;
pub mod app_instance;
pub mod diagnostics;
pub mod launcher_settings;
pub mod operations;
pub mod paths;
pub mod persistence;
pub mod privacy_redaction;
pub mod runtime_environment;
pub mod startup;
pub mod startup_guard;
pub mod storage_health;
pub mod support_bundle;

// Workspace ownership and lifecycle.
pub mod adoption;
pub mod workspace_creation;
pub mod workspace_registry;

// Managed runtime provisioning.
pub mod core_modules;
pub mod java_runtime;
pub mod paper_provider;
pub mod provisioning;
pub mod runtime_updates;

// Local Paper server lifecycle and safety.
pub mod backup_recovery;
pub mod resource_settings;
pub mod server_backups;
pub mod server_config;
pub mod server_health;
pub mod server_manager;
pub mod server_process_guard;
pub mod server_repair;
pub mod server_restore;
pub mod server_runtime_registry;
pub mod server_start_lock;

// External integrations owned by the launcher boundary only.
pub mod client_integration;
pub mod plugin_ingress;
pub mod plugin_manager;

// Minecraft world semantics remain owned by the Paper World Manager. The launcher
// module is only the authenticated loopback control client/bridge. Keep the old
// name as a compatibility alias while new code uses the ownership-accurate name.
#[path = "world_manager/mod.rs"]
pub mod world_control_bridge;
pub use world_control_bridge as world_manager;
