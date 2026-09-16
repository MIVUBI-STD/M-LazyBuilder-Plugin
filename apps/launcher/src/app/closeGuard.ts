import { getCurrentWindow } from '@tauri-apps/api/window';
import { runtimeProduct } from './bridge/runtimeProductFacade';
import type { LauncherOperationSnapshot } from './bridge/runtimeApi';

const ACTIVE_OPERATION_STATES = new Set(['QUEUED', 'RUNNING', 'CANCELLING']);
const RUNNING_SERVER_STATES = new Set(['Online', 'Starting', 'Stopping', 'Detached']);

function activeOperations(operations: LauncherOperationSnapshot[]) {
  return operations.filter((operation) => ACTIVE_OPERATION_STATES.has(operation.state));
}

function operationCloseMessage(operations: LauncherOperationSnapshot[]) {
  const count = operations.length;
  const names = operations
    .slice(0, 3)
    .map((operation) => operation.kind.split('-').join(' '))
    .join(', ');
  const remainder = count > 3 ? ` and ${count - 3} more` : '';
  return `${count} Launcher operation${count === 1 ? ' is' : 's are'} still running (${names}${remainder}). Closing now will interrupt the operation and may require recovery on the next start. Close LazyBuilder anyway?`;
}

export async function installLauncherCloseGuard(): Promise<() => void> {
  if (import.meta.env.MODE === 'visual-preview') return () => {};

  const appWindow = getCurrentWindow();
  return appWindow.onCloseRequested(async (event) => {
    event.preventDefault();
    try {
      const operations = activeOperations(await runtimeProduct.operations.list());
      if (operations.length > 0) {
        if (window.confirm(operationCloseMessage(operations))) await appWindow.destroy();
        return;
      }

      const settings = await runtimeProduct.settings.get();
      if (!settings.confirmCloseWhileServerRunning) {
        await appWindow.destroy();
        return;
      }

      const snapshot = await runtimeProduct.server.snapshot();
      const running = RUNNING_SERVER_STATES.has(snapshot.state);
      if (!running || window.confirm('A Minecraft server is still running. Close LazyBuilder anyway? The server process may continue outside this Launcher session.')) {
        await appWindow.destroy();
      }
    } catch {
      if (window.confirm('LazyBuilder could not verify active operations or server state. Close the Launcher anyway?')) {
        await appWindow.destroy();
      }
    }
  });
}

export const closeGuardContract = {
  activeOperationStates: ACTIVE_OPERATION_STATES,
  runningServerStates: RUNNING_SERVER_STATES,
};
