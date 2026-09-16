import { invoke } from '@tauri-apps/api/core';

export type RuntimeCommandError = {
  code: string;
  message: string;
  details?: string;
  recoverable?: boolean;
  action?: string | null;
  correlationId?: string;
};

export class RuntimeError extends Error {
  readonly code: string;
  readonly details: string;
  readonly recoverable: boolean;
  readonly action?: string | null;
  readonly correlationId: string;

  constructor(error: RuntimeCommandError) {
    super(error.message);
    this.name = 'RuntimeError';
    this.code = error.code;
    this.details = error.details ?? '';
    this.recoverable = error.recoverable ?? false;
    this.action = error.action ?? null;
    this.correlationId = error.correlationId ?? '';
  }
}

export function runtimeError(value: unknown): RuntimeCommandError {
  if (value && typeof value === 'object') {
    const candidate = value as Record<string, unknown>;
    if (typeof candidate.code === 'string' && typeof candidate.message === 'string') {
      return {
        code: candidate.code,
        message: candidate.message,
        details: typeof candidate.details === 'string' ? candidate.details : '',
        recoverable: candidate.recoverable === true,
        action: typeof candidate.action === 'string' ? candidate.action : null,
        correlationId: typeof candidate.correlationId === 'string' ? candidate.correlationId : ''
      };
    }
  }
  const message = String(value ?? '').replace(/^Error:\s*/i, '').trim();
  return { code: 'RUNTIME_ERROR', message: message || 'Something went wrong. Try again.', details: '', recoverable: false, action: null, correlationId: '' };
}

async function invokeRuntime<T>(command: string, args?: Record<string, unknown>): Promise<T> {
  try {
    return await invoke<T>(command, args);
  } catch (value) {
    throw new RuntimeError(runtimeError(value));
  }
}

export type DiagnosticSummary = { launcherVersion: string; launcherLogPath: string; workspaceName?: string | null; workspacePath?: string | null; minecraftVersion?: string | null; serverPlatform?: string | null; paperBuild?: number | null; serverState: string; pid?: number | null; javaVersion: string; maxMemoryMb: number };
export type StartupStepState = 'READY' | 'WARNING';
export type StartupStep = { key: string; state: StartupStepState; summary: string; details: string };
export type StartupReport = { ready: boolean; degraded: boolean; startedAtUnixSeconds: number; completedAtUnixSeconds: number; runtimeTempPath?: string | null; steps: StartupStep[] };

export type LauncherSettings = {
  schemaVersion: number;
  rememberLastServer: boolean;
  confirmCloseWhileServerRunning: boolean;
  autoCheckUpdates: boolean;
  updateChannel: 'stable' | 'preview';
};

export type LauncherOperationState = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLING' | 'CANCELLED' | 'RECOVERY_REQUIRED';
export type LauncherOperationProgress = { current: number; total?: number | null; unit: string };
export type LauncherOperationError = { code: string; message: string; details: string; recoverable: boolean };
export type LauncherOperationSnapshot = {
  id: string;
  correlationId?: string;
  kind: string;
  resource: string;
  state: LauncherOperationState;
  phase: string;
  status: string;
  details: string;
  progress?: LauncherOperationProgress | null;
  canCancel: boolean;
  cancelRequested: boolean;
  warnings: string[];
  error?: LauncherOperationError | null;
  createdAtUnixSeconds: number;
  updatedAtUnixSeconds: number;
  completedAtUnixSeconds?: number | null;
};

export type WorkspaceEntry = { id: string; name: string; path: string; lastOpenedUnixSeconds: number };
export type WorkspaceState = { active?: WorkspaceEntry | null; recent: WorkspaceEntry[] };
export type WorkspaceDuplicateEstimate = { sourceBytes: number; requiredBytes: number; availableBytes?: number | null };
export type AdoptionPlan = { root: string; name: string; paperJar: string; worlds: string[]; serverEntries: string[]; legacyPluginsToDisable: string[]; preservedEntries: string[]; warnings: string[] };
export type WorkspaceProvisioningStatus = { workspaceCreated: boolean; javaReady: boolean; paperReady: boolean; coreModulesReady: boolean; configReady: boolean; eulaAccepted: boolean; ready: boolean; nextStep: string };
export type WorkspaceProvisionResult = { javaPath: string; paperBuild?: number | null; coreVersion: string; status: WorkspaceProvisioningStatus };
export type RuntimeUpdateStatus = { currentPaperBuild?: number | null; latestPaperBuild: number; paperUpdateAvailable: boolean };

