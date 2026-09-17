import { appApi } from './appApi';
import { clientApi } from './clientApi';
import { pluginApi } from './pluginApi';
import { serverApi } from './serverApi';
import { workspaceApi } from './workspaceApi';
import { worldApi } from './worldApi';

export { RuntimeError, runtimeError } from './errors';
export type { RecoveryAction, RuntimeCommandError } from './errors';
export * from './runtimeTypes';

/**
 * Canonical frontend boundary for production Tauri commands.
 *
 * Keep this module as a composition-only facade. Domain command ownership lives
 * in the adjacent *Api modules so product surfaces share one public contract
 * without growing a second application/runtime authority in Svelte.
 */
export const runtimeApi = {
  diagnostics: appApi.diagnostics,
  startup: appApi.startup,
  settings: appApi.settings,
  readiness: appApi.readiness,
  /** Compatibility alias; new product code should prefer `readiness`. */
  health: appApi.health,
  operations: appApi.operations,
  backups: workspaceApi.backups,
  workspace: workspaceApi.workspace,
  server: serverApi.server,
  plugins: pluginApi,
  client: clientApi,
  worlds: worldApi
};
