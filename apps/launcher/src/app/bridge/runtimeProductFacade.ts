import { runtimeApi } from './runtimeApi';
import { runtimePreviewProduct } from './runtimePreviewProduct';
import type { ServerBackupEstimate, ServerBackupSummary } from './runtimeApi';

const previewBackup: ServerBackupSummary = {
  id: 'backup-1788400100000-1000',
  workspaceId: 'preview-build-server',
  workspaceName: 'MIVUBI Build Server',
  path: 'D:\\LazyBuilder\\.lazybuilder-backups\\preview-build-server\\backup-1788400100000-1000',
  createdUnixSeconds: 1_788_400_100,
  sourceBytes: 2_400_000_000
};

let previewBackups: ServerBackupSummary[] = [previewBackup];

const previewRuntimeProduct = {
  ...runtimePreviewProduct,
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
    delete: async (_workspaceId: string, backupId: string) => {
      previewBackups = previewBackups.filter((item) => item.id !== backupId);
    }
  }
};

/**
 * Production uses the Tauri runtime. Vite visual-preview mode swaps only the
 * runtime data source so the real Svelte UI can be rendered deterministically
 * in CI without requiring a local Windows/Tauri session.
 */
export const runtimeProduct = import.meta.env.MODE === 'visual-preview'
  ? previewRuntimeProduct
  : runtimeApi;
