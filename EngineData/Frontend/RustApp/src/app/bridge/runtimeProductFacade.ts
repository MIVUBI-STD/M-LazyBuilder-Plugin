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
    pickJar: runtimeApi.pickPluginJar,
    install: runtimeApi.installPlugin,
    update: runtimeApi.updatePlugin,
    setEnabled: runtimeApi.setPluginEnabled,
    remove: runtimeApi.removePlugin,
    resolveDuplicates: runtimeApi.resolvePluginDuplicates,
    setCategory: runtimeApi.setPluginCategory
  },
  worlds: {
    list: runtimeApi.listWorlds,
    create: runtimeApi.createWorld,
    load: runtimeApi.loadWorld,
    unload: runtimeApi.unloadWorld,
    settings: runtimeApi.getWorldSettings,
    updateSettings: runtimeApi.updateWorldSettings
  }
};
