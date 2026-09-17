<script lang="ts">
  import ServerConsole from '../components/ServerConsole.svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import { RuntimeError } from '../app/bridge/runtimeApi';
  import type { AdoptionPlan, ServerRuntimeSummary, WorkspaceDuplicateEstimate, WorkspaceEntry } from '../app/bridge/runtimeApi';
  import { canMutateLibraryServer, canOpenRuntimeConsole, canStopLibraryRuntime, stopLibraryRuntime } from '../app/serverLibraryRuntimeActions';

  type ManagementMode = 'locate' | 'duplicate' | 'remove' | 'delete-review' | 'delete-confirm' | null;

  let {
    recent,
    runtimes,
    error = '',
    onOpenServer,
    onChanged
  }: {
    recent: WorkspaceEntry[];
    runtimes: ServerRuntimeSummary[];
    error?: string;
    onOpenServer: (server: WorkspaceEntry) => Promise<void> | void;
    onChanged: () => Promise<void> | void;
  } = $props();

  let librarySearch = '';
  let menuServerId: string | null = null;
  let libraryConsoleWorkspaceId: string | null = null;
  let libraryConsoleServerName = 'Server';
  let libraryRuntimeBusyId: string | null = null;
  let stopCandidate: WorkspaceEntry | null = null;
  let stopCandidateDetached = false;
  let surfaceError = '';
  let creating = false;
  let createOpen = false;
  let createName = '';
  let createParent = '';
  let adopting = false;
  let adoptionPlan: AdoptionPlan | null = null;
  let managementServer: WorkspaceEntry | null = null;
  let managementMode: ManagementMode = null;
  let managementBusy = false;
  let managementError = '';
  let locateCandidate = '';
  let duplicateName = '';
  let duplicateParent = '';
  let duplicateEstimate: WorkspaceDuplicateEstimate | null = null;
  let estimateLoading = false;
  let deleteTypedName = '';

  function friendlyError(value: unknown) {
    if (value instanceof RuntimeError && value.code === 'SERVER_BUSY') return 'Stop this server before changing, duplicating, removing, or deleting it.';
    if (value instanceof Error && value.message.trim()) return value.message.trim();
    const message = String(value ?? '').replace(/^Error:\s*/i, '').trim();
    return message || 'Something went wrong. Try again.';
  }

  function visibleServers() {
    const query = librarySearch.trim().toLowerCase();
    return query ? recent.filter((server) => server.name.toLowerCase().includes(query)) : recent;
  }

  function runtimeFor(workspaceId: string) {
    return runtimes.find((runtime) => runtime.workspaceId === workspaceId) ?? null;
  }

  function formatBytes(bytes?: number | null) {
    if (bytes == null || !Number.isFinite(bytes)) return 'Unknown';
    if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
    return `${Math.ceil(bytes / 1024 ** 2)} MB`;
  }

  function runtimeLabel(runtime: ServerRuntimeSummary | null) {
    if (!runtime) return '';
    if (runtime.state === 'Online') return 'Running';
    if (runtime.state === 'Detached') return 'Running externally';
    return runtime.state;
  }

  function runtimeMeta(runtime: ServerRuntimeSummary | null) {
    if (!runtime) return '';
    const parts = [runtimeLabel(runtime)];
    if (runtime.paperPort) parts.push(`:${runtime.paperPort}`);
    if (runtime.usedMemoryBytes > 0) parts.push(formatBytes(runtime.usedMemoryBytes));
    return parts.filter(Boolean).join(' · ');
  }

  function runtimeTone(runtime: ServerRuntimeSummary | null) {
    if (!runtime) return '';
    if (runtime.state === 'Online') return 'running';
    if (runtime.state === 'Detached') return 'detached';
    if (runtime.state === 'Starting' || runtime.state === 'Stopping') return 'transition';
    return '';
  }

  function displayLocation(path: string) {
    if (!path) return '';
    const normalized = path.replace(/[\\/]+$/, '');
    return normalized.split(/[\\/]/).pop() || normalized;
  }

  function parentLocation(path: string) {
    const normalized = path.replace(/[\\/]+$/, '');
    const index = Math.max(normalized.lastIndexOf('\\'), normalized.lastIndexOf('/'));
    return index > 0 ? normalized.slice(0, index) : '';
  }

  function formatLastOpened(seconds: number) {
    if (!seconds) return 'Not opened yet';
    const then = new Date(seconds * 1000);
    const now = new Date();
    const day = 86400000;
    const diff = now.getTime() - then.getTime();
    if (diff < day && now.getDate() === then.getDate()) return 'Opened today';
    if (diff < day * 2) return 'Opened yesterday';
    return `Opened ${then.toLocaleDateString()}`;
  }

  async function chooseCreateLocation() {
    surfaceError = '';
    try {
      const selected = await runtimeProduct.workspace.pickParent();
      if (selected) createParent = selected;
    } catch (value) { surfaceError = friendlyError(value); }
  }

  async function createServer() {
    if (!createName.trim() || !createParent.trim()) return;
    creating = true;
    surfaceError = '';
    try {
      await runtimeProduct.workspace.create(createParent, createName.trim());
      createOpen = false;
      createName = '';
      createParent = '';
      await onChanged();
    } catch (value) { surfaceError = friendlyError(value); }
    finally { creating = false; }
  }

  async function analyzeAdoption() {
    surfaceError = '';
    try { adoptionPlan = await runtimeProduct.workspace.pickAdoption(); }
    catch (value) {
      surfaceError = friendlyError(value);
      adoptionPlan = null;
    }
  }

  async function adoptServer() {
    if (!adoptionPlan) return;
    adopting = true;
    surfaceError = '';
    try {
      await runtimeProduct.workspace.adopt(adoptionPlan.root, adoptionPlan.name);
      adoptionPlan = null;
      await onChanged();
    } catch (value) { surfaceError = friendlyError(value); }
    finally { adopting = false; }
  }

  function beginLocate(server: WorkspaceEntry, initialError = '') {
    menuServerId = null;
    managementServer = server;
    managementMode = 'locate';
    managementError = initialError;
    locateCandidate = '';
  }

  async function openServer(server: WorkspaceEntry) {
    menuServerId = null;
    surfaceError = '';
    try { await onOpenServer(server); }
    catch (value) {
      const message = friendlyError(value);
      if (message.toLowerCase().includes('currently unavailable')) beginLocate(server, message);
      else surfaceError = message;
    }
  }

  function openLibraryConsole(server: WorkspaceEntry, runtime: ServerRuntimeSummary | null) {
    if (!canOpenRuntimeConsole(runtime)) return;
    menuServerId = null;
    libraryConsoleWorkspaceId = server.id;
    libraryConsoleServerName = server.name;
  }

  function closeLibraryConsole() {
    libraryConsoleWorkspaceId = null;
    void onChanged();
  }

  function requestStopRuntime(server: WorkspaceEntry, runtime: ServerRuntimeSummary | null) {
    if (!canStopLibraryRuntime(runtime) || libraryRuntimeBusyId) return;
    menuServerId = null;
    stopCandidate = server;
    stopCandidateDetached = runtime?.state === 'Detached';
  }

  async function confirmStopRuntime() {
    if (!stopCandidate || libraryRuntimeBusyId) return;
    const server = stopCandidate;
    libraryRuntimeBusyId = server.id;
    surfaceError = '';
    try {
      await stopLibraryRuntime(server.id);
      stopCandidate = null;
      stopCandidateDetached = false;
      await onChanged();
    } catch (value) { surfaceError = friendlyError(value); }
    finally { libraryRuntimeBusyId = null; }
  }

  function cancelStopRuntime() {
    if (libraryRuntimeBusyId) return;
    stopCandidate = null;
    stopCandidateDetached = false;
  }

  async function openServerFolder(server: WorkspaceEntry) {
    menuServerId = null;
    managementError = '';
    try { await runtimeProduct.workspace.openFolder(server.id); }
    catch (value) {
      const message = friendlyError(value);
      if (message.toLowerCase().includes('unavailable')) beginLocate(server, message);
      else surfaceError = message;
    }
  }

  async function chooseLocateCandidate() {
    if (!managementServer || managementBusy) return;
    managementError = '';
    try {
      const selected = await runtimeProduct.workspace.pickLocation(managementServer.id);
      if (selected) locateCandidate = selected;
    } catch (value) { managementError = friendlyError(value); }
  }

  async function reconnectServerLocation() {
    if (!managementServer || !locateCandidate || managementBusy) return;
    managementBusy = true;
    managementError = '';
    try {
      const relocated = await runtimeProduct.workspace.reconnectLocation(managementServer.id, locateCandidate);
      closeManagementAfterSuccess();
      await onChanged();
      surfaceError = `${relocated.name} was reconnected to ${relocated.path}.`;
    } catch (value) { managementError = friendlyError(value); }
    finally { managementBusy = false; }
  }

  async function refreshDuplicateEstimate() {
    duplicateEstimate = null;
    if (!managementServer || !duplicateParent.trim()) return;
    estimateLoading = true;
    managementError = '';
    try { duplicateEstimate = await runtimeProduct.workspace.duplicateEstimate(managementServer.id, duplicateParent); }
    catch (value) { managementError = friendlyError(value); }
    finally { estimateLoading = false; }
  }

  function beginDuplicate(server: WorkspaceEntry) {
    menuServerId = null;
    managementServer = server;
    managementMode = 'duplicate';
    managementError = '';
    duplicateName = `${server.name} Copy`;
    duplicateParent = parentLocation(server.path);
    duplicateEstimate = null;
    void refreshDuplicateEstimate();
  }

  async function chooseDuplicateLocation() {
    try {
      const selected = await runtimeProduct.workspace.pickParent();
      if (selected) {
        duplicateParent = selected;
        await refreshDuplicateEstimate();
      }
    } catch (value) { managementError = friendlyError(value); }
  }

  async function duplicateServer() {
    if (!managementServer || !duplicateName.trim() || !duplicateParent.trim()) return;
    managementBusy = true;
    managementError = '';
    try {
      await runtimeProduct.workspace.duplicate(managementServer.id, duplicateParent, duplicateName.trim());
      closeManagementAfterSuccess();
      await onChanged();
    } catch (value) { managementError = friendlyError(value); }
    finally { managementBusy = false; }
  }

  function beginRemove(server: WorkspaceEntry) {
    menuServerId = null;
    managementServer = server;
    managementMode = 'remove';
    managementError = '';
  }

  async function removeServerFromLibrary() {
    if (!managementServer) return;
    managementBusy = true;
    managementError = '';
    try {
      await runtimeProduct.workspace.removeFromLibrary(managementServer.id);
      closeManagementAfterSuccess();
      await onChanged();
    } catch (value) { managementError = friendlyError(value); }
    finally { managementBusy = false; }
  }

  function beginDelete(server: WorkspaceEntry) {
    menuServerId = null;
    managementServer = server;
    managementMode = 'delete-review';
    managementError = '';
    deleteTypedName = '';
  }

  async function deleteServer() {
    if (!managementServer || deleteTypedName !== managementServer.name) return;
    managementBusy = true;
    managementError = '';
    try {
      await runtimeProduct.workspace.delete(managementServer.id, deleteTypedName);
      closeManagementAfterSuccess();
      await onChanged();
    } catch (value) { managementError = friendlyError(value); }
    finally { managementBusy = false; }
  }

  function closeManagement() {
    if (managementBusy) return;
    closeManagementAfterSuccess();
  }

  function closeManagementAfterSuccess() {
    managementServer = null;
    managementMode = null;
    managementError = '';
    locateCandidate = '';
    duplicateEstimate = null;
    deleteTypedName = '';
  }
