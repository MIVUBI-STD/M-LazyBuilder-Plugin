import { getCurrentWindow } from '@tauri-apps/api/window';
import { runtimeProduct } from './bridge/runtimeProductFacade';
import { operationTitle } from './operations/operationPresentation';
import type { LauncherOperationSnapshot, ServerRuntimeSummary } from './bridge/runtimeApi';

const ACTIVE_OPERATION_STATES = new Set(['QUEUED', 'RUNNING', 'CANCELLING']);
const RUNNING_SERVER_STATES = new Set(['Online', 'Starting', 'Stopping', 'Detached']);

export type LauncherCloseRequest = {
  kind: 'operations' | 'servers' | 'unverified';
  title: string;
  message: string;
  details: string[];
  confirmLabel: string;
  dangerous: boolean;
};

export type LauncherCloseRequestHandler = (
  request: LauncherCloseRequest,
  proceed: () => Promise<void>
) => void;

function activeOperations(operations: LauncherOperationSnapshot[]) {
  return operations.filter((operation) => ACTIVE_OPERATION_STATES.has(operation.state));
}

function runningServers(runtimes: ServerRuntimeSummary[]) {
  return runtimes.filter((runtime) => RUNNING_SERVER_STATES.has(runtime.state));
}

function operationCloseRequest(operations: LauncherOperationSnapshot[]): LauncherCloseRequest {
  return {
    kind: 'operations',
    title: 'Tasks are still running',
    message: 'Closing now will interrupt the operation and may require recovery the next time LazyBuilder starts. Wait for active work to finish when possible.',
    details: operations.slice(0, 5).map((operation) => `${operationTitle(operation.kind)} — ${operation.status || 'Running'}`),
    confirmLabel: 'Close anyway',
    dangerous: true
  };
}

function serverCloseRequest(runtimes: ServerRuntimeSummary[]): LauncherCloseRequest {
  const attached = runtimes.filter((runtime) => runtime.state !== 'Detached').length;
  const detached = runtimes.length - attached;
  const behavior = [
    attached > 0 ? `${attached} managed server${attached === 1 ? '' : 's'} will be asked to stop cleanly.` : '',
    detached > 0 ? `${detached} external server process${detached === 1 ? '' : 'es'} may continue running outside LazyBuilder.` : ''
  ].filter(Boolean).join(' ');
  return {
    kind: 'servers',
    title: 'Servers are still running',
    message: behavior || 'One or more Minecraft servers are still running.',
    details: runtimes.map((runtime) => `${runtime.workspaceName} — ${runtime.state === 'Online' ? 'Running' : runtime.state === 'Detached' ? 'Running externally' : runtime.state}`),
    confirmLabel: 'Close LazyBuilder',
    dangerous: detached > 0
  };
}

function unverifiedCloseRequest(): LauncherCloseRequest {
  return {
    kind: 'unverified',
    title: 'Close safety could not be verified',
    message: 'LazyBuilder could not confirm whether tasks or servers are still active. Closing may interrupt work or leave a server running.',
    details: [],
    confirmLabel: 'Close anyway',
    dangerous: true
  };
}

export async function installLauncherCloseGuard(onRequest: LauncherCloseRequestHandler): Promise<() => void> {
  if (import.meta.env.MODE === 'visual-preview') return () => {};

  const appWindow = getCurrentWindow();
  return appWindow.onCloseRequested(async (event) => {
    event.preventDefault();
    const proceed = async () => appWindow.destroy();

    try {
      const operations = activeOperations(await runtimeProduct.operations.list());
      if (operations.length > 0) {
        onRequest(operationCloseRequest(operations), proceed);
        return;
      }

      const settings = await runtimeProduct.settings.get();
      if (!settings.confirmCloseWhileServerRunning) {
        await proceed();
        return;
      }

      const runtimes = runningServers(await runtimeProduct.server.runtimes());
      if (runtimes.length === 0) {
        await proceed();
        return;
      }

      onRequest(serverCloseRequest(runtimes), proceed);
    } catch {
      onRequest(unverifiedCloseRequest(), proceed);
    }
  });
}

export const closeGuardContract = {
  activeOperationStates: ACTIVE_OPERATION_STATES,
  runningServerStates: RUNNING_SERVER_STATES,
};
