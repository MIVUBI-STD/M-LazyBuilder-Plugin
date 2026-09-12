import { runtimeApi } from './runtimeApi';

export const runtimeProduct = {
  server: {
    snapshot: runtimeApi.getServerSnapshot,
    start: runtimeApi.startServer,
    stop: runtimeApi.stopServer,
    restart: runtimeApi.restartServer
  },
  plugins: {
    list: runtimeApi.listPlugins,
    install: runtimeApi.installPlugin,
    update: runtimeApi.updatePlugin,
    setEnabled: runtimeApi.setPluginEnabled,
    remove: runtimeApi.removePlugin,
    resolveDuplicates: runtimeApi.resolvePluginDuplicates,
    setCategory: runtimeApi.setPluginCategory
  },
  worlds: {
    list: runtimeApi.listWorlds
  }
};