export type ServerHealthState = 'READY' | 'NEEDS_ATTENTION' | 'UNAVAILABLE' | 'BUSY';
export type ServerHealthCheck = { key: string; ready: boolean; summary: string; details: string; repairable: boolean };
export type ServerHealthSnapshot = { workspaceId: string; workspaceName: string; state: ServerHealthState; ready: boolean; running: boolean; checks: ServerHealthCheck[] };
export type ServerRepairItem = { checkKey: string; title: string; details: string };
export type ServerRepairPlan = { workspaceId: string; workspaceName: string; canRepair: boolean; blockedReason: string; repairs: ServerRepairItem[]; manualActions: ServerRepairItem[]; health: ServerHealthSnapshot };
export type ServerRepairResult = { repairedChecks: string[]; health: ServerHealthSnapshot };
export type ServerState = 'Offline' | 'Starting' | 'Online' | 'Stopping' | 'Detached' | 'Crashed';
export type ServerHealth = 'Offline' | 'Good' | 'Warning' | 'Critical';
export type ServerSnapshot = { state: ServerState; health: ServerHealth; cpuLoadPercent: number; usedMemoryBytes: number; maxMemoryBytes: number; pid?: number | null; logPath: string };
export type ServerRuntimeSummary = { workspaceId: string; workspaceName: string; state: ServerState; pid?: number | null; paperPort?: number | null };
export type ServerPreflight = { ready: boolean; workspace: string; serverDirectory: string; paperJar: string; worldsDirectory: string; javaPath: string; javaVersion: string; logDirectory: string; issues: string[] };
export type DetachedRecoveryResult = { pid: number; stopped: boolean; message: string };
export type ServerLogTail = { path: string; content: string; truncated: boolean };
export type ServerResourceProfile = { totalMemoryMb: number; safeMaxMemoryMb: number; currentMaxMemoryMb: number; currentMinMemoryMb: number; recommendedMaxMemoryMb: number; warning: string };
export type ResourceUpdateRequest = { maxMemoryMb: number };
export type ServerBackupSummary = { id: string; workspaceId: string; workspaceName: string; path: string; createdUnixSeconds: number; sourceBytes: number };
export type ServerBackupEstimate = { sourceBytes: number; requiredBytes: number; availableBytes?: number | null };
export type ServerRestoreResult = { restoredBackupId: string; safetyBackup: ServerBackupSummary; cleanupPending: boolean };

export type PluginState = 'Enabled' | 'Disabled' | 'Problem';
export type PluginSummary = { id: string; displayName: string; version: string; category: string; state: PluginState; problemDetail?: string | null; candidateFiles?: string[] | null; managedByLazyBuilder: boolean; mutable: boolean };
export type PluginInstallResult = { success: boolean; pluginId: string; message?: string | null; restartRequired: boolean };
export type ClientProfileSummary = { name: string; path: string; modrinthRoot: string; modsPath: string; gameVersion?: string | null; loader?: string | null; verification: string; compatible: boolean; selected: boolean };
export type ClientModStatus = { id: string; displayName: string; state: string; installedFiles: string[]; targetFile: string; bundled: boolean };
export type ClientIntegrationStatus = { modrinthDetected: boolean; profiles: ClientProfileSummary[]; selectedProfile?: ClientProfileSummary | null; selectedProfileMissing: boolean; mods: ClientModStatus[]; ready: boolean; message: string };

