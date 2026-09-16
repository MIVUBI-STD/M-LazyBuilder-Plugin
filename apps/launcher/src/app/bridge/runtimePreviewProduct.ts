import type {
  AdoptionPlan,
  ClientIntegrationStatus,
  DiagnosticSummary,
  LauncherOperationSnapshot,
  LauncherSettings,
  ManagedWorldSummary,
  PluginInstallResult,
  PluginSummary,
  RuntimeUpdateStatus,
  ServerBackupEstimate,
  ServerBackupSummary,
  ServerHealthSnapshot,
  ServerLogTail,
  ServerPreflight,
  ServerRepairPlan,
  ServerRepairResult,
  ServerResourceProfile,
  ServerRestoreResult,
  ServerSnapshot,
  StartupReport,
  UpdateWorldSettingsRequest,
  WorkspaceDuplicateEstimate,
  WorkspaceEntry,
  WorkspaceProvisionResult,
  WorkspaceProvisioningStatus,
  WorkspaceState,
  WorldSettingsSnapshot,
  WorldTaskSnapshot
} from './runtimeApi';

/** Deterministic browser-only runtime for visual-preview proof. */

const activeWorkspace: WorkspaceEntry = {
  id: 'preview-build-server',
  name: 'MIVUBI Build Server',
  path: 'D:\\LazyBuilder\\MIVUBI Build Server',
  lastOpenedUnixSeconds: 1_788_400_000
};

const missingWorkspace: WorkspaceEntry = {
  id: 'missing-preview-server',
  name: 'Moved Build Server',
  path: 'E:\\Old Server Drive\\Moved Build Server',
  lastOpenedUnixSeconds: 1_788_100_000
};

const recentWorkspaces: WorkspaceEntry[] = [
  activeWorkspace,
  missingWorkspace,
  { id: 'museum', name: 'Museum Khatulistiwa', path: 'D:\\LazyBuilder\\Museum Khatulistiwa', lastOpenedUnixSeconds: 1_788_300_000 },
  { id: 'tana', name: 'Tana Samawa', path: 'D:\\LazyBuilder\\Tana Samawa', lastOpenedUnixSeconds: 1_787_900_000 },
  { id: 'jalur', name: 'Jalur Tanam', path: 'D:\\LazyBuilder\\Jalur Tanam', lastOpenedUnixSeconds: 1_787_500_000 },
  { id: 'arena', name: 'Rampogan Arena', path: 'D:\\LazyBuilder\\Rampogan Arena', lastOpenedUnixSeconds: 1_787_000_000 }
];

const previewOperations: LauncherOperationSnapshot[] = [{
  id: 'preview-operation-duplicate', correlationId: 'operation-preview-1', kind: 'duplicate-server',
  resource: `workspace:${activeWorkspace.id}`, state: 'SUCCEEDED', phase: 'publishing', status: 'Server duplicated', details: '',
  progress: { current: 2_400_000_000, total: 2_400_000_000, unit: 'bytes' }, canCancel: false, cancelRequested: false,
  warnings: [], error: null, createdAtUnixSeconds: 1_788_400_000, updatedAtUnixSeconds: 1_788_400_030, completedAtUnixSeconds: 1_788_400_030
}];

const previewStartup: StartupReport = {
  ready: true, degraded: false, startedAtUnixSeconds: 1_788_399_990, completedAtUnixSeconds: 1_788_399_991,
  runtimeTempPath: 'C:\\Users\\Builder\\AppData\\Local\\LazyBuilder\\temp',
  steps: [
    { key: 'runtime-environment', state: 'READY', summary: 'Runtime environment ready', details: 'Preview runtime.' },
    { key: 'launcher-settings', state: 'READY', summary: 'Launcher settings ready', details: 'Settings schema 1 loaded.' },
    { key: 'workspace-registry', state: 'READY', summary: 'Server library ready', details: 'Registry loaded.' },
    { key: 'server-restore-recovery', state: 'READY', summary: 'Server restore state reconciled', details: 'No interrupted restore requires recovery.' },
    { key: 'server-process-reconciliation', state: 'READY', summary: 'Background server state reconciled', details: 'No recovery required.' }
  ]
};

