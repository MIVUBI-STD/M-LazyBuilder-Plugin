import { runtimeApi } from './runtimeApi';
import { runtimePreviewProduct } from './runtimePreviewProduct';
import type { ServerBackupEstimate, ServerBackupSummary, ServerRuntimeSummary } from './runtimeApi';

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

const previewRuntimeProduct = {
  ...runtimePreviewProduct,
  diagnostics: {
    ...runtimePreviewProduct.diagnostics,
    exportSupportBundle: async () => 'C:\\Users\\Builder\\Desktop\\LazyBuilder-Support.zip'
  },
  server: {
    ...runtimePreviewProduct.server,
    snapshot: async (_workspaceId?: string) => runtimePreviewProduct.server.snapshot(),
    command: async (_command: string, _workspaceId?: string) => undefined,
    stop: async (_workspaceId?: string) => runtimePreviewProduct.server.stop(),
    recoverDetached: async (_workspaceId?: string) => runtimePreviewProduct.server.recoverDetached(),
    logTail: async (path: string, _workspaceId?: string) => runtimePreviewProduct.server.logTail(path),
    runtimes: async (): Promise<ServerRuntimeSummary[]> => [{
      workspaceId: 'preview-build-server',
      workspaceName: 'MIVUBI Build Server',
      state: 'Online',
      pid: 14872,
      paperPort: 25565,
      usedMemoryBytes: 3.1 * 1024 ** 3,
      maxMemoryBytes: 6 * 1024 ** 3
    }],
    connectionPort: async () => 25565
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
