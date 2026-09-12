import { runtimeApi } from './runtimeApi';

export const runtimeProduct = {
  server: {
    snapshot: runtimeApi.getServerSnapshot,
    start: runtimeApi.startServer,
    stop: runtimeApi.stopServer,
    restart: runtimeApi.restartServer
  },
  plugins: {
    list: runtimeApi.listPlugins
  },
  worlds: {
    list: runtimeApi.listWorlds
  }
};
