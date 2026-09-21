export type DiagnosticSummary = { launcherVersion: string; buildCommit?: string; buildChannel?: string; buildTarget?: string; launcherLogPath: string; workspaceName?: string | null; workspacePath?: string | null; minecraftVersion?: string | null; serverPlatform?: string | null; paperBuild?: number | null; serverState: string; pid?: number | null; javaVersion: string; maxMemoryMb: number };
export type StartupStepState = 'READY' | 'WARNING';
export type StartupStep = { key: string; state: StartupStepState; summary: string; details: string };
export type StartupReport = { ready: boolean; degraded: boolean; startedAtUnixSeconds: number; completedAtUnixSeconds: number; runtimeTempPath?: string | null; steps: StartupStep[] };

export type LauncherSettings = {
  schemaVersion: number;
  rememberLastServer: boolean;
  confirmCloseWhileServerRunning: boolean;
  /** Signed in-app update checks are not enabled in the current runtime. */
  autoCheckUpdates: false;
  /** Only the stable channel is accepted until the signed updater runtime is enabled. */
  updateChannel: 'stable';
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


export type SystemReadiness = 'NO_WORKSPACE' | 'NEEDS_ATTENTION' | 'READY' | 'BUSY' | 'DEGRADED';
export type CapabilityStatus = { key: string; available: boolean; reason: string };
export type SystemActivitySnapshot = { launcherOperations: LauncherOperationSnapshot[]; worldTasks: WorldTaskSnapshot[]; worldTasksAvailable: boolean; warnings: string[] };

export type SystemSnapshot = {
  readiness: SystemReadiness;
  workspace?: WorkspaceEntry | null;
  recentWorkspaces: WorkspaceEntry[];
  serverHealth?: ServerReadinessSnapshot | null;
  runtimes: ServerRuntimeSummary[];
  activeOperations: LauncherOperationSnapshot[];
  capabilities: CapabilityStatus[];
  warnings: string[];
};

export type WorkspaceEntry = { id: string; name: string; path: string; lastOpenedUnixSeconds: number };
export type WorkspaceState = { active?: WorkspaceEntry | null; recent: WorkspaceEntry[] };
export type WorkspaceDuplicateEstimate = { sourceBytes: number; requiredBytes: number; availableBytes?: number | null };
export type AdoptionPlan = { root: string; name: string; paperJar: string; worlds: string[]; serverEntries: string[]; legacyPluginsToDisable: string[]; preservedEntries: string[]; warnings: string[] };
export type WorkspaceProvisioningStatus = { workspaceCreated: boolean; javaReady: boolean; paperReady: boolean; coreModulesReady: boolean; configReady: boolean; eulaAccepted: boolean; ready: boolean; nextStep: string };
export type WorkspaceProvisionResult = { javaPath: string; paperBuild?: number | null; coreVersion: string; status: WorkspaceProvisioningStatus };
export type RuntimeUpdateStatus = { currentPaperBuild?: number | null; latestPaperBuild: number; paperUpdateAvailable: boolean };

/** Whether a registered server is safe and complete enough for the requested Launcher operation. */
export type ServerReadinessState = 'READY' | 'NEEDS_ATTENTION' | 'UNAVAILABLE' | 'BUSY';
export type ServerReadinessCheck = { key: string; ready: boolean; summary: string; details: string; repairable: boolean };
export type ServerReadinessSnapshot = { workspaceId: string; workspaceName: string; state: ServerReadinessState; ready: boolean; running: boolean; checks: ServerReadinessCheck[] };

/** Compatibility aliases while product surfaces migrate from the older overloaded "health" name. */
export type ServerHealthState = ServerReadinessState;
export type ServerHealthCheck = ServerReadinessCheck;
export type ServerHealthSnapshot = ServerReadinessSnapshot;

export type ServerRepairItem = { checkKey: string; title: string; details: string };
export type ServerRepairPlan = { workspaceId: string; workspaceName: string; canRepair: boolean; blockedReason: string; repairs: ServerRepairItem[]; manualActions: ServerRepairItem[]; health: ServerReadinessSnapshot };
export type ServerRepairResult = { repairedChecks: string[]; health: ServerReadinessSnapshot };

export type ServerState = 'Offline' | 'Starting' | 'Online' | 'Stopping' | 'Detached' | 'Crashed';
export type ServerRuntimeCondition = 'Offline' | 'Good' | 'Warning' | 'Critical';
/** Compatibility alias for the older runtime-condition type name. */
export type ServerHealth = ServerRuntimeCondition;
export type ServerSnapshot = { state: ServerState; health: ServerRuntimeCondition; cpuLoadPercent: number; usedMemoryBytes: number; maxMemoryBytes: number; pid?: number | null; logPath: string };
export type ActiveServerRuntimeStatus = { snapshot: ServerSnapshot; connectionPort?: number | null };
export type ServerRuntimeSummary = { workspaceId: string; workspaceName: string; state: ServerState; pid?: number | null; paperPort?: number | null; usedMemoryBytes: number; maxMemoryBytes: number };
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
