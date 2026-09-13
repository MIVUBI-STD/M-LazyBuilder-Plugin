import { invoke } from '@tauri-apps/api/core';

export type WorkspaceEntry = {
  id: string;
  name: string;
  path: string;
  lastOpenedUnixSeconds: number;
};

export type WorkspaceState = {
  active?: WorkspaceEntry | null;
  recent: WorkspaceEntry[];
};

export type AdoptionPlan = {
  root: string;
  name: string;
  paperJar: string;
  worlds: string[];
  serverEntries: string[];
  legacyPluginsToDisable: string[];
  preservedEntries: string[];
  warnings: string[];
};

export type WorkspaceProvisioningStatus = {
  workspaceCreated: boolean;
  javaReady: boolean;
  paperReady: boolean;
  coreModulesReady: boolean;
  configReady: boolean;
  eulaAccepted: boolean;
  ready: boolean;
  nextStep: string;
};

export type WorkspaceProvisionResult = {
  javaPath: string;
  paperBuild?: number | null;
  coreVersion: string;
  status: WorkspaceProvisioningStatus;
};

export type RuntimeUpdateStatus = {
  currentPaperBuild?: number | null;
  latestPaperBuild: number;
  paperUpdateAvailable: boolean;
};

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
};

export type ServerResourceProfile = {
  totalMemoryMb: number;
  reservedSystemMemoryMb: number;
  safeMaxMemoryMb: number;
  logicalProcessors: number;
  currentMaxMemoryMb: number;
  currentMinMemoryMb: number;
  currentPreset: string;
  performance: ResourcePreset;
  boost: ResourcePreset;
  warning: string;
};

export type ResourceUpdateRequest = {
  maxMemoryMb: number;
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

export type CreateWorldRequest = { folderName: string; displayName: string; kind: 'FLAT' | 'VOID' };
export type CloneWorldRequest = { worldId: string; destinationFolder: string; displayName: string };
export type ExportWorldRequest = { worldId: string; targetFormat: string; artifactName: string };
export type ImportWorldRequest = { artifactName: string; destinationFolder: string; displayName: string };
export type DeleteWorldRequest = { worldId: string; typedDisplayName: string };

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
  workspace: {
    state: () => invoke<WorkspaceState>('workspace_state'),
    provisioningStatus: () => invoke<WorkspaceProvisioningStatus>('workspace_provisioning_status'),
    provision: () => invoke<WorkspaceProvisionResult>('workspace_provision'),
    runtimeUpdateStatus: () => invoke<RuntimeUpdateStatus>('workspace_runtime_update_status'),
    updatePaper: () => invoke<RuntimeUpdateStatus>('workspace_update_paper'),
    acceptEula: () => invoke<WorkspaceProvisioningStatus>('workspace_accept_eula'),
    pickParent: () => invoke<string | null>('workspace_pick_parent'),
    create: (parentPath: string, name: string) => invoke<WorkspaceEntry>('workspace_create', { parentPath, name }),
    pickAdoption: () => invoke<AdoptionPlan | null>('workspace_adoption_pick'),
    adopt: (rootPath: string, name?: string | null) => invoke<WorkspaceEntry>('workspace_adopt', { rootPath, name: name ?? null }),
    activate: (id: string) => invoke<WorkspaceEntry>('workspace_activate', { id }),
    close: () => invoke<void>('workspace_close')
  },
  server: {
    preflight: () => invoke<ServerPreflight>('server_preflight'),
    snapshot: () => invoke<ServerSnapshot>('server_snapshot'),
    start: () => invoke<void>('server_start'),
    stop: () => invoke<void>('server_stop'),
    restart: () => invoke<void>('server_restart'),
    recoverDetached: () => invoke<DetachedRecoveryResult>('server_recover_detached'),
    logTail: (path: string) => invoke<ServerLogTail>('server_log_tail', { path }),
    resources: () => invoke<ServerResourceProfile>('server_resource_profile'),
    saveResources: (request: ResourceUpdateRequest) => invoke<ServerResourceProfile>('server_resource_save', { request })
  },
  plugins: {
    list: () => invoke<PluginSummary[]>('plugin_list'),
    pickJar: () => invoke<string | null>('plugin_pick_jar'),
    install: (jarPath: string) => invoke<PluginInstallResult>('plugin_install', { jarPath }),
    update: (pluginId: string, jarPath: string) => invoke<PluginInstallResult>('plugin_update', { pluginId, jarPath }),
    setEnabled: (pluginId: string, enabled: boolean) => invoke<void>('plugin_set_enabled', { pluginId, enabled }),
    remove: (pluginId: string) => invoke<void>('plugin_remove', { pluginId }),
    removeProblem: (pluginId: string, jarFileName: string) => invoke<void>('plugin_remove_problem', { pluginId, jarFileName }),
    resolveDuplicates: (pluginId: string, keepJarFileName: string) => invoke<PluginInstallResult>('plugin_resolve_duplicates', { pluginId, keepJarFileName })
  },
  worlds: {
    list: () => invoke<ManagedWorldSummary[]>('world_list'),
    create: (request: CreateWorldRequest) => invoke<ManagedWorldSummary>('world_create', { request }),
    load: (worldId: string) => invoke<ManagedWorldSummary>('world_load', { worldId }),
    unload: (worldId: string) => invoke<ManagedWorldSummary>('world_unload', { worldId }),
    settings: (worldId: string) => invoke<WorldSettingsSnapshot>('world_settings', { worldId }),
    updateSettings: (worldId: string, request: UpdateWorldSettingsRequest) => invoke<WorldSettingsSnapshot>('world_update_settings', { worldId, request }),
    tasks: () => invoke<WorldTaskSnapshot[]>('world_task_list'),
    task: (taskId: string) => invoke<WorldTaskSnapshot>('world_task', { taskId }),
    archive: (worldId: string) => invoke<WorldTaskSnapshot>('world_archive', { worldId }),
    restore: (worldId: string) => invoke<WorldTaskSnapshot>('world_restore', { worldId }),
    backup: (worldId: string) => invoke<WorldTaskSnapshot>('world_backup', { worldId }),
    clone: (request: CloneWorldRequest) => invoke<WorldTaskSnapshot>('world_clone', { request }),
    export: (request: ExportWorldRequest) => invoke<WorldTaskSnapshot>('world_export', { request }),
    delete: (request: DeleteWorldRequest) => invoke<WorldTaskSnapshot>('world_delete', { request }),
    pickImport: () => invoke<string | null>('world_import_pick'),
    uploadImport: (filePath: string) => invoke<string>('world_import_upload', { filePath }),
    import: (request: ImportWorldRequest) => invoke<WorldTaskSnapshot>('world_import', { request })
  }
};
