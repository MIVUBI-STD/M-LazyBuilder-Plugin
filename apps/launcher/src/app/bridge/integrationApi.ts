import { invokeRuntime } from './invokeRuntime';
import type {
  ClientIntegrationStatus,
  CreateWorldRequest,
  DeleteWorldRequest,
  DuplicateWorldRequest,
  ExportWorldRequest,
  ImportWorldRequest,
  ManagedWorldSummary,
  PluginInstallResult,
  PluginSummary,
  UpdateWorldSettingsRequest,
  WorldSettingsSnapshot,
  WorldTaskSnapshot
} from './runtimeTypes';

export const integrationApi = {
  plugins: {
    list: () => invokeRuntime<PluginSummary[]>('plugin_list'),
    pickJar: () => invokeRuntime<string | null>('plugin_pick_jar'),
    install: (jarPath: string) => invokeRuntime<PluginInstallResult>('plugin_install', { jarPath }),
    update: (pluginId: string, jarPath: string) => invokeRuntime<PluginInstallResult>('plugin_update', { pluginId, jarPath }),
    setEnabled: (pluginId: string, enabled: boolean) => invokeRuntime<void>('plugin_set_enabled', { pluginId, enabled }),
    remove: (pluginId: string) => invokeRuntime<void>('plugin_remove', { pluginId }),
    removeProblem: (pluginId: string, jarFileName: string) => invokeRuntime<void>('plugin_remove_problem', { pluginId, jarFileName }),
    resolveDuplicates: (pluginId: string, keepJarFileName: string) =>
      invokeRuntime<PluginInstallResult>('plugin_resolve_duplicates', { pluginId, keepJarFileName })
  },
  client: {
    status: () => invokeRuntime<ClientIntegrationStatus>('client_integration_status'),
    selectProfile: (profilePath: string) =>
      invokeRuntime<ClientIntegrationStatus>('client_integration_select_profile', { profilePath }),
    pickProfile: () => invokeRuntime<ClientIntegrationStatus>('client_integration_pick_profile'),
    sync: () => invokeRuntime<ClientIntegrationStatus>('client_integration_sync')
  },
  worlds: {
    list: () => invokeRuntime<ManagedWorldSummary[]>('world_list'),
    create: (request: CreateWorldRequest) => invokeRuntime<ManagedWorldSummary>('world_create', { request }),
    settings: (worldId: string) => invokeRuntime<WorldSettingsSnapshot>('world_settings', { worldId }),
    updateSettings: (worldId: string, request: UpdateWorldSettingsRequest) =>
      invokeRuntime<WorldSettingsSnapshot>('world_update_settings', { worldId, request }),
    tasks: () => invokeRuntime<WorldTaskSnapshot[]>('world_task_list'),
    task: (taskId: string) => invokeRuntime<WorldTaskSnapshot>('world_task', { taskId }),
    archive: (worldId: string) => invokeRuntime<WorldTaskSnapshot>('world_archive', { worldId }),
    restore: (worldId: string) => invokeRuntime<WorldTaskSnapshot>('world_restore', { worldId }),
    backup: (worldId: string) => invokeRuntime<WorldTaskSnapshot>('world_backup', { worldId }),
    duplicate: (request: DuplicateWorldRequest) => invokeRuntime<WorldTaskSnapshot>('world_duplicate', { request }),
    export: (request: ExportWorldRequest) => invokeRuntime<WorldTaskSnapshot>('world_export', { request }),
    delete: (request: DeleteWorldRequest) => invokeRuntime<WorldTaskSnapshot>('world_delete', { request }),
    pickImport: () => invokeRuntime<string | null>('world_import_pick'),
    uploadImport: (filePath: string) => invokeRuntime<string>('world_import_upload', { filePath }),
    import: (request: ImportWorldRequest) => invokeRuntime<WorldTaskSnapshot>('world_import', { request })
  }
};
