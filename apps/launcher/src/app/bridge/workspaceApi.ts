import { invokeRuntime } from './invokeRuntime';
import type {
  AdoptionPlan,
  RuntimeUpdateStatus,
  ServerBackupEstimate,
  ServerBackupSummary,
  ServerRestoreResult,
  WorkspaceDuplicateEstimate,
  WorkspaceEntry,
  WorkspaceProvisionResult,
  WorkspaceProvisioningStatus,
  WorkspaceState
} from './runtimeTypes';

export const workspaceApi = {
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
  }
};