export type WorldKind = 'FLAT' | 'VOID' | 'IMPORTED';
export type WorldLifecycle = 'ACTIVE' | 'ARCHIVED';
export type GameMode = 'CREATIVE' | 'SURVIVAL' | 'ADVENTURE' | 'SPECTATOR';
export type Weather = 'CLEAR' | 'RAIN' | 'THUNDER';
export type WorldTaskState = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
export type ManagedWorldSummary = { id: string; displayName: string; kind: WorldKind; lifecycle: WorldLifecycle; defaultGameMode: GameMode };
export type CreateWorldRequest = { folderName: string; displayName: string; kind: Extract<WorldKind, 'FLAT' | 'VOID'> };
export type DuplicateWorldRequest = { worldId: string; destinationFolder: string; displayName: string };
export type ExportWorldRequest = { worldId: string; targetFormat: string; artifactName: string };
export type ImportWorldRequest = { artifactName: string; destinationFolder: string; displayName: string };
export type DeleteWorldRequest = { worldId: string; typedDisplayName: string };
export type WorldSettingsSnapshot = { id: string; displayName: string; defaultGameMode: GameMode; timeOfDayTicks: number; weather: Weather; naturalSpawning: boolean; daylightCycle: boolean; weatherCycle: boolean };
export type UpdateWorldSettingsRequest = Partial<{ defaultGameMode: GameMode; timeOfDayTicks: number; weather: Weather; naturalSpawning: boolean; daylightCycle: boolean; weatherCycle: boolean }>;
export type WorldTaskSnapshot = { taskId: string; taskType: string; worldId?: string | null; state: WorldTaskState; progressPercent: number; message: string; result: string; error: string; createdAt: string; updatedAt: string };

