import { invoke } from '@tauri-apps/api/core';

export type ServerSnapshot = {
  state: string;
  health: string;
  cpuLoadPercent: number;
  usedMemoryBytes: number;
  maxMemoryBytes: number;
};

export type PluginSummary = {
  id: string;
  displayName: string;
  version: string;
  category: string;
  state: string;
  problemDetail?: string | null;
  candidateFiles?: string[] | null;
};

export type PluginInstallResult = {
  success: boolean;
  pluginId: string;
  message?: string | null;
  restartRequired: boolean;
};

export type ManagedWorldSummary = {
  id: string;
  displayName: string;
  kind: string;
  lifecycle: string;
  runtimeState: string;
  autoLoad: boolean;
  defaultGameMode: string;
};

export const runtimeApi = {
  getServerSnapshot: () => invoke<ServerSnapshot>('server_snapshot'),
  startServer: () => invoke<void>('server_start'),
  stopServer: () => invoke<void>('server_stop'),
  restartServer: () => invoke<void>('server_restart'),

  listPlugins: () => invoke<PluginSummary[]>('plugin_list'),
  installPlugin: (jarPath: string) => invoke<PluginInstallResult>('plugin_install', { jarPath }),
  updatePlugin: (pluginId: string, jarPath: string) =>
    invoke<PluginInstallResult>('plugin_update', { pluginId, jarPath }),
  setPluginEnabled: (pluginId: string, enabled: boolean) =>
    invoke<void>('plugin_set_enabled', { pluginId, enabled }),
  removePlugin: (pluginId: string, removeData = false) =>
    invoke<void>('plugin_remove', { pluginId, removeData }),
  resolvePluginDuplicates: (pluginId: string, keepJarFileName: string) =>
    invoke<PluginInstallResult>('plugin_resolve_duplicates', { pluginId, keepJarFileName }),
  setPluginCategory: (pluginId: string, category: string) =>
    invoke<void>('plugin_set_category', { pluginId, category }),

  listWorlds: () => invoke<ManagedWorldSummary[]>('world_list')
};