let previewSettings: LauncherSettings = { schemaVersion: 1, rememberLastServer: true, confirmCloseWhileServerRunning: true, autoCheckUpdates: true, updateChannel: 'stable' };

function params() { return typeof window === 'undefined' ? new URLSearchParams() : new URLSearchParams(window.location.search); }
function previewKind() { return params().get('preview') ?? 'active'; }
function previewPage() { return params().get('page') ?? 'Overview'; }
function workspaceState(): WorkspaceState { return { active: previewKind() === 'library' || previewKind() === 'missing-location' ? null : activeWorkspace, recent: recentWorkspaces }; }

function healthSnapshot(): ServerHealthSnapshot {
  if (previewKind() === 'repair') {
    return {
      workspaceId: activeWorkspace.id, workspaceName: activeWorkspace.name, state: 'NEEDS_ATTENTION', ready: false, running: false,
      checks: [
        { key: 'workspace-location', ready: true, summary: 'Server location available', details: activeWorkspace.path, repairable: false },
        { key: 'workspace-manifest', ready: true, summary: 'Workspace identity valid', details: 'workspace.json', repairable: false },
        { key: 'workspace-config', ready: true, summary: 'Workspace configuration ready', details: 'config', repairable: true },
        { key: 'paper-runtime', ready: false, summary: 'Paper runtime missing', details: 'paper.jar', repairable: true },
        { key: 'java-runtime', ready: true, summary: 'Java runtime ready', details: 'Java 21', repairable: true },
        { key: 'core-modules', ready: false, summary: 'Core modules missing', details: 'plugins', repairable: true },
        { key: 'minecraft-eula', ready: false, summary: 'Minecraft EULA requires acceptance', details: 'eula.txt', repairable: false },
        { key: 'process-state', ready: true, summary: 'Server is offline', details: 'No Paper process owns this workspace.', repairable: false }
      ]
    };
  }
  return {
    workspaceId: activeWorkspace.id, workspaceName: activeWorkspace.name, state: 'READY', ready: true, running: false,
    checks: [
      { key: 'workspace-location', ready: true, summary: 'Server location available', details: activeWorkspace.path, repairable: false },
      { key: 'workspace-manifest', ready: true, summary: 'Workspace identity valid', details: 'workspace.json', repairable: false },
      { key: 'paper-runtime', ready: true, summary: 'Paper runtime ready', details: 'paper.jar', repairable: true },
      { key: 'java-runtime', ready: true, summary: 'Java runtime ready', details: 'Java 21', repairable: true },
      { key: 'core-modules', ready: true, summary: 'Core modules ready', details: 'plugins', repairable: true },
      { key: 'minecraft-eula', ready: true, summary: 'Minecraft EULA accepted', details: 'eula.txt', repairable: false },
      { key: 'process-state', ready: true, summary: 'Server is offline', details: 'No Paper process owns this workspace.', repairable: false }
    ]
  };
}

function repairPlan(): ServerRepairPlan {
  if (previewKind() !== 'repair') return { workspaceId: activeWorkspace.id, workspaceName: activeWorkspace.name, canRepair: false, blockedReason: '', repairs: [], manualActions: [], health: healthSnapshot() };
  return {
    workspaceId: activeWorkspace.id, workspaceName: activeWorkspace.name, canRepair: true, blockedReason: '',
    repairs: [
      { checkKey: 'paper-runtime', title: 'Repair Paper runtime', details: 'Provision the supported Paper runtime only because paper.jar is missing.' },
      { checkKey: 'core-modules', title: 'Repair LazyBuilder core modules', details: 'Republish LazyBuilder-owned World Manager and Utilities Manager transactionally.' }
    ],
    manualActions: [{ checkKey: 'minecraft-eula', title: 'Accept Minecraft EULA', details: 'EULA acceptance always requires explicit user confirmation.' }],
    health: healthSnapshot()
  };
}

