import { invokeRuntime } from './invokeRuntime';
import type {
  ActiveServerRuntimeStatus,
  DetachedRecoveryResult,
  ResourceUpdateRequest,
  ServerLogTail,
  ServerPreflight,
  ServerResourceProfile,
  ServerRuntimeSummary,
  ServerSnapshot
} from './runtimeTypes';

function workspaceArgs(workspaceId?: string) {
  return workspaceId ? { workspaceId } : undefined;
}

export const serverApi = {
  server: {
    preflight: () => invokeRuntime<ServerPreflight>('server_preflight'),
    status: () => invokeRuntime<ActiveServerRuntimeStatus>('server_runtime_status'),
    snapshot: (workspaceId?: string) => invokeRuntime<ServerSnapshot>('server_snapshot', workspaceArgs(workspaceId)),
    runtimes: () => invokeRuntime<ServerRuntimeSummary[]>('server_runtime_list'),
    connectionPort: () => invokeRuntime<number | null>('server_connection_port'),
    command: (command: string, workspaceId?: string) =>
      invokeRuntime<void>('server_console_command', { command, ...(workspaceId ? { workspaceId } : {}) }),
    start: () => invokeRuntime<void>('server_start'),
    stop: (workspaceId?: string) => invokeRuntime<void>('server_stop', workspaceArgs(workspaceId)),
    restart: () => invokeRuntime<void>('server_restart'),
    recoverDetached: (workspaceId?: string) =>
      invokeRuntime<DetachedRecoveryResult>('server_recover_detached', workspaceArgs(workspaceId)),
    logTail: (path: string, workspaceId?: string) =>
      invokeRuntime<ServerLogTail>('server_log_tail', { path, ...(workspaceId ? { workspaceId } : {}) }),
    resources: () => invokeRuntime<ServerResourceProfile>('server_resource_profile'),
    saveResources: (request: ResourceUpdateRequest) =>
      invokeRuntime<ServerResourceProfile>('server_resource_save', { request })
  }
};
