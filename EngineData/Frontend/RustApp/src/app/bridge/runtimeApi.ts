import { invoke } from '@tauri-apps/api/core';

export type ServerSnapshot = {
  state: string;
  health: string;
  cpuLoadPercent: number;
  usedMemoryBytes: number;
  maxMemoryBytes: number;
  pid?: number | null;
  logPath: string;
};

export type ServerPreflight = {
  ready: boolean;
  workspace: string;
  serverDirectory: string;
  paperJar: string;
  worldsDirectory: string;
  javaPath: string;
  javaVersion: string;
  logDirectory: string;
  issues: string[];
};

export type ServerProcessMetrics = {
  available: boolean;
  pid?: number | null;
  cpuPercent: number;
  processMemoryBytes: number;
  diskReadBytes: number;
  diskWriteBytes: number;
};

export type DetachedRecoveryResult = {
  pid: number;
  stopped: boolean;
  message: string;
};

export type ServerLogTail = {
  path: string;
  content: string;
  truncated: boolean;
};

export type ResourcePreset = {
  name: string;
  maxMemoryMb: number;
  minMemoryMb: number;
  cpuThreads: number;
};

export type ServerResourceProfile = {
  totalMemoryMb: number;
  reservedSystemMemoryMb: number;
  safeMaxMemoryMb: number;
  logicalProcessors: number;
  currentMaxMemoryMb: number;
  currentMinMemoryMb: number;
  currentCpuThreads: number;
  currentPreset: string;
  performance: ResourcePreset;
  boost: ResourcePreset;
  warning: string;
};

export type ResourceUpdateRequest = {
  maxMemoryMb: number;
  cpuThreads: number;
  preset: string;
};

export type PluginSummary = {
  id: string;
  displayName: string;
  version: string;
  category: string;
  state: string;
  problemDetail?: string | null;
  candidateFiles?: string[] | null;
};

export type PluginInstallResult = {
  success: boolean;
  pluginId: string;
  message?: string | null;
  restartRequired: boolean;
};

export type ManagedWorldSummary = {
  id: string;
  displayName: string;
  kind: string;
  lifecycle: string;
  runtimeState: string;
  autoLoad: boolean;
  defaultGameMode: string;
};

export type CreateWorldRequest = {
  folderName: string;
  displayName: string;
  kind: 'FLAT' | 'VOID';
};

export type CloneWorldRequest = {
  worldId: string;
  destinationFolder: string;
  displayName: string;
};

export type ExportWorldRequest = {
  worldId: string;
  targetFormat: string;
  artifactName: string;
};

export type ImportWorldRequest = {
  artifactName: string;
  destinationFolder: string;
  displayName: string;
};

export type DeleteWorldRequest = {
  worldId: string;
  typedFolderName: string;
};

export type WorldSettingsSnapshot = {
  id: string;
  displayName: string;
  autoLoad: boolean;
  defaultGameMode: string;
  timeOfDayTicks: number;
  weather: string;
  naturalSpawning: boolean;
  daylightCycle: boolean;
  weatherCycle: boolean;
};

export type UpdateWorldSettingsRequest = Partial<{
  autoLoad: boolean;
  defaultGameMode: string;
  timeOfDayTicks: number;
  weather: string;
  naturalSpawning: boolean;
  daylightCycle: boolean;
  weatherCycle: boolean;
}>;

export type WorldTaskSnapshot = {
  taskId: string;
  taskType: string;
  worldId?: string | null;
  state: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  progressPercent: number;
  message: string;
  result: string;
  error: string;
  createdAt: string;
  updatedAt: string;
};

export const runtimeApi = {
  getServerPreflight: () => invoke<ServerPreflight>('server_preflight'),
  getServerSnapshot: () => invoke<ServerSnapshot>('server_snapshot'),
  getServerProcessMetrics: () => invoke<ServerProcessMetrics>('server_process_metrics'),
  startServer: () => invoke<void>('server_start'),
  stopServer: () => invoke<void>('server_stop'),
  restartServer: () => invoke<void>('server_restart'),
  recoverDetachedServer: () => invoke<DetachedRecoveryResult>('server_recover_detached'),
  readServerLogTail: (path: string) => invoke<ServerLogTail>('server_log_tail', { path }),
  getServerResourceProfile: () => invoke<ServerResourceProfile>('server_resource_profile'),
  saveServerResources: (request: ResourceUpdateRequest) =>
    invoke<ServerResourceProfile>('server_resource_save', { request }),
  applyServerResourcePreset: (name: string) =>
    invoke<ServerResourceProfile>('server_resource_preset', { name }),

  listPlugins: () => invoke<PluginSummary[]>('plugin_list'),
  pickPluginJar: () => invoke<string | null>('plugin_pick_jar'),
  installPlugin: (jarPath: string) => invoke<PluginInstallResult>('plugin_install', { jarPath }),
  updatePlugin: (pluginId: string, jarPath: string) =>
    invoke<PluginInstallResult>('plugin_update', { pluginId, jarPath }),
  setPluginEnabled: (pluginId: string, enabled: boolean) =>
    invoke<void>('plugin_set_enabled', { pluginId, enabled }),
  removePlugin: (pluginId: string, removeData = false) =>
    invoke<void>('plugin_remove', { pluginId, removeData }),
  resolvePluginDuplicates: (pluginId: string, keepJarFileName: string) =>
    invoke<PluginInstallResult>('plugin_resolve_duplicates', { pluginId, keepJarFileName }),
  setPluginCategory: (pluginId: string, category: string) =>
    invoke<void>('plugin_set_category', { pluginId, category }),

  listWorlds: () => invoke<ManagedWorldSummary[]>('world_list'),
  createWorld: (request: CreateWorldRequest) =>
    invoke<ManagedWorldSummary>('world_create', { request }),
  loadWorld: (worldId: string) => invoke<ManagedWorldSummary>('world_load', { worldId }),
  unloadWorld: (worldId: string) => invoke<ManagedWorldSummary>('world_unload', { worldId }),
  getWorldSettings: (worldId: string) =>
    invoke<WorldSettingsSnapshot>('world_settings', { worldId }),
  updateWorldSettings: (worldId: string, request: UpdateWorldSettingsRequest) =>
    invoke<WorldSettingsSnapshot>('world_update_settings', { worldId, request }),
  listWorldTasks: () => invoke<WorldTaskSnapshot[]>('world_task_list'),
  getWorldTask: (taskId: string) => invoke<WorldTaskSnapshot>('world_task', { taskId }),
  archiveWorld: (worldId: string) => invoke<WorldTaskSnapshot>('world_archive', { worldId }),
  restoreWorld: (worldId: string) => invoke<WorldTaskSnapshot>('world_restore', { worldId }),
  backupWorld: (worldId: string) => invoke<WorldTaskSnapshot>('world_backup', { worldId }),
  cloneWorld: (request: CloneWorldRequest) => invoke<WorldTaskSnapshot>('world_clone', { request }),
  exportWorld: (request: ExportWorldRequest) => invoke<WorldTaskSnapshot>('world_export', { request }),
  deleteWorld: (request: DeleteWorldRequest) => invoke<WorldTaskSnapshot>('world_delete', { request }),
  pickWorldImport: () => invoke<string | null>('world_import_pick'),
  uploadWorldImport: (filePath: string) => invoke<string>('world_import_upload', { filePath }),
  importWorld: (request: ImportWorldRequest) => invoke<WorldTaskSnapshot>('world_import', { request })
};
