import { runtimeProduct } from './bridge/runtimeProductFacade';
import type { ServerRuntimeSummary } from './bridge/runtimeApi';

const ACTIVE_RUNTIME_STATES = new Set(['Starting', 'Online', 'Stopping', 'Detached']);

export function canOpenRuntimeConsole(runtime: ServerRuntimeSummary | null) {
  return runtime?.state === 'Online';
}

export function canStopLibraryRuntime(runtime: ServerRuntimeSummary | null) {
  return runtime != null && ACTIVE_RUNTIME_STATES.has(runtime.state);
}

export function canMutateLibraryServer(runtime: ServerRuntimeSummary | null) {
  return runtime == null || !ACTIVE_RUNTIME_STATES.has(runtime.state);
}

export async function stopLibraryRuntime(workspaceId: string) {
  const target = workspaceId.trim();
  if (!target) throw new Error('Server workspace id is required.');
  await runtimeProduct.server.stop(target);
}