const previewBackups: ServerBackupSummary[] = [{
  id: 'backup-preview-1', workspaceId: activeWorkspace.id, workspaceName: activeWorkspace.name,
  path: 'D:\\LazyBuilder\\.lazybuilder-backups\\preview-build-server\\backup-preview-1', createdUnixSeconds: 1_788_390_000, sourceBytes: 2_400_000_000
}];

function provisioningStatus(): WorkspaceProvisioningStatus {
  const setup = previewKind() === 'setup';
  return setup
    ? { workspaceCreated: true, javaReady: true, paperReady: true, coreModulesReady: true, configReady: true, eulaAccepted: false, ready: false, nextStep: 'Accept the Minecraft EULA' }
    : { workspaceCreated: true, javaReady: true, paperReady: true, coreModulesReady: true, configReady: true, eulaAccepted: true, ready: true, nextStep: 'Ready' };
}

function serverSnapshot(): ServerSnapshot {
  const kind = previewKind();
  const forcedStates: Record<string, ServerSnapshot['state']> = {
    starting: 'Starting',
    stopping: 'Stopping',
    detached: 'Detached',
    crashed: 'Crashed'
  };
  const forcedState = forcedStates[kind];
  if (forcedState) {
    const health = forcedState === 'Detached' ? 'Warning' : forcedState === 'Crashed' ? 'Critical' : 'Good';
    const hasProcess = forcedState !== 'Crashed';
    return {
      state: forcedState,
      health,
      cpuLoadPercent: 0,
      usedMemoryBytes: 0,
      maxMemoryBytes: 6 * 1024 ** 3,
      pid: hasProcess ? 14872 : null,
      logPath: 'D:\\LazyBuilder\\MIVUBI Build Server\\logs\\latest.log'
    };
  }
  const offline = previewPage() === 'Plugins' || previewPage() === 'Settings' || kind === 'repair';
  return { state: offline ? 'Offline' : 'Online', health: offline ? 'Offline' : 'Good', cpuLoadPercent: offline ? 0 : 17, usedMemoryBytes: offline ? 0 : 2.4 * 1024 ** 3, maxMemoryBytes: 6 * 1024 ** 3, pid: offline ? null : 14872, logPath: 'D:\\LazyBuilder\\MIVUBI Build Server\\logs\\latest.log' };
}

