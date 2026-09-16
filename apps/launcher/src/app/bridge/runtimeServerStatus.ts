import { invoke } from '@tauri-apps/api/core';
import { RuntimeError, runtimeError, type ServerState } from './runtimeApi';

export type ServerRuntimeSummary = {
  workspaceId: string;
  workspaceName: string;
  state: ServerState;
  pid?: number | null;
  paperPort?: number | null;
};

async function invokeRuntimeStatus<T>(command: string): Promise<T> {
  try {
    return await invoke<T>(command);
  } catch (value) {
    throw new RuntimeError(runtimeError(value));
  }
}

export const runtimeServerStatus = {
  runtimes: () => invokeRuntimeStatus<ServerRuntimeSummary[]>('server_runtime_list'),
  connectionPort: () => invokeRuntimeStatus<number | null>('server_connection_port')
};
