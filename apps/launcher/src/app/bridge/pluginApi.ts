import { invokeRuntime } from './invokeRuntime';
import type { PluginInstallResult, PluginSummary } from './runtimeTypes';

export const pluginApi = {
  list: () => invokeRuntime<PluginSummary[]>('plugin_list'),
  pickJar: () => invokeRuntime<string | null>('plugin_pick_jar'),
  install: (jarPath: string) => invokeRuntime<PluginInstallResult>('plugin_install', { jarPath }),
  update: (pluginId: string, jarPath: string) =>
    invokeRuntime<PluginInstallResult>('plugin_update', { pluginId, jarPath }),
  setEnabled: (pluginId: string, enabled: boolean) =>
    invokeRuntime<void>('plugin_set_enabled', { pluginId, enabled }),
  remove: (pluginId: string) => invokeRuntime<void>('plugin_remove', { pluginId }),
  removeProblem: (pluginId: string, jarFileName: string) =>
    invokeRuntime<void>('plugin_remove_problem', { pluginId, jarFileName }),
  resolveDuplicates: (pluginId: string, keepJarFileName: string) =>
    invokeRuntime<PluginInstallResult>('plugin_resolve_duplicates', { pluginId, keepJarFileName })
};