export const runtimeApi = {
  diagnostics: {
    summary: () => invokeRuntime<DiagnosticSummary>('diagnostics_summary'),
    exportSupportBundle: () => invokeRuntime<string | null>('diagnostics_export_support_bundle')
  },
  startup: { status: () => invokeRuntime<StartupReport>('launcher_startup_status') },
  settings: {
    get: () => invokeRuntime<LauncherSettings>('launcher_settings_get'),
    save: (settings: LauncherSettings) => invokeRuntime<LauncherSettings>('launcher_settings_save', { settings })
  },
  health: {
    server: (id: string) => invokeRuntime<ServerHealthSnapshot>('launcher_server_health', { id }),
    repairPlan: (id: string) => invokeRuntime<ServerRepairPlan>('launcher_server_repair_plan', { id }),
    repair: (id: string) => invokeRuntime<ServerRepairResult>('launcher_server_repair', { id })
  },
  operations: {
    list: () => invokeRuntime<LauncherOperationSnapshot[]>('launcher_operation_list'),
    get: (id: string) => invokeRuntime<LauncherOperationSnapshot>('launcher_operation', { id }),
    cancel: (id: string) => invokeRuntime<LauncherOperationSnapshot>('launcher_operation_cancel', { id })
  },
  backups: {
    list: (workspaceId: string) => invokeRuntime<ServerBackupSummary[]>('server_backup_list', { workspaceId }),
    estimate: (workspaceId: string) => invokeRuntime<ServerBackupEstimate>('server_backup_estimate', { workspaceId }),
    create: (workspaceId: string) => invokeRuntime<ServerBackupSummary>('server_backup_create', { workspaceId }),
    restore: (workspaceId: string, backupId: string) => invokeRuntime<ServerRestoreResult>('server_backup_restore', { workspaceId, backupId }),
    delete: (workspaceId: string, backupId: string) => invokeRuntime<void>('server_backup_delete', { workspaceId, backupId })
  },
  workspace: {
    state: () => invokeRuntime<WorkspaceState>('workspace_state'),
    provisioningStatus: () => invokeRuntime<WorkspaceProvisioningStatus>('workspace_provisioning_status'),
    provision: () => invokeRuntime<WorkspaceProvisionResult>('workspace_provision'),
    runtimeUpdateStatus: () => invokeRuntime<RuntimeUpdateStatus>('workspace_runtime_update_status'),
    updatePaper: () => invokeRuntime<RuntimeUpdateStatus>('workspace_update_paper'),
    acceptEula: () => invokeRuntime<WorkspaceProvisioningStatus>('workspace_accept_eula'),
    pickParent: () => invokeRuntime<string | null>('workspace_pick_parent'),
    create: (parentPath: string, name: string) => invokeRuntime<WorkspaceEntry>('workspace_create', { parentPath, name }),
    pickAdoption: () => invokeRuntime<AdoptionPlan | null>('workspace_adoption_pick'),
    adopt: (rootPath: string, name?: string | null) => invokeRuntime<WorkspaceEntry>('workspace_adopt', { rootPath, name: name ?? null }),
    activate: (id: string) => invokeRuntime<WorkspaceEntry>('workspace_activate', { id }),
    close: () => invokeRuntime<void>('workspace_close'),
    openFolder: (id: string) => invokeRuntime<void>('workspace_open_folder', { id }),
    pickLocation: (id: string) => invokeRuntime<string | null>('workspace_location_pick', { id }),
    reconnectLocation: (id: string, rootPath: string) => invokeRuntime<WorkspaceEntry>('workspace_location_reconnect', { id, rootPath }),
    duplicateEstimate: (id: string, parentPath: string) => invokeRuntime<WorkspaceDuplicateEstimate>('workspace_duplicate_estimate', { id, parentPath }),
    duplicate: (id: string, parentPath: string, name: string) => invokeRuntime<WorkspaceEntry>('workspace_duplicate', { id, parentPath, name }),
    removeFromLibrary: (id: string) => invokeRuntime<void>('workspace_remove_from_library', { id }),
    delete: (id: string, typedDisplayName: string) => invokeRuntime<void>('workspace_delete', { id, typedDisplayName })
  },
  server: {
    preflight: () => invokeRuntime<ServerPreflight>('server_preflight'),
    snapshot: () => invokeRuntime<ServerSnapshot>('server_snapshot'),
    runtimes: () => invokeRuntime<ServerRuntimeSummary[]>('server_runtime_list'),
    connectionPort: () => invokeRuntime<number | null>('server_connection_port'),
    command: (command: string) => invokeRuntime<void>('server_console_command', { command }),
    start: () => invokeRuntime<void>('server_start'),
    stop: () => invokeRuntime<void>('server_stop'),
    restart: () => invokeRuntime<void>('server_restart'),
    recoverDetached: () => invokeRuntime<DetachedRecoveryResult>('server_recover_detached'),
    logTail: (path: string) => invokeRuntime<ServerLogTail>('server_log_tail', { path }),
    resources: () => invokeRuntime<ServerResourceProfile>('server_resource_profile'),
    saveResources: (request: ResourceUpdateRequest) => invokeRuntime<ServerResourceProfile>('server_resource_save', { request })
  },
  plugins: {
    list: () => invokeRuntime<PluginSummary[]>('plugin_list'),
    pickJar: () => invokeRuntime<string | null>('plugin_pick_jar'),
    install: (jarPath: string) => invokeRuntime<PluginInstallResult>('plugin_install', { jarPath }),
    update: (pluginId: string, jarPath: string) => invokeRuntime<PluginInstallResult>('plugin_update', { pluginId, jarPath }),
    setEnabled: (pluginId: string, enabled: boolean) => invokeRuntime<void>('plugin_set_enabled', { pluginId, enabled }),
    remove: (pluginId: string) => invokeRuntime<void>('plugin_remove', { pluginId }),
    removeProblem: (pluginId: string, jarFileName: string) => invokeRuntime<void>('plugin_remove_problem', { pluginId, jarFileName }),
    resolveDuplicates: (pluginId: string, keepJarFileName: string) => invokeRuntime<PluginInstallResult>('plugin_resolve_duplicates', { pluginId, keepJarFileName })
  },
  client: {
    status: () => invokeRuntime<ClientIntegrationStatus>('client_integration_status'),
    selectProfile: (profilePath: string) => invokeRuntime<ClientIntegrationStatus>('client_integration_select_profile', { profilePath }),
    pickProfile: () => invokeRuntime<ClientIntegrationStatus>('client_integration_pick_profile'),
    sync: () => invokeRuntime<ClientIntegrationStatus>('client_integration_sync')
  },
  worlds: {
    list: () => invokeRuntime<ManagedWorldSummary[]>('world_list'),
    create: (request: CreateWorldRequest) => invokeRuntime<ManagedWorldSummary>('world_create', { request }),
    settings: (worldId: string) => invokeRuntime<WorldSettingsSnapshot>('world_settings', { worldId }),
    updateSettings: (worldId: string, request: UpdateWorldSettingsRequest) => invokeRuntime<WorldSettingsSnapshot>('world_update_settings', { worldId, request }),
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