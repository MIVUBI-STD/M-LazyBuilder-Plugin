import { invokeRuntime } from './invokeRuntime';
import type {
  CreateWorldRequest,
  DeleteWorldRequest,
  DuplicateWorldRequest,
  ExportWorldRequest,
  ImportWorldRequest,
  ManagedWorldSummary,
  UpdateWorldSettingsRequest,
  WorldSettingsSnapshot,
  WorldTaskSnapshot
} from './runtimeTypes';

export const worldApi = {
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
};
