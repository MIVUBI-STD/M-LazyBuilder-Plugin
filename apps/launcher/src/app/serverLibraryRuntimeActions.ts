import { runtimeProduct } from './bridge/runtimeProductFacade';
import type { ServerRuntimeSummary } from './bridge/runtimeApi';

export function canOpenRuntimeConsole(runtime: ServerRuntimeSummary | null) {
  return runtime?.state === 'Online';
}

export function canStopLibraryRuntime(runtime: ServerRuntimeSummary | null) {
  return runtime != null && ['Starting', 'Online', 'Stopping', 'Detached'].includes(runtime.state);
}

export async function stopLibraryRuntime(workspaceId: string) {
  const target = workspaceId.trim();
  if (!target) throw new Error('Server workspace id is required.');
  await runtimeProduct.server.stop(target);
}