</script>

<header class="page-toolbar">
  <div><h1>Servers</h1><p>Open, reconnect, duplicate, remove, or delete servers from one place.</p></div>
  <div class="top-actions"><button class="secondary-button" onclick={() => void analyzeAdoption()}>Add existing</button><button class="primary-button" onclick={() => (createOpen = true)}>Create server</button></div>
</header>
<main class="library-content">
  {#if error || surfaceError}<div class="error-box" role="status">{surfaceError || error}</div>{/if}
  {#if recent.length > 0}
    {#if recent.length > 4}<div class="library-toolbar"><label class="search-field" aria-label="Search servers"><span aria-hidden="true">⌕</span><input bind:value={librarySearch} placeholder="Search servers" /></label></div>{/if}
    {#if visibleServers().length > 0}
      <div class="server-grid">
        {#each visibleServers() as server}
          {@const runtime = runtimeFor(server.id)}
          <div class="server-tile">
            <button class="server-open" onclick={() => void openServer(server)}><div class="server-icon">{server.name.slice(0,1).toUpperCase()}</div><div class="server-tile-copy"><strong>{server.name}</strong>{#if runtime && runtimeTone(runtime)}<span class={`runtime-meta ${runtimeTone(runtime)}`}><i aria-hidden="true"></i>{runtimeMeta(runtime)}</span>{:else}<span title={server.path}>{formatLastOpened(server.lastOpenedUnixSeconds)}</span>{/if}</div><span class="open-chevron">›</span></button>
            <div class="server-menu-wrap"><button class="server-menu-button" aria-label={`Manage ${server.name}`} aria-expanded={menuServerId === server.id} onclick={() => (menuServerId = menuServerId === server.id ? null : server.id)}>•••</button>
              {#if menuServerId === server.id}<div class="server-menu" role="menu"><button onclick={() => void openServer(server)}>Open</button>{#if canOpenRuntimeConsole(runtime)}<button onclick={() => openLibraryConsole(server, runtime)}>Open console</button><button disabled={libraryRuntimeBusyId === server.id} onclick={() => requestStopRuntime(server, runtime)}>{libraryRuntimeBusyId === server.id ? 'Stopping…' : 'Stop server'}</button><div class="menu-divider"></div>{:else if runtime?.state === 'Detached'}<button class="danger-menu-item" disabled={libraryRuntimeBusyId === server.id} onclick={() => requestStopRuntime(server, runtime)}>{libraryRuntimeBusyId === server.id ? 'Stopping…' : 'Stop external server'}</button><div class="menu-divider"></div>{/if}<button onclick={() => void openServerFolder(server)}>Open folder</button><button disabled={!canMutateLibraryServer(runtime)} onclick={() => beginLocate(server)}>Locate moved server…</button><div class="menu-divider"></div><button disabled={!canMutateLibraryServer(runtime)} onclick={() => beginDuplicate(server)}>Duplicate server</button><div class="menu-divider"></div><button disabled={!canMutateLibraryServer(runtime)} onclick={() => beginRemove(server)}>Remove from library</button><button class="danger-menu-item" disabled={!canMutateLibraryServer(runtime)} onclick={() => beginDelete(server)}>Delete server…</button></div>{/if}
            </div>
          </div>
        {/each}
      </div>
    {:else}<section class="search-empty"><strong>No servers found</strong><span>Try a different server name.</span><button onclick={() => (librarySearch = '')}>Clear search</button></section>{/if}
  {:else}<section class="empty-library"><div class="empty-icon">L</div><h2>Start with a server</h2><p>Create a new build server, or add one you already use.</p><div class="empty-actions"><button class="primary-button" onclick={() => (createOpen = true)}>Create server</button><button class="secondary-button" onclick={() => void analyzeAdoption()}>Add existing</button></div></section>{/if}
</main>

{#if stopCandidate}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && cancelStopRuntime()}><div class="dialog" role="dialog" aria-modal="true" aria-labelledby="stop-server-heading"><div class="dialog-heading"><div><h2 id="stop-server-heading">{stopCandidateDetached ? 'Stop external server?' : `Stop ${stopCandidate.name}?`}</h2><p>{stopCandidateDetached ? 'LazyBuilder will stop the verified Paper process that is still running from an earlier launcher session.' : 'LazyBuilder will ask Paper to stop cleanly before releasing this server runtime.'}</p></div><button class="icon-button" disabled={libraryRuntimeBusyId === stopCandidate.id} onclick={cancelStopRuntime}>×</button></div><div class="safe-notice"><strong>{stopCandidateDetached ? 'External process ownership verified' : 'Graceful shutdown'}</strong><span>{stopCandidate.name}</span><p>{stopCandidateDetached ? 'This does not delete or modify server files.' : 'Server files remain unchanged. Wait for the stop to finish before changing runtime files.'}</p></div><div class="dialog-actions"><button class="ghost-button" disabled={libraryRuntimeBusyId === stopCandidate.id} onclick={cancelStopRuntime}>Cancel</button><button class={stopCandidateDetached ? 'danger-button' : 'secondary-button'} disabled={libraryRuntimeBusyId === stopCandidate.id} onclick={confirmStopRuntime}>{libraryRuntimeBusyId === stopCandidate.id ? 'Stopping…' : stopCandidateDetached ? 'Stop external server' : 'Stop server'}</button></div></div></div>{/if}

{#if createOpen}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !creating && (createOpen = false)}><div class="dialog" role="dialog" aria-modal="true"><div class="dialog-heading"><div><h2>Create server</h2><p>Set a name and choose where LazyBuilder should keep it.</p></div><button class="icon-button" disabled={creating} onclick={() => (createOpen = false)}>×</button></div><label>Server name<input bind:value={createName} placeholder="Build Server" disabled={creating} /></label><label>Save in<div class="location-row"><input value={displayLocation(createParent)} title={createParent} readonly placeholder="Choose a folder" /><button class="secondary-button" disabled={creating} onclick={chooseCreateLocation}>Browse</button></div></label><div class="dialog-actions"><button class="ghost-button" disabled={creating} onclick={() => (createOpen = false)}>Cancel</button><button class="primary-button" disabled={!createName.trim() || !createParent.trim() || creating} onclick={createServer}>{creating ? 'Creating…' : 'Create server'}</button></div></div></div>{/if}

{#if adoptionPlan}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !adopting && (adoptionPlan = null)}><div class="dialog adoption-dialog" role="dialog" aria-modal="true"><div class="dialog-heading"><div><h2>Add {adoptionPlan.name}</h2><p>Review what LazyBuilder found before adding this server.</p></div><button class="icon-button" disabled={adopting} onclick={() => (adoptionPlan = null)}>×</button></div><div class="detected-grid"><div><strong>{adoptionPlan.worlds.length}</strong><span>Worlds</span></div><div><strong>{adoptionPlan.serverEntries.length}</strong><span>Server files</span></div><div><strong>{adoptionPlan.legacyPluginsToDisable.length}</strong><span>Legacy plugins</span></div></div>{#if adoptionPlan.warnings.length > 0}<div class="warning-box"><strong>Needs your attention</strong>{#each adoptionPlan.warnings as warning}<p>{warning}</p>{/each}</div>{/if}<details><summary>Technical migration details</summary><div class="details-list"><p><strong>Location:</strong> {adoptionPlan.root}</p><p><strong>Paper:</strong> {adoptionPlan.paperJar}</p></div></details><div class="dialog-actions"><button class="ghost-button" disabled={adopting} onclick={() => (adoptionPlan = null)}>Cancel</button><button class="primary-button" disabled={adopting} onclick={adoptServer}>{adopting ? 'Adding…' : 'Add server'}</button></div></div></div>{/if}

{#if managementServer && managementMode === 'locate'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !managementBusy && closeManagement()}><div class="dialog" role="dialog" aria-modal="true" aria-labelledby="locate-server-heading"><div class="dialog-heading"><div><h2 id="locate-server-heading">Locate {managementServer.name}</h2><p>Reconnect this server after its folder or drive has moved.</p></div><button class="icon-button" disabled={managementBusy} onclick={closeManagement}>×</button></div>{#if managementError}<div class="error-box" role="alert">{managementError}</div>{/if}<div class="safe-notice"><strong>Previously registered location</strong><span>{managementServer.path}</span><p>LazyBuilder will only reconnect if the selected folder belongs to this same server.</p></div><label>New server folder<div class="location-row"><input value={locateCandidate} title={locateCandidate} readonly placeholder="Choose the moved server folder" /><button class="secondary-button" disabled={managementBusy} onclick={chooseLocateCandidate}>Browse</button></div></label><div class="identity-notice"><strong>Server identity protected</strong><span>Other server folders and unsupported linked locations are rejected.</span></div><div class="dialog-actions"><button class="ghost-button" disabled={managementBusy} onclick={closeManagement}>Cancel</button><button class="primary-button" disabled={managementBusy || !locateCandidate} onclick={reconnectServerLocation}>{managementBusy ? 'Reconnecting…' : 'Reconnect server'}</button></div></div></div>{/if}

{#if managementServer && managementMode === 'duplicate'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !managementBusy && closeManagement()}><div class="dialog" role="dialog" aria-modal="true"><div class="dialog-heading"><div><h2>Duplicate server</h2><p>Create a complete, independently usable copy of {managementServer.name}.</p></div><button class="icon-button" disabled={managementBusy} onclick={closeManagement}>×</button></div>{#if managementError}<div class="error-box" role="alert">{managementError}</div>{/if}<label>New server name<input bind:value={duplicateName} disabled={managementBusy} /></label><label>Save in<div class="location-row"><input value={displayLocation(duplicateParent)} title={duplicateParent} readonly /><button class="secondary-button" disabled={managementBusy} onclick={chooseDuplicateLocation}>Browse</button></div></label><div class="included-box"><strong>Included in the copy</strong><span>Worlds, server configuration, plugins, plugin data, and LazyBuilder server settings.</span><small>Temporary files, cache, logs, and active runtime files are not copied.</small></div><div class="storage-row"><div><span>Server data</span><strong>{estimateLoading ? 'Calculating…' : formatBytes(duplicateEstimate?.sourceBytes)}</strong></div><div><span>Space needed</span><strong>{estimateLoading ? 'Calculating…' : formatBytes(duplicateEstimate?.requiredBytes)}</strong></div><div><span>Available</span><strong>{estimateLoading ? 'Calculating…' : formatBytes(duplicateEstimate?.availableBytes)}</strong></div></div><div class="dialog-actions"><button class="ghost-button" disabled={managementBusy} onclick={closeManagement}>Cancel</button><button class="primary-button" disabled={managementBusy || estimateLoading || !!managementError || !duplicateName.trim() || !duplicateParent.trim()} onclick={duplicateServer}>{managementBusy ? 'Duplicating…' : 'Duplicate server'}</button></div></div></div>{/if}

{#if managementServer && managementMode === 'remove'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !managementBusy && closeManagement()}><div class="dialog" role="dialog" aria-modal="true"><div class="dialog-heading"><div><h2>Remove {managementServer.name} from LazyBuilder?</h2><p>This only removes the server from your LazyBuilder library.</p></div><button class="icon-button" disabled={managementBusy} onclick={closeManagement}>×</button></div>{#if managementError}<div class="error-box" role="alert">{managementError}</div>{/if}<div class="safe-notice"><strong>Your files will remain on this computer.</strong><span>{managementServer.path}</span><p>You can add the server again later.</p></div><div class="dialog-actions"><button class="ghost-button" disabled={managementBusy} onclick={closeManagement}>Cancel</button><button class="secondary-button" disabled={managementBusy} onclick={removeServerFromLibrary}>{managementBusy ? 'Removing…' : 'Remove from library'}</button></div></div></div>{/if}

{#if managementServer && managementMode === 'delete-review'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && closeManagement()}><div class="dialog danger-dialog" role="dialog" aria-modal="true"><div class="dialog-heading"><div><h2>Delete {managementServer.name}?</h2><p>Review exactly what will be permanently removed.</p></div><button class="icon-button" onclick={closeManagement}>×</button></div>{#if managementError}<div class="error-box" role="alert">{managementError}</div>{/if}<div class="danger-summary"><strong>This permanently deletes:</strong><ul><li>Worlds</li><li>Server configuration</li><li>Plugins and plugin data</li><li>LazyBuilder server metadata stored inside this server folder</li></ul><span class="path-copy">{managementServer.path}</span><p>The server folder is removed from disk. This cannot be undone.</p></div><div class="dialog-actions"><button class="ghost-button" onclick={closeManagement}>Cancel</button><button class="danger-button" onclick={() => (managementMode = 'delete-confirm')}>Continue</button></div></div></div>{/if}

{#if managementServer && managementMode === 'delete-confirm'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !managementBusy && closeManagement()}><div class="dialog danger-dialog" role="dialog" aria-modal="true"><div class="dialog-heading"><div><h2>Confirm permanent deletion</h2><p>Type the server name exactly to confirm deleting its folder and all server data.</p></div><button class="icon-button" disabled={managementBusy} onclick={closeManagement}>×</button></div>{#if managementError}<div class="error-box" role="alert">{managementError}</div>{/if}<div class="typed-confirmation"><code>{managementServer.name}</code><label>Server name<input bind:value={deleteTypedName} autocomplete="off" disabled={managementBusy} /></label></div><div class="dialog-actions"><button class="ghost-button" disabled={managementBusy} onclick={() => (managementMode = 'delete-review')}>Back</button><button class="danger-button" disabled={managementBusy || deleteTypedName !== managementServer.name} onclick={deleteServer}>{managementBusy ? 'Deleting…' : 'Delete permanently'}</button></div></div></div>{/if}

<ServerConsole open={libraryConsoleWorkspaceId !== null} workspaceId={libraryConsoleWorkspaceId ?? undefined} serverName={libraryConsoleServerName} onClose={closeLibraryConsole} />

<style>
  .page-toolbar{min-height:82px;display:flex;align-items:center;justify-content:space-between;gap:20px;padding:15px 30px;border-bottom:1px solid var(--border-soft);background:#111315}.page-toolbar h1{margin:0;font-size:23px}.page-toolbar p{margin:4px 0 0;color:var(--muted);font-size:12px}.top-actions,.empty-actions{display:flex;gap:8px}.library-content{width:min(1040px,calc(100% - 56px));margin:0 auto;padding:26px 0 48px;overflow:auto;min-height:0;flex:1}.server-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.server-tile{position:relative;min-height:78px;display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:stretch;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}.server-open{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:12px;padding:13px;border:0;background:transparent;color:var(--text);text-align:left;cursor:pointer}.server-open:hover{background:var(--surface-2)}.server-icon{width:44px;height:44px;display:grid;place-items:center;border-radius:10px;background:#252a2e;border:1px solid #3a4147;font-weight:800}.server-tile-copy{display:grid;gap:3px;min-width:0}.server-tile-copy strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.server-tile-copy span{color:var(--muted);font-size:10px}.runtime-meta{display:flex!important;align-items:center;gap:6px}.runtime-meta i{width:6px;height:6px;border-radius:50%;background:var(--muted-2)}.runtime-meta.running{color:#9ee8b9}.runtime-meta.running i{background:var(--accent)}.runtime-meta.transition{color:var(--info)}.runtime-meta.transition i{background:var(--info)}.runtime-meta.detached{color:var(--warning)}.runtime-meta.detached i{background:var(--warning)}.open-chevron{color:var(--muted-2);font-size:22px}.server-menu-wrap{position:relative;display:flex;align-items:center;padding-right:8px}.server-menu-button{width:34px;height:34px;border:0;border-radius:8px;background:transparent;color:var(--muted);cursor:pointer;letter-spacing:1px}.server-menu-button:hover{background:var(--surface-3);color:var(--text)}.server-menu{position:absolute;z-index:12;right:7px;top:58px;width:190px;display:grid;padding:6px;border:1px solid var(--border);border-radius:9px;background:#171a1d;box-shadow:0 12px 34px rgba(0,0,0,.38)}.server-menu button{padding:9px 10px;border:0;border-radius:6px;background:transparent;color:var(--text-soft);text-align:left;cursor:pointer}.server-menu button:hover{background:var(--surface-2);color:var(--text)}.server-menu .danger-menu-item{color:#ff9aa2}.menu-divider{height:1px;margin:4px 2px;background:var(--border-soft)}.empty-library,.search-empty{min-height:300px;display:grid;place-content:center;justify-items:center;text-align:center;border:1px dashed var(--border);border-radius:12px;background:var(--bg-elevated)}.empty-icon{width:50px;height:50px;display:grid;place-items:center;border-radius:13px;background:var(--surface-2);margin-bottom:13px}.empty-library h2{margin:0}.empty-library p{color:var(--muted);font-size:12px}.search-field{display:flex;gap:8px;margin-bottom:12px;padding:9px 11px;border:1px solid var(--border-soft);border-radius:8px;background:var(--surface)}.search-field input{flex:1;border:0;background:transparent;color:var(--text)}
  .primary-button,.secondary-button,.ghost-button,.icon-button,.danger-button{min-height:36px;border-radius:8px;padding:8px 13px;font-weight:650;cursor:pointer}.primary-button{border:1px solid var(--accent);background:var(--accent);color:var(--accent-ink)}.secondary-button{border:1px solid var(--border);background:var(--surface-2);color:var(--text)}.ghost-button,.icon-button{border:0;background:transparent;color:var(--text-soft)}.icon-button{font-size:20px}.danger-button{border:1px solid #8e3840;background:#7a2d34;color:#fff}button:disabled{opacity:.5;cursor:default}.error-box{margin-bottom:12px;padding:11px 13px;border:1px solid #70343a;border-radius:8px;background:var(--danger-bg);color:#ffd9dc;font-size:12px}
  .modal-backdrop{position:fixed;inset:0;z-index:20;display:grid;place-items:center;padding:24px;background:rgba(4,6,8,.72)}.dialog{width:min(500px,100%);display:grid;gap:17px;padding:21px;border:1px solid var(--border);border-radius:14px;background:var(--surface)}.adoption-dialog{width:min(580px,100%)}.danger-dialog{border-color:#6d3036}.dialog-heading{display:flex;justify-content:space-between;gap:18px}.dialog-heading h2{margin:0}.dialog-heading p{margin:5px 0 0;color:var(--muted);font-size:12px}label{display:grid;gap:7px;font-size:12px}.location-row{display:grid;grid-template-columns:1fr auto;gap:8px}label>input,.location-row input{min-height:36px;padding:8px 11px;border:1px solid var(--border-soft);border-radius:8px;background:var(--surface-2);color:var(--text)}.dialog-actions{display:flex;justify-content:flex-end;gap:8px}.detected-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.detected-grid>div{display:grid;padding:13px;border:1px solid var(--border-soft);border-radius:8px}.warning-box{padding:12px;border:1px solid #5f5125;border-radius:8px;background:var(--warning-bg)}.warning-box p{margin:5px 0 0;font-size:11px}.details-list{margin-top:8px;padding:10px;background:var(--bg-elevated)}.included-box,.safe-notice,.identity-notice,.danger-summary,.typed-confirmation{display:grid;gap:8px;padding:13px;border:1px solid var(--border-soft);border-radius:9px;background:var(--bg-elevated)}.included-box span,.safe-notice span,.identity-notice span,.danger-summary .path-copy{color:var(--text-soft);font-size:11px;word-break:break-all}.included-box small,.safe-notice p,.danger-summary p{margin:0;color:var(--muted);font-size:10px}.identity-notice{border-color:var(--accent-border);background:var(--accent-soft)}.identity-notice span{word-break:normal}.storage-row{display:grid;grid-template-columns:repeat(3,1fr);gap:7px}.storage-row>div{display:grid;gap:4px;padding:10px;border:1px solid var(--border-soft);border-radius:8px}.storage-row span{color:var(--muted);font-size:9px}.storage-row strong{font-size:12px}.danger-summary ul{margin:2px 0 2px 18px;padding:0;color:var(--text-soft);font-size:11px}.typed-confirmation code{width:max-content;padding:5px 8px;border-radius:5px;background:var(--surface-3);color:#ffb7bd}
  @media(max-width:760px){.page-toolbar{padding:15px 18px}.library-content{width:calc(100% - 28px)}.server-grid,.storage-row,.detected-grid{grid-template-columns:1fr}}
</style>