const diagnostics: DiagnosticSummary = { launcherVersion: '0.1.0-preview', launcherLogPath: 'C:\\Users\\Builder\\AppData\\Local\\LazyBuilder\\logs\\launcher.log', workspaceName: activeWorkspace.name, workspacePath: activeWorkspace.path, minecraftVersion: '1.21.4', serverPlatform: 'paper', paperBuild: 232, serverState: 'Offline', pid: null, javaVersion: '21.0.8', maxMemoryMb: 6144 };
const pluginList: PluginSummary[] = [
  { id: 'worldedit', displayName: 'WorldEdit', version: '7.3.14', category: 'Builder', state: 'Enabled', managedByLazyBuilder: false, mutable: true },
  { id: 'axiom-paper', displayName: 'Axiom Paper Support', version: '4.7.1', category: 'Builder', state: 'Enabled', managedByLazyBuilder: false, mutable: true },
  { id: 'luckperms', displayName: 'LuckPerms', version: '5.5.10', category: 'Permissions', state: 'Disabled', managedByLazyBuilder: false, mutable: true },
  { id: 'fastasyncworldedit', displayName: 'FastAsyncWorldEdit', version: '2.13.0', category: 'Builder', state: 'Problem', problemDetail: 'A duplicate plugin JAR needs attention.', candidateFiles: ['FastAsyncWorldEdit-2.13.0.jar', 'FastAsyncWorldEdit-old.jar'], managedByLazyBuilder: false, mutable: true },
  { id: 'lazymap-core', displayName: 'LazyBuilder World Manager', version: '0.1.0', category: 'Core', state: 'Enabled', managedByLazyBuilder: true, mutable: false },
  { id: 'lazyutilities-core', displayName: 'LazyBuilder Utilities Manager', version: '0.1.0', category: 'Core', state: 'Enabled', managedByLazyBuilder: true, mutable: false }
];
const worlds: ManagedWorldSummary[] = [
  { id: 'world-1', displayName: 'Tana Samawa', kind: 'FLAT', lifecycle: 'ACTIVE', defaultGameMode: 'CREATIVE' },
  { id: 'world-2', displayName: 'Museum Khatulistiwa', kind: 'IMPORTED', lifecycle: 'ACTIVE', defaultGameMode: 'CREATIVE' },
  { id: 'world-3', displayName: 'Jalur Tanam', kind: 'FLAT', lifecycle: 'ACTIVE', defaultGameMode: 'CREATIVE' },
  { id: 'world-4', displayName: 'Rampogan Archive', kind: 'IMPORTED', lifecycle: 'ARCHIVED', defaultGameMode: 'ADVENTURE' }
];
const clientStatus: ClientIntegrationStatus = {
  modrinthDetected: true,
  profiles: [{ name: 'LazyBuilder 1.21.4', path: 'C:\\Users\\Builder\\AppData\\Roaming\\com.modrinth.theseus\\profiles\\LazyBuilder 1.21.4', modrinthRoot: 'C:\\Users\\Builder\\AppData\\Roaming\\com.modrinth.theseus', modsPath: 'C:\\Users\\Builder\\AppData\\Roaming\\com.modrinth.theseus\\profiles\\LazyBuilder 1.21.4\\mods', gameVersion: '1.21.4', loader: 'Fabric', verification: 'profile metadata', compatible: true, selected: true }],
  selectedProfile: { name: 'LazyBuilder 1.21.4', path: 'C:\\Users\\Builder\\AppData\\Roaming\\com.modrinth.theseus\\profiles\\LazyBuilder 1.21.4', modrinthRoot: 'C:\\Users\\Builder\\AppData\\Roaming\\com.modrinth.theseus', modsPath: 'C:\\Users\\Builder\\AppData\\Roaming\\com.modrinth.theseus\\profiles\\LazyBuilder 1.21.4\\mods', gameVersion: '1.21.4', loader: 'Fabric', verification: 'profile metadata', compatible: true, selected: true },
  selectedProfileMissing: false,
  mods: [
    { id: 'map-manager', displayName: 'Map Manager', state: 'Installed', installedFiles: ['lazybuilder-map-manager-0.1.0-SNAPSHOT.jar'], targetFile: 'lazybuilder-map-manager-0.1.0-SNAPSHOT.jar', bundled: true },
    { id: 'utility-manager', displayName: 'Utility Manager', state: 'Installed', installedFiles: ['lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar'], targetFile: 'lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar', bundled: true },
    { id: 'performance-manager', displayName: 'Performance Manager', state: 'Installed', installedFiles: ['lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar'], targetFile: 'lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar', bundled: true }
  ],
  ready: true, message: 'LazyBuilder client components are synchronized with this profile.'
};
function completedTask(worldId = 'world-1'): WorldTaskSnapshot { return { taskId: 'preview-task', taskType: 'PREVIEW', worldId, state: 'SUCCEEDED', progressPercent: 100, message: 'Complete', result: '', error: '', createdAt: '2026-09-15T16:00:00Z', updatedAt: '2026-09-15T16:00:01Z' }; }
const pluginResult: PluginInstallResult = { success: true, pluginId: 'preview-plugin', message: 'Plugin changed. Restart the server to apply it.', restartRequired: true };
const resourceProfile: ServerResourceProfile = { totalMemoryMb: 12288, safeMaxMemoryMb: 8192, currentMaxMemoryMb: 6144, currentMinMemoryMb: 1024, recommendedMaxMemoryMb: 6144, warning: '' };

