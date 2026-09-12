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
  listWorlds: () => invoke<ManagedWorldSummary[]>('world_list')
};
