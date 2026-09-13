import { runtimeApi } from './runtimeApi';

export const runtimeProduct = {
  workspace: {
    state: runtimeApi.getWorkspaceState,
    provisioningStatus: runtimeApi.getWorkspaceProvisioningStatus,
    provision: runtimeApi.provisionWorkspace,
    acceptEula: runtimeApi.acceptWorkspaceEula,
    pickParent: runtimeApi.pickWorkspaceParent,
    create: runtimeApi.createWorkspace,
    open: runtimeApi.openWorkspace,
    pickAdoption: runtimeApi.pickAdoptionServer,
    adopt: runtimeApi.adoptWorkspace,
    activate: runtimeApi.activateWorkspace,
    close: runtimeApi.closeWorkspace
  },
  server: {
    preflight: runtimeApi.getServerPreflight,
    snapshot: runtimeApi.getServerSnapshot,
    start: runtimeApi.startServer,
    stop: runtimeApi.stopServer,
    restart: runtimeApi.restartServer,
    recoverDetached: runtimeApi.recoverDetachedServer,
    logTail: runtimeApi.readServerLogTail,
    resources: runtimeApi.getServerResourceProfile,
    saveResources: runtimeApi.saveServerResources,
    applyResourcePreset: runtimeApi.applyServerResourcePreset
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
    updateSettings: runtimeApi.updateWorldSettings,
    tasks: runtimeApi.listWorldTasks,
    task: runtimeApi.getWorldTask,
    archive: runtimeApi.archiveWorld,
    restore: runtimeApi.restoreWorld,
    backup: runtimeApi.backupWorld,
    clone: runtimeApi.cloneWorld,
    export: runtimeApi.exportWorld,
    delete: runtimeApi.deleteWorld,
    pickImport: runtimeApi.pickWorldImport,
    uploadImport: runtimeApi.uploadWorldImport,
    import: runtimeApi.importWorld
  }
};
