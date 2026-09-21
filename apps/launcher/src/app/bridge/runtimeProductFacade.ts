import { runtimeApi, RuntimeError } from './runtimeApi';
import { runtimePreviewProduct } from './runtimePreviewProduct';
import type { ServerBackupEstimate, ServerBackupSummary, ServerRuntimeSummary, SystemActivitySnapshot, SystemSnapshot } from './runtimeApi';

const previewBackup: ServerBackupSummary = {
  id: 'backup-1788400100000-1000',
  workspaceId: 'preview-build-server',
  workspaceName: 'MIVUBI Build Server',
  path: 'D:\\LazyBuilder\\.lazybuilder-backups\\preview-build-server\\backup-1788400100000-1000',
  createdUnixSeconds: 1_788_400_100,
  sourceBytes: 2_400_000_000
};

let previewBackups: ServerBackupSummary[] = [previewBackup];

const productionRuntimeProduct = runtimeApi;


async function previewSystemActivity(): Promise<SystemActivitySnapshot> {
  const [launcherOperations, worldTasks] = await Promise.all([
    runtimePreviewProduct.operations.list(),
    runtimePreviewProduct.worlds.tasks()
  ]);
  return { launcherOperations, worldTasks, worldTasksAvailable: true, warnings: [] };
}

async function previewSystemSnapshot(): Promise<SystemSnapshot> {
  const [workspace, runtimes, operations] = await Promise.all([
    runtimePreviewProduct.workspace.state(),
    previewRuntimeSummaries(),
    runtimePreviewProduct.operations.list()
  ]);
  const active = workspace.active ?? null;
  const serverHealth = active ? await runtimePreviewProduct.health.server(active.id) : null;
  const busy = operations.some((operation) =>
    operation.state === 'RUNNING'
    || operation.state === 'QUEUED'
    || operation.state === 'CANCELLING'
  );
  const running = runtimes.some((runtime) =>
    active?.id === runtime.workspaceId
    && ['Starting', 'Online', 'Stopping', 'Detached'].includes(runtime.state)
  );
  return {
    readiness: !active ? 'NO_WORKSPACE' : busy ? 'BUSY' : serverHealth?.ready ? 'READY' : 'NEEDS_ATTENTION',
    workspace: active,
    recentWorkspaces: workspace.recent,
    serverHealth,
    runtimes,
    activeOperations: operations.filter((operation) =>
      !['SUCCEEDED', 'FAILED', 'CANCELLED', 'RECOVERY_REQUIRED'].includes(operation.state)
    ),
    capabilities: [
      { key: 'workspace.manage', available: !busy, reason: busy ? 'A workspace operation is active.' : 'Workspace library is available.' },
      { key: 'server.inspect', available: Boolean(active), reason: active ? 'An active workspace is selected.' : 'Open a workspace first.' },
      { key: 'server.start', available: Boolean(active && serverHealth?.ready && !running && !busy), reason: active && serverHealth?.ready && !running && !busy ? 'Server start requirements are satisfied.' : 'Server start requirements are not currently satisfied.' },
      { key: 'server.stop', available: running, reason: running ? 'A server runtime is active.' : 'No active server runtime.' },
      { key: 'world.manage', available: Boolean(active && running && !busy), reason: active && running && !busy ? 'World control can use the active Paper runtime.' : 'World control is not currently available.' },
      { key: 'client.sync', available: Boolean(active && !busy), reason: active && !busy ? 'Client Setup may inspect or synchronize the selected profile.' : 'Open an idle workspace first.' },
      { key: 'diagnostics.export', available: true, reason: 'Launcher diagnostics are always available.' }
    ],
    warnings: []
  };
}

async function previewRuntimeSummaries(): Promise<ServerRuntimeSummary[]> {
  const snapshot = await runtimePreviewProduct.server.snapshot();
  if (snapshot.state === 'Offline' || snapshot.state === 'Crashed') return [];
  return [{
    workspaceId: 'preview-build-server',
    workspaceName: 'MIVUBI Build Server',
    state: snapshot.state,
    pid: snapshot.pid,
    paperPort: snapshot.state === 'Online' ? 25565 : null,
    usedMemoryBytes: snapshot.usedMemoryBytes,
    maxMemoryBytes: snapshot.maxMemoryBytes
  }];
}

const previewRuntimeProduct = {
  system: { snapshot: previewSystemSnapshot, activity: previewSystemActivity },
  ...runtimePreviewProduct,
  readiness: runtimePreviewProduct.health,
  diagnostics: {
    summary: async () => ({
      ...(await runtimePreviewProduct.diagnostics.summary()),
      buildCommit: 'preview',
      buildChannel: 'stable',
      buildTarget: 'windows-x86_64'
    }),
    exportSupportBundle: async () => 'C:\\Users\\Builder\\Desktop\\LazyBuilder-Support.zip'
  },
  workspace: {
    ...runtimePreviewProduct.workspace,
    activate: async (id: string) => {
      try {
        return await runtimePreviewProduct.workspace.activate(id);
      } catch (value) {
        const message = value instanceof Error ? value.message : String(value ?? '');
        if (message.startsWith('Saved server workspace is currently unavailable:')) {
          throw new RuntimeError({
            code: 'WORKSPACE_UNAVAILABLE',
            message,
            recoverable: true,
            action: 'LOCATE_WORKSPACE',
            correlationId: 'preview-workspace-unavailable'
          });
        }
        throw value;
      }
    }
  },
  server: {
    ...runtimePreviewProduct.server,
    status: async () => runtimePreviewProduct.server.status(),
    snapshot: async (_workspaceId?: string) => runtimePreviewProduct.server.snapshot(),
    command: async (_command: string, _workspaceId?: string) => undefined,
    stop: async (_workspaceId?: string) => runtimePreviewProduct.server.stop(),
    recoverDetached: async (_workspaceId?: string) => runtimePreviewProduct.server.recoverDetached(),
    logTail: async (path: string, _workspaceId?: string) => runtimePreviewProduct.server.logTail(path),
    runtimes: previewRuntimeSummaries,
    connectionPort: async () => {
      const snapshot = await runtimePreviewProduct.server.snapshot();
      return snapshot.state === 'Online' ? 25565 : null;
    }
  },
  backups: {
    list: async (_workspaceId: string) => previewBackups,
    estimate: async (_workspaceId: string): Promise<ServerBackupEstimate> => ({
      sourceBytes: 2_400_000_000,
      requiredBytes: 3_000_000_000,
      availableBytes: 120_000_000_000
    }),
    create: async (_workspaceId: string) => {
      const created = { ...previewBackup, id: `backup-${Date.now()}-preview`, createdUnixSeconds: Math.floor(Date.now() / 1000) };
      previewBackups = [created, ...previewBackups];
      return created;
    },
    restore: async (workspaceId: string, backupId: string) => runtimePreviewProduct.backups.restore(workspaceId, backupId),
    delete: async (_workspaceId: string, backupId: string) => {
      previewBackups = previewBackups.filter((item) => item.id !== backupId);
    }
  }
};

/**
 * Production uses the canonical typed Tauri runtime bridge. Vite visual-preview
 * mode swaps only the runtime data source so the real Svelte UI can be rendered
 * deterministically in CI without requiring a local Windows/Tauri session.
 */
export const runtimeProduct = import.meta.env.MODE === 'visual-preview'
  ? previewRuntimeProduct
  : productionRuntimeProduct;