export const runtimePreviewProduct = {
  diagnostics: { summary: async () => diagnostics },
  startup: { status: async () => previewStartup },
  settings: { get: async () => previewSettings, save: async (settings: LauncherSettings) => { previewSettings = settings; return previewSettings; } },
  health: {
    server: async (_id: string) => healthSnapshot(),
    repairPlan: async (_id: string) => repairPlan(),
    repair: async (_id: string): Promise<ServerRepairResult> => ({ repairedChecks: repairPlan().repairs.map((item) => item.checkKey), health: healthSnapshot() })
  },
  operations: {
    list: async () => previewOperations,
    get: async (id: string) => previewOperations.find((operation) => operation.id === id) ?? previewOperations[0],
    cancel: async (id: string) => previewOperations.find((operation) => operation.id === id) ?? previewOperations[0]
  },
  backups: {
    list: async (_workspaceId: string) => previewBackups,
    estimate: async (_workspaceId: string): Promise<ServerBackupEstimate> => ({ sourceBytes: 2_400_000_000, requiredBytes: 3_000_000_000, availableBytes: 120_000_000_000 }),
    create: async (_workspaceId: string) => previewBackups[0],
    restore: async (_workspaceId: string, backupId: string): Promise<ServerRestoreResult> => ({ restoredBackupId: backupId, safetyBackup: previewBackups[0], cleanupPending: false }),
    delete: async (_workspaceId: string, _backupId: string) => undefined
  },
  workspace: {
    state: async () => workspaceState(), provisioningStatus: async () => provisioningStatus(),
    provision: async (): Promise<WorkspaceProvisionResult> => ({ javaPath: 'C:\\LazyBuilder\\runtime\\java.exe', paperBuild: 232, coreVersion: '0.1.0', status: provisioningStatus() }),
    runtimeUpdateStatus: async (): Promise<RuntimeUpdateStatus> => ({ currentPaperBuild: 232, latestPaperBuild: 232, paperUpdateAvailable: false }),
    updatePaper: async (): Promise<RuntimeUpdateStatus> => ({ currentPaperBuild: 232, latestPaperBuild: 232, paperUpdateAvailable: false }),
    acceptEula: async () => ({ ...provisioningStatus(), eulaAccepted: true, ready: true, nextStep: 'Ready' }),
    pickParent: async () => 'D:\\LazyBuilder',
    create: async (_parentPath: string, name: string) => ({ ...activeWorkspace, id: `preview-${name.toLowerCase().replace(/\s+/g, '-')}`, name }),
    pickAdoption: async (): Promise<AdoptionPlan> => ({ root: 'D:\\Existing Server', name: 'Existing Server', paperJar: 'paper.jar', worlds: ['world'], serverEntries: ['paper.jar'], legacyPluginsToDisable: [], preservedEntries: ['plugins'], warnings: [] }),
    adopt: async (_rootPath: string, name?: string | null) => ({ ...activeWorkspace, name: name || 'Existing Server' }), activate: async (id: string) => {
      if (previewKind() === 'missing-location' && id === missingWorkspace.id) throw new Error(`Saved server workspace is currently unavailable: ${missingWorkspace.path}`);
      return recentWorkspaces.find((item) => item.id === id) ?? activeWorkspace;
    },
    close: async () => undefined, openFolder: async (_id: string) => undefined,
    pickLocation: async (_id: string) => 'D:\\Recovered Servers\\Moved Build Server',
    reconnectLocation: async (id: string, rootPath: string) => ({ ...(recentWorkspaces.find((item) => item.id === id) ?? missingWorkspace), path: rootPath, lastOpenedUnixSeconds: 1_788_400_100 }),
    duplicateEstimate: async (_id: string, _parentPath: string): Promise<WorkspaceDuplicateEstimate> => ({ sourceBytes: 2_400_000_000, requiredBytes: 3_000_000_000, availableBytes: 120_000_000_000 }),
    duplicate: async (_id: string, parentPath: string, name: string) => ({ ...activeWorkspace, id: 'preview-copy', name, path: `${parentPath}\\${name}` }),
    removeFromLibrary: async (_id: string) => undefined, delete: async (_id: string, _typedDisplayName: string) => undefined
  },
  server: {
    preflight: async (): Promise<ServerPreflight> => ({ ready: previewKind() !== 'repair', workspace: activeWorkspace.path, serverDirectory: `${activeWorkspace.path}\\server`, paperJar: `${activeWorkspace.path}\\server\\paper.jar`, worldsDirectory: `${activeWorkspace.path}\\server\\worlds`, javaPath: 'C:\\LazyBuilder\\runtime\\java.exe', javaVersion: '21.0.8', logDirectory: `${activeWorkspace.path}\\logs`, issues: previewKind() === 'repair' ? ['Paper runtime is missing'] : [] }),
    snapshot: async () => serverSnapshot(), start: async () => undefined, stop: async () => undefined, restart: async () => undefined,
    recoverDetached: async () => ({ pid: 14872, stopped: true, message: 'External server stopped.' }),
    logTail: async (_path: string): Promise<ServerLogTail> => ({ path: `${activeWorkspace.path}\\logs\\latest.log`, content: '[16:00:00 INFO]: Done (3.42s)! For help, type "help"', truncated: false }),
    resources: async () => resourceProfile, saveResources: async (request: { maxMemoryMb: number }) => ({ ...resourceProfile, currentMaxMemoryMb: request.maxMemoryMb })
  },
  plugins: {
    list: async () => pluginList, pickJar: async () => 'D:\\Downloads\\Plugin.jar', install: async (_jarPath: string) => pluginResult,
    update: async (pluginId: string, _jarPath: string) => ({ ...pluginResult, pluginId }), setEnabled: async (_pluginId: string, _enabled: boolean) => undefined,
    remove: async (_pluginId: string) => undefined, removeProblem: async (_pluginId: string, _jarFileName: string) => undefined,
    resolveDuplicates: async (pluginId: string, _keepJarFileName: string) => ({ ...pluginResult, pluginId })
  },
  client: { status: async () => clientStatus, selectProfile: async (_profilePath: string) => clientStatus, pickProfile: async () => clientStatus, sync: async () => clientStatus },
  worlds: {
    list: async () => worlds,
    create: async (request: { folderName: string; displayName: string; kind: 'FLAT' | 'VOID' }) => ({ id: 'preview-new-world', displayName: request.displayName, kind: request.kind, lifecycle: 'ACTIVE' as const, defaultGameMode: 'CREATIVE' as const }),
    settings: async (worldId: string): Promise<WorldSettingsSnapshot> => ({ id: worldId, displayName: worlds.find((world) => world.id === worldId)?.displayName ?? 'World', defaultGameMode: 'CREATIVE', timeOfDayTicks: 6000, weather: 'CLEAR', naturalSpawning: false, daylightCycle: true, weatherCycle: true }),
    updateSettings: async (worldId: string, request: UpdateWorldSettingsRequest): Promise<WorldSettingsSnapshot> => ({ id: worldId, displayName: worlds.find((world) => world.id === worldId)?.displayName ?? 'World', defaultGameMode: request.defaultGameMode ?? 'CREATIVE', timeOfDayTicks: request.timeOfDayTicks ?? 6000, weather: request.weather ?? 'CLEAR', naturalSpawning: request.naturalSpawning ?? false, daylightCycle: request.daylightCycle ?? true, weatherCycle: request.weatherCycle ?? true }),
    tasks: async () => [completedTask()], task: async (_taskId: string) => completedTask(), archive: async (worldId: string) => completedTask(worldId), restore: async (worldId: string) => completedTask(worldId), backup: async (worldId: string) => completedTask(worldId),
    duplicate: async (request: { worldId: string }) => completedTask(request.worldId), export: async (request: { worldId: string }) => completedTask(request.worldId), delete: async (request: { worldId: string }) => completedTask(request.worldId),
    pickImport: async () => 'D:\\Imports\\Example World.zip', uploadImport: async (_filePath: string) => 'preview-import.zip', import: async (_request: unknown) => completedTask('preview-import')
  }
};