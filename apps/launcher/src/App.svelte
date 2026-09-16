<script lang="ts">
  import { onMount } from 'svelte';
  import Dashboard from './pages/Dashboard.svelte';
  import Worlds from './pages/Worlds.svelte';
  import Plugins from './pages/Plugins.svelte';
  import Settings from './pages/Settings.svelte';
  import LauncherSettingsPage from './pages/LauncherSettings.svelte';
  import Client from './pages/Client.svelte';
  import Activity from './pages/Activity.svelte';
  import { installLauncherCloseGuard } from './app/closeGuard';
  import { runtimeProduct } from './app/bridge/runtimeProductFacade';
  import { RuntimeError } from './app/bridge/runtimeApi';
  import type { AdoptionPlan, DiagnosticSummary, RuntimeUpdateStatus, ServerRuntimeSummary, WorkspaceDuplicateEstimate, WorkspaceEntry, WorkspaceProvisioningStatus, WorkspaceState } from './app/bridge/runtimeApi';

  type Page = 'Overview' | 'Worlds' | 'Plugins' | 'Settings';
  type GlobalPage = 'Servers' | 'Activity' | 'Client' | 'Settings';
  type LauncherMode = 'home' | 'create';
  type ManagementMode = 'active-actions' | 'locate' | 'duplicate' | 'remove' | 'delete-review' | 'delete-confirm' | null;

  let page: Page = 'Overview';
  let globalPage: GlobalPage = 'Servers';
  let launcherMode: LauncherMode = 'home';
  let workspaceState: WorkspaceState = { active: null, recent: [] };
  let serverRuntimes: ServerRuntimeSummary[] = [];
  let provisioning: WorkspaceProvisioningStatus | null = null;
  let runtimeUpdates: RuntimeUpdateStatus | null = null;
  let diagnostics: DiagnosticSummary | null = null;
  let adoptionPlan: AdoptionPlan | null = null;
  let loadingWorkspace = true;
  let workspaceError = '';
  let librarySearch = '';
  let createName = '';
  let createParent = '';
  let creating = false;
  let adopting = false;
  let provisioningServer = false;
  let acceptingEula = false;
  let updatingPaper = false;

  let menuServerId: string | null = null;
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
  let closeGuardUnlisten: (() => void) | null = null;

  const pages: Page[] = ['Overview', 'Worlds', 'Plugins', 'Settings'];

  function friendlyError(error: unknown) {
    if (error instanceof RuntimeError && error.code === 'SERVER_BUSY') return 'Stop this server before changing, duplicating, removing, or deleting it.';
    if (error instanceof Error && error.message.trim()) return error.message.trim();
    const message = String(error ?? '').replace(/^Error:\s*/i, '').trim();
    return message || 'Something went wrong. Try again.';
  }

  function visibleServers() {
    const query = librarySearch.trim().toLowerCase();
    return query ? workspaceState.recent.filter((server) => server.name.toLowerCase().includes(query)) : workspaceState.recent;
  }

  function runtimeFor(workspaceId: string) {
    return serverRuntimes.find((runtime) => runtime.workspaceId === workspaceId) ?? null;
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

  function formatBytes(bytes?: number | null) {
    if (bytes == null || !Number.isFinite(bytes)) return 'Unknown';
    if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
    return `${Math.ceil(bytes / 1024 ** 2)} MB`;
  }

  function setupProgress() {
    if (!provisioning) return 0;
    const steps = [provisioning.workspaceCreated, provisioning.javaReady, provisioning.paperReady, provisioning.coreModulesReady, provisioning.configReady, provisioning.eulaAccepted];
    return Math.round((steps.filter(Boolean).length / steps.length) * 100);
  }

  function setupHeadline() {
    if (!provisioning) return 'Preparing server';
    if (!provisioning.javaReady || !provisioning.paperReady || !provisioning.coreModulesReady || !provisioning.configReady) return 'Finish preparing this server';
    if (!provisioning.eulaAccepted) return 'Accept the Minecraft EULA';
    return 'Server setup complete';
  }

  function platformLabel() {
    const platform = diagnostics?.serverPlatform;
    const minecraft = diagnostics?.minecraftVersion;
    if (!platform && !minecraft) return 'Server';
    const platformName = platform ? platform.charAt(0).toUpperCase() + platform.slice(1) : 'Server';
    return minecraft ? `${platformName} ${minecraft}` : platformName;
  }

  async function loadRuntimeUpdates() { try { runtimeUpdates = await runtimeProduct.workspace.runtimeUpdateStatus(); } catch { runtimeUpdates = null; } }
  async function loadDiagnostics() { try { diagnostics = await runtimeProduct.diagnostics.summary(); } catch { diagnostics = null; } }
  async function loadServerRuntimes() { try { serverRuntimes = await runtimeProduct.server.runtimes(); } catch { serverRuntimes = []; } }

  async function refreshWorkspaceState() {
    loadingWorkspace = true;
    workspaceError = '';
    runtimeUpdates = null;
    diagnostics = null;
    try {
      const [nextWorkspaceState, nextRuntimes] = await Promise.all([
        runtimeProduct.workspace.state(),
        runtimeProduct.server.runtimes().catch(() => [] as ServerRuntimeSummary[])
      ]);
      workspaceState = nextWorkspaceState;
      serverRuntimes = nextRuntimes;
      provisioning = workspaceState.active ? await runtimeProduct.workspace.provisioningStatus() : null;
      if (workspaceState.active) {
        adoptionPlan = null;
        launcherMode = 'home';
        void loadDiagnostics();
        if (provisioning?.ready) void loadRuntimeUpdates();
      }
    } catch (error) {
      workspaceError = friendlyError(error);
      provisioning = null;
    } finally { loadingWorkspace = false; }
  }

  async function installCloseGuard() {
    try { closeGuardUnlisten = await installLauncherCloseGuard(); }
    catch { closeGuardUnlisten = null; }
  }

  async function chooseCreateLocation() {
    workspaceError = '';
    try { const selected = await runtimeProduct.workspace.pickParent(); if (selected) createParent = selected; }
    catch (error) { workspaceError = friendlyError(error); }
  }

  async function createServer() {
    if (!createName.trim() || !createParent.trim()) return;
    creating = true; workspaceError = '';
    try {
      await runtimeProduct.workspace.create(createParent, createName.trim());
      createName = ''; createParent = ''; page = 'Overview'; globalPage = 'Servers';
      await refreshWorkspaceState();
    } catch (error) { workspaceError = friendlyError(error); }
    finally { creating = false; }
  }

  async function analyzeAdoption() {
    workspaceError = '';
    try { adoptionPlan = await runtimeProduct.workspace.pickAdoption(); }
    catch (error) { workspaceError = friendlyError(error); adoptionPlan = null; }
  }

  async function adoptServer() {
    if (!adoptionPlan) return;
    adopting = true; workspaceError = '';
    try {
      await runtimeProduct.workspace.adopt(adoptionPlan.root, adoptionPlan.name);
      adoptionPlan = null; page = 'Overview'; globalPage = 'Servers';
      await refreshWorkspaceState();
    } catch (error) { workspaceError = friendlyError(error); }
    finally { adopting = false; }
  }

  function beginLocate(server: WorkspaceEntry, initialError = '') {
    menuServerId = null; managementServer = server; managementMode = 'locate'; managementError = initialError; locateCandidate = '';
  }

  async function activateServer(server: WorkspaceEntry) {
    menuServerId = null; workspaceError = '';
    try {
      await runtimeProduct.workspace.activate(server.id);
      page = 'Overview'; globalPage = 'Servers';
      await refreshWorkspaceState();
    } catch (error) {
      const message = friendlyError(error);
      if (message.toLowerCase().includes('currently unavailable')) beginLocate(server, message);
      else workspaceError = message;
    }
  }

  async function chooseLocateCandidate() {
    if (!managementServer || managementBusy) return;
    managementError = '';
    try { const selected = await runtimeProduct.workspace.pickLocation(managementServer.id); if (selected) locateCandidate = selected; }
    catch (error) { managementError = friendlyError(error); }
  }

  async function reconnectServerLocation() {
    if (!managementServer || !locateCandidate || managementBusy) return;
    managementBusy = true; managementError = '';
    try {
      const relocated = await runtimeProduct.workspace.reconnectLocation(managementServer.id, locateCandidate);
      closeManagementAfterSuccess();
      await refreshWorkspaceState();
      workspaceError = `${relocated.name} was reconnected to ${relocated.path}.`;
    } catch (error) { managementError = friendlyError(error); }
    finally { managementBusy = false; }
  }

  async function backToServers() {
    globalPage = 'Servers';
    if (!workspaceState.active) { await loadServerRuntimes(); return; }
    workspaceError = '';
    try { await runtimeProduct.workspace.close(); page = 'Overview'; await refreshWorkspaceState(); }
    catch (error) { workspaceError = friendlyError(error); }
  }

  async function prepareServer() {
    provisioningServer = true; workspaceError = '';
    try {
      const result = await runtimeProduct.workspace.provision();
      provisioning = result.status; runtimeUpdates = null; void loadDiagnostics();
      if (provisioning?.ready) void loadRuntimeUpdates();
    } catch (error) {
      workspaceError = friendlyError(error);
      try { provisioning = await runtimeProduct.workspace.provisioningStatus(); } catch {}
    } finally { provisioningServer = false; }
  }

  async function acceptEula() {
    acceptingEula = true; workspaceError = '';
    try { provisioning = await runtimeProduct.workspace.acceptEula(); if (provisioning.ready) void loadRuntimeUpdates(); }
    catch (error) { workspaceError = friendlyError(error); }
    finally { acceptingEula = false; }
  }

  async function updatePaper() {
    updatingPaper = true; workspaceError = '';
    try { runtimeUpdates = await runtimeProduct.workspace.updatePaper(); void loadDiagnostics(); }
    catch (error) { workspaceError = friendlyError(error); }
    finally { updatingPaper = false; }
  }

  function formatLastOpened(seconds: number) {
    if (!seconds) return 'Not opened yet';
    const then = new Date(seconds * 1000); const now = new Date(); const day = 86400000; const diff = now.getTime() - then.getTime();
    if (diff < day && now.getDate() === then.getDate()) return 'Opened today';
    if (diff < day * 2) return 'Opened yesterday';
    return `Opened ${then.toLocaleDateString()}`;
  }

  function closeManagement() {
    if (managementBusy) return;
    managementServer = null; managementMode = null; managementError = ''; locateCandidate = ''; duplicateEstimate = null; deleteTypedName = '';
  }

  function openActiveActions(server: WorkspaceEntry) { managementServer = server; managementMode = 'active-actions'; managementError = ''; }

  async function openServerFolder(server: WorkspaceEntry) {
    menuServerId = null; managementError = '';
    try { await runtimeProduct.workspace.openFolder(server.id); }
    catch (error) {
      const message = friendlyError(error);
      if (message.toLowerCase().includes('unavailable')) beginLocate(server, message); else workspaceError = message;
    }
  }

  async function refreshDuplicateEstimate() {
    duplicateEstimate = null;
    if (!managementServer || !duplicateParent.trim()) return;
    estimateLoading = true; managementError = '';
    try { duplicateEstimate = await runtimeProduct.workspace.duplicateEstimate(managementServer.id, duplicateParent); }
    catch (error) { managementError = friendlyError(error); }
    finally { estimateLoading = false; }
  }

  function beginDuplicate(server: WorkspaceEntry) {
    menuServerId = null; managementServer = server; managementMode = 'duplicate'; managementError = ''; duplicateName = `${server.name} Copy`; duplicateParent = parentLocation(server.path); duplicateEstimate = null; void refreshDuplicateEstimate();
  }

  async function chooseDuplicateLocation() {
    try { const selected = await runtimeProduct.workspace.pickParent(); if (selected) { duplicateParent = selected; await refreshDuplicateEstimate(); } }
    catch (error) { managementError = friendlyError(error); }
  }

  async function duplicateServer() {
    if (!managementServer || !duplicateName.trim() || !duplicateParent.trim()) return;
    managementBusy = true; managementError = '';
    try { await runtimeProduct.workspace.duplicate(managementServer.id, duplicateParent, duplicateName.trim()); closeManagementAfterSuccess(); await refreshWorkspaceState(); }
    catch (error) { managementError = friendlyError(error); }
    finally { managementBusy = false; }
  }

  function beginRemove(server: WorkspaceEntry) { menuServerId = null; managementServer = server; managementMode = 'remove'; managementError = ''; }
  async function removeServerFromLibrary() {
    if (!managementServer) return;
    managementBusy = true; managementError = '';
    try { await runtimeProduct.workspace.removeFromLibrary(managementServer.id); closeManagementAfterSuccess(); await refreshWorkspaceState(); }
    catch (error) { managementError = friendlyError(error); }
    finally { managementBusy = false; }
  }

  function beginDelete(server: WorkspaceEntry) { menuServerId = null; managementServer = server; managementMode = 'delete-review'; managementError = ''; deleteTypedName = ''; }
  async function deleteServer() {
    if (!managementServer || deleteTypedName !== managementServer.name) return;
    managementBusy = true; managementError = '';
    try { await runtimeProduct.workspace.delete(managementServer.id, deleteTypedName); closeManagementAfterSuccess(); await refreshWorkspaceState(); }
    catch (error) { managementError = friendlyError(error); }
    finally { managementBusy = false; }
  }

  function closeManagementAfterSuccess() { managementServer = null; managementMode = null; managementError = ''; locateCandidate = ''; duplicateEstimate = null; deleteTypedName = ''; }

  onMount(() => {
    void refreshWorkspaceState();
    void installCloseGuard();
    return () => { closeGuardUnlisten?.(); closeGuardUnlisten = null; };
  });
</script>

{#snippet navIcon(item: Page | GlobalPage)}
  {#if item === 'Servers'}<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5.5h16v5H4zM4 13.5h16v5H4z"/><path d="M7 8h.01M7 16h.01"/></svg>
  {:else if item === 'Activity'}<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 12h3l2-5 4 10 2-5h5"/></svg>
  {:else if item === 'Client'}<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="5" width="16" height="12" rx="2"/><path d="M9 20h6M12 17v3"/></svg>
  {:else if item === 'Overview'}<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 11 8-7 8 7"/><path d="M6.5 10v9h11v-9M10 19v-5h4v5"/></svg>
  {:else if item === 'Worlds'}<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8.5"/><path d="M3.8 12h16.4M12 3.5c2.3 2.4 3.4 5.2 3.4 8.5S14.3 18.1 12 20.5M12 3.5C9.7 5.9 8.6 8.7 8.6 12s1.1 6.1 3.4 8.5"/></svg>
  {:else if item === 'Plugins'}<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9.5 4.5v4h-4v7h4v4h7v-4h4v-7h-4v-4z"/></svg>
  {:else}<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M12 3.5v2M12 18.5v2M20.5 12h-2M5.5 12h-2M18 6l-1.4 1.4M7.4 16.6 6 18M18 18l-1.4-1.4M7.4 7.4 6 6"/></svg>{/if}
{/snippet}

{#if loadingWorkspace}
  <main class="loading-screen" aria-live="polite"><div class="brand-mark loading-brand">L</div><div class="loading-copy"><strong>LazyBuilder</strong><span>Opening your server library…</span></div></main>
{:else}
  <div class="desktop-shell">
    <aside class="navigation" aria-label="Launcher navigation">
      <div class="brand-lockup navigation-brand"><div class="brand-mark">L</div><strong>LazyBuilder</strong></div>
      <nav class="global-nav" aria-label="Launcher navigation">
        <button class:active={globalPage === 'Servers' && !workspaceState.active} onclick={backToServers}><span class="nav-icon">{@render navIcon('Servers')}</span><span>Servers</span></button>
        <button class:active={globalPage === 'Activity'} aria-current={globalPage === 'Activity' ? 'page' : undefined} onclick={() => (globalPage = 'Activity')}><span class="nav-icon">{@render navIcon('Activity')}</span><span>Activity</span></button>
        <button class:active={globalPage === 'Client'} aria-current={globalPage === 'Client' ? 'page' : undefined} onclick={() => (globalPage = 'Client')}><span class="nav-icon">{@render navIcon('Client')}</span><span>Client</span></button>
        <button class:active={globalPage === 'Settings'} aria-current={globalPage === 'Settings' ? 'page' : undefined} onclick={() => (globalPage = 'Settings')}><span class="nav-icon">{@render navIcon('Settings')}</span><span>Settings</span></button>
      </nav>
      {#if workspaceState.active && globalPage === 'Servers'}
        <div class="nav-divider"></div>
        <div class="nav-server-card"><div class="server-icon nav-server-icon">{workspaceState.active.name.slice(0,1).toUpperCase()}</div><div><strong>{workspaceState.active.name}</strong><span>{provisioning?.ready ? 'Ready' : 'Setup required'}</span></div></div>
        <nav class="server-nav" aria-label="Server navigation">{#each pages as item}<button class:active={page === item} aria-current={page === item ? 'page' : undefined} onclick={() => (page = item)}><span class="nav-icon">{@render navIcon(item)}</span><span>{item}</span></button>{/each}</nav>
      {/if}
      <div class="navigation-spacer"></div><div class="navigation-footer"><span>Builder workspace</span></div>
    </aside>

    <section class="main-view">
      {#if globalPage === 'Activity'}
        <header class="page-toolbar"><div><h1>Activity</h1><p>See ongoing tasks, progress, completed work, and anything that needs attention.</p></div></header>
        <main class="content"><Activity /></main>
      {:else if globalPage === 'Client'}
        <header class="page-toolbar"><div><h1>Client</h1><p>Connect and maintain the Minecraft client used with LazyBuilder.</p></div></header>
        <main class="content"><Client /></main>
      {:else if globalPage === 'Settings'}
        <header class="page-toolbar"><div><h1>Settings</h1><p>Choose how LazyBuilder behaves and handles safety checks.</p></div></header>
        <main class="content"><LauncherSettingsPage /></main>
      {:else if !workspaceState.active}
        <header class="page-toolbar"><div><h1>Servers</h1><p>Open, reconnect, duplicate, remove, or delete servers from one place.</p></div><div class="top-actions"><button class="secondary-button" onclick={analyzeAdoption}>Add existing</button><button class="primary-button" onclick={() => (launcherMode = 'create')}>Create server</button></div></header>
        <main class="library-content">
          {#if workspaceError}<div class="error-box" role="status">{workspaceError}</div>{/if}
          {#if workspaceState.recent.length > 0}
            {#if workspaceState.recent.length > 4}<div class="library-toolbar"><label class="search-field" aria-label="Search servers"><span aria-hidden="true">⌕</span><input bind:value={librarySearch} placeholder="Search servers" /></label></div>{/if}
            {#if visibleServers().length > 0}
              <div class="server-grid">
                {#each visibleServers() as server}
                  {@const runtime = runtimeFor(server.id)}
                  <div class="server-tile">
                    <button class="server-open" onclick={() => activateServer(server)}><div class="server-icon">{server.name.slice(0,1).toUpperCase()}</div><div class="server-tile-copy"><strong>{server.name}</strong>{#if runtime && runtimeTone(runtime)}<span class={`runtime-meta ${runtimeTone(runtime)}`}><i aria-hidden="true"></i>{runtimeMeta(runtime)}</span>{:else}<span title={server.path}>{formatLastOpened(server.lastOpenedUnixSeconds)}</span>{/if}</div><span class="open-chevron">›</span></button>
                    <div class="server-menu-wrap"><button class="server-menu-button" aria-label={`Manage ${server.name}`} aria-expanded={menuServerId === server.id} onclick={() => (menuServerId = menuServerId === server.id ? null : server.id)}>•••</button>
                      {#if menuServerId === server.id}<div class="server-menu" role="menu"><button onclick={() => activateServer(server)}>Open</button><button onclick={() => openServerFolder(server)}>Open folder</button><button onclick={() => beginLocate(server)}>Locate moved server…</button><div class="menu-divider"></div><button onclick={() => beginDuplicate(server)}>Duplicate server</button><div class="menu-divider"></div><button onclick={() => beginRemove(server)}>Remove from library</button><button class="danger-menu-item" onclick={() => beginDelete(server)}>Delete server…</button></div>{/if}
                    </div>
                  </div>
                {/each}
              </div>
            {:else}<section class="search-empty"><strong>No servers found</strong><span>Try a different server name.</span><button onclick={() => (librarySearch = '')}>Clear search</button></section>{/if}
          {:else}<section class="empty-library"><div class="empty-icon">L</div><h2>Start with a server</h2><p>Create a new build server, or add one you already use.</p><div class="empty-actions"><button class="primary-button" onclick={() => (launcherMode = 'create')}>Create server</button><button class="secondary-button" onclick={analyzeAdoption}>Add existing</button></div></section>{/if}
        </main>
      {:else}
        <header class="server-toolbar"><div class="server-toolbar-main"><div class="server-icon header-icon">{workspaceState.active.name.slice(0,1).toUpperCase()}</div><div><h1>{workspaceState.active.name}</h1><div class="server-context-meta"><span>{platformLabel()}</span><span>{provisioning?.ready ? 'Ready' : 'Setup required'}</span></div></div></div><div class="top-actions">{#if runtimeUpdates?.paperUpdateAvailable}<button class="update-button" disabled={updatingPaper} onclick={updatePaper}>{updatingPaper ? 'Updating…' : 'Update Paper'}</button>{/if}<button class="secondary-button compact-action" aria-label="Manage current server" onclick={() => openActiveActions(workspaceState.active!)}>•••</button></div></header>
        <main class="content">
          {#if workspaceError}<div class="error-box workspace-error" role="alert">{workspaceError}</div>{/if}
          {#if provisioning && !provisioning.ready}<section class="setup-card"><div class="setup-main"><div class="setup-icon">{provisioningServer ? '…' : '✓'}</div><div class="setup-copy"><span class="setup-label">Server setup</span><h2>{setupHeadline()}</h2><p>{!provisioning.javaReady || !provisioning.paperReady || !provisioning.coreModulesReady || !provisioning.configReady ? 'LazyBuilder can prepare everything this server needs automatically.' : 'Accept the Minecraft EULA to finish setup.'}</p></div></div><div class="setup-progress-row"><div class="setup-track"><span style={`width:${setupProgress()}%`}></span></div><strong>{setupProgress()}%</strong></div><div class="setup-footer"><details class="setup-details"><summary>Setup details</summary><div class="setup-steps"><span class:done={provisioning.workspaceCreated}>Workspace</span><span class:done={provisioning.javaReady}>Java</span><span class:done={provisioning.paperReady}>Paper</span><span class:done={provisioning.coreModulesReady}>Components</span><span class:done={provisioning.configReady}>Configuration</span><span class:done={provisioning.eulaAccepted}>EULA</span></div></details>{#if !provisioning.javaReady || !provisioning.paperReady || !provisioning.coreModulesReady || !provisioning.configReady}<button class="primary-button" disabled={provisioningServer} onclick={prepareServer}>{provisioningServer ? 'Preparing…' : 'Prepare server'}</button>{:else if !provisioning.eulaAccepted}<button class="primary-button" disabled={acceptingEula} onclick={acceptEula}>{acceptingEula ? 'Saving…' : 'Accept EULA'}</button>{/if}</div></section>{/if}
          {#if page === 'Overview'}<Dashboard serverName={workspaceState.active.name} />{:else if page === 'Worlds'}<Worlds />{:else if page === 'Plugins'}<Plugins />{:else}<Settings />{/if}
        </main>
      {/if}
    </section>
  </div>

  {#if launcherMode === 'create'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !creating && (launcherMode = 'home')}><div class="dialog" role="dialog" aria-modal="true"><div class="dialog-heading"><div><h2>Create server</h2><p>Set a name and choose where LazyBuilder should keep it.</p></div><button class="icon-button" disabled={creating} onclick={() => (launcherMode = 'home')}>×</button></div><label>Server name<input bind:value={createName} placeholder="Build Server" disabled={creating} /></label><label>Save in<div class="location-row"><input value={displayLocation(createParent)} title={createParent} readonly placeholder="Choose a folder" /><button class="secondary-button" disabled={creating} onclick={chooseCreateLocation}>Browse</button></div></label><div class="dialog-actions"><button class="ghost-button" disabled={creating} onclick={() => (launcherMode = 'home')}>Cancel</button><button class="primary-button" disabled={!createName.trim() || !createParent.trim() || creating} onclick={createServer}>{creating ? 'Creating…' : 'Create server'}</button></div></div></div>{/if}

  {#if adoptionPlan}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !adopting && (adoptionPlan = null)}><div class="dialog adoption-dialog" role="dialog" aria-modal="true"><div class="dialog-heading"><div><h2>Add {adoptionPlan.name}</h2><p>Review what LazyBuilder found before adding this server.</p></div><button class="icon-button" disabled={adopting} onclick={() => (adoptionPlan = null)}>×</button></div><div class="detected-grid"><div><strong>{adoptionPlan.worlds.length}</strong><span>Worlds</span></div><div><strong>{adoptionPlan.serverEntries.length}</strong><span>Server files</span></div><div><strong>{adoptionPlan.legacyPluginsToDisable.length}</strong><span>Legacy plugins</span></div></div>{#if adoptionPlan.warnings.length > 0}<div class="warning-box"><strong>Needs your attention</strong>{#each adoptionPlan.warnings as warning}<p>{warning}</p>{/each}</div>{/if}<details><summary>Technical migration details</summary><div class="details-list"><p><strong>Location:</strong> {adoptionPlan.root}</p><p><strong>Paper:</strong> {adoptionPlan.paperJar}</p></div></details><div class="dialog-actions"><button class="ghost-button" disabled={adopting} onclick={() => (adoptionPlan = null)}>Cancel</button><button class="primary-button" disabled={adopting} onclick={adoptServer}>{adopting ? 'Adding…' : 'Add server'}</button></div></div></div>{/if}

  {#if managementServer && managementMode === 'active-actions'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && closeManagement()}><div class="dialog action-dialog" role="dialog" aria-modal="true"><div class="dialog-heading"><div><h2>Manage {managementServer.name}</h2><p>Open its folder, duplicate it, remove it from the library, or delete it.</p></div><button class="icon-button" onclick={closeManagement}>×</button></div><div class="action-list"><button onclick={() => openServerFolder(managementServer!)}>Open folder</button><button onclick={() => beginDuplicate(managementServer!)}>Duplicate server</button><button onclick={() => beginRemove(managementServer!)}>Remove from library</button><button class="danger-action" onclick={() => beginDelete(managementServer!)}>Delete server…</button></div></div></div>{/if}

  {#if managementServer && managementMode === 'locate'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !managementBusy && closeManagement()}><div class="dialog" role="dialog" aria-modal="true" aria-labelledby="locate-server-heading"><div class="dialog-heading"><div><h2 id="locate-server-heading">Locate {managementServer.name}</h2><p>Reconnect this server after its folder or drive has moved.</p></div><button class="icon-button" disabled={managementBusy} onclick={closeManagement}>×</button></div>{#if managementError}<div class="error-box" role="alert">{managementError}</div>{/if}<div class="safe-notice"><strong>Previously registered location</strong><span>{managementServer.path}</span><p>LazyBuilder will only reconnect if the selected folder belongs to this same server.</p></div><label>New server folder<div class="location-row"><input value={locateCandidate} title={locateCandidate} readonly placeholder="Choose the moved server folder" /><button class="secondary-button" disabled={managementBusy} onclick={chooseLocateCandidate}>Browse</button></div></label><div class="identity-notice"><strong>Server identity protected</strong><span>Other server folders and unsupported linked locations are rejected.</span></div><div class="dialog-actions"><button class="ghost-button" disabled={managementBusy} onclick={closeManagement}>Cancel</button><button class="primary-button" disabled={managementBusy || !locateCandidate} onclick={reconnectServerLocation}>{managementBusy ? 'Reconnecting…' : 'Reconnect server'}</button></div></div></div>{/if}

  {#if managementServer && managementMode === 'duplicate'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !managementBusy && closeManagement()}><div class="dialog" role="dialog" aria-modal="true"><div class="dialog-heading"><div><h2>Duplicate server</h2><p>Create a complete, independently usable copy of {managementServer.name}.</p></div><button class="icon-button" disabled={managementBusy} onclick={closeManagement}>×</button></div>{#if managementError}<div class="error-box" role="alert">{managementError}</div>{/if}<label>New server name<input bind:value={duplicateName} disabled={managementBusy} /></label><label>Save in<div class="location-row"><input value={displayLocation(duplicateParent)} title={duplicateParent} readonly /><button class="secondary-button" disabled={managementBusy} onclick={chooseDuplicateLocation}>Browse</button></div></label><div class="included-box"><strong>Included in the copy</strong><span>Worlds, server configuration, plugins, plugin data, and LazyBuilder server settings.</span><small>Temporary files, cache, logs, and active runtime files are not copied.</small></div><div class="storage-row"><div><span>Server data</span><strong>{estimateLoading ? 'Calculating…' : formatBytes(duplicateEstimate?.sourceBytes)}</strong></div><div><span>Space needed</span><strong>{estimateLoading ? 'Calculating…' : formatBytes(duplicateEstimate?.requiredBytes)}</strong></div><div><span>Available</span><strong>{estimateLoading ? 'Calculating…' : formatBytes(duplicateEstimate?.availableBytes)}</strong></div></div><div class="dialog-actions"><button class="ghost-button" disabled={managementBusy} onclick={closeManagement}>Cancel</button><button class="primary-button" disabled={managementBusy || estimateLoading || !!managementError || !duplicateName.trim() || !duplicateParent.trim()} onclick={duplicateServer}>{managementBusy ? 'Duplicating…' : 'Duplicate server'}</button></div></div></div>{/if}

  {#if managementServer && managementMode === 'remove'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !managementBusy && closeManagement()}><div class="dialog" role="dialog" aria-modal="true"><div class="dialog-heading"><div><h2>Remove {managementServer.name} from LazyBuilder?</h2><p>This only removes the server from your LazyBuilder library.</p></div><button class="icon-button" disabled={managementBusy} onclick={closeManagement}>×</button></div>{#if managementError}<div class="error-box" role="alert">{managementError}</div>{/if}<div class="safe-notice"><strong>Your files will remain on this computer.</strong><span>{managementServer.path}</span><p>You can add the server again later.</p></div><div class="dialog-actions"><button class="ghost-button" disabled={managementBusy} onclick={closeManagement}>Cancel</button><button class="secondary-button" disabled={managementBusy} onclick={removeServerFromLibrary}>{managementBusy ? 'Removing…' : 'Remove from library'}</button></div></div></div>{/if}

  {#if managementServer && managementMode === 'delete-review'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && closeManagement()}><div class="dialog danger-dialog" role="dialog" aria-modal="true"><div class="dialog-heading"><div><h2>Delete {managementServer.name}?</h2><p>Review exactly what will be permanently removed.</p></div><button class="icon-button" onclick={closeManagement}>×</button></div>{#if managementError}<div class="error-box" role="alert">{managementError}</div>{/if}<div class="danger-summary"><strong>This permanently deletes:</strong><ul><li>Worlds</li><li>Server configuration</li><li>Plugins and plugin data</li><li>LazyBuilder workspace data</li></ul><span class="path-copy">{managementServer.path}</span><p>This cannot be undone.</p></div><div class="dialog-actions"><button class="ghost-button" onclick={closeManagement}>Cancel</button><button class="danger-button" onclick={() => (managementMode = 'delete-confirm')}>Continue</button></div></div></div>{/if}

  {#if managementServer && managementMode === 'delete-confirm'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !managementBusy && closeManagement()}><div class="dialog danger-dialog" role="dialog" aria-modal="true"><div class="dialog-heading"><div><h2>Confirm permanent deletion</h2><p>Type the server name exactly to continue.</p></div><button class="icon-button" disabled={managementBusy} onclick={closeManagement}>×</button></div>{#if managementError}<div class="error-box" role="alert">{managementError}</div>{/if}<div class="typed-confirmation"><code>{managementServer.name}</code><label>Server name<input bind:value={deleteTypedName} autocomplete="off" disabled={managementBusy} /></label></div><div class="dialog-actions"><button class="ghost-button" disabled={managementBusy} onclick={() => (managementMode = 'delete-review')}>Back</button><button class="danger-button" disabled={managementBusy || deleteTypedName !== managementServer.name} onclick={deleteServer}>{managementBusy ? 'Deleting…' : 'Delete permanently'}</button></div></div></div>{/if}
{/if}

<style>
  .loading-screen{min-height:100vh;display:flex;align-items:center;justify-content:center;gap:14px;color:var(--muted);background:var(--bg)}.loading-copy{display:grid;gap:1px}.loading-copy strong{color:var(--text)}.loading-copy span{font-size:12px}.brand-mark{width:34px;height:34px;display:grid;place-items:center;border-radius:9px;background:var(--accent);color:var(--accent-ink);font-weight:900}.brand-lockup{display:flex;align-items:center;gap:10px}.desktop-shell{display:grid;grid-template-columns:220px minmax(0,1fr);width:100%;height:100vh;background:var(--bg)}.navigation{display:flex;flex-direction:column;padding:14px 11px;border-right:1px solid var(--border-soft);background:#0e1012}.navigation-brand{padding:2px 8px 16px}.navigation-spacer{flex:1}.navigation-footer{padding:10px 9px 2px;color:var(--muted-2);font-size:9px;text-transform:uppercase}.global-nav,.server-nav{display:grid;gap:3px}.global-nav button,.server-nav button{position:relative;min-height:40px;display:flex;align-items:center;gap:10px;padding:9px 10px;border-radius:8px;background:transparent;color:var(--muted);text-align:left;cursor:pointer}.global-nav button:hover,.server-nav button:hover{background:var(--surface);color:var(--text)}.global-nav button.active,.server-nav button.active{background:var(--surface-2);color:var(--text);font-weight:650}.global-nav button.active::before,.server-nav button.active::before{content:'';position:absolute;left:-5px;top:9px;bottom:9px;width:2px;background:var(--accent)}.nav-icon{width:19px;height:19px;display:grid;place-items:center}.nav-icon :global(svg){width:17px;height:17px;fill:none;stroke:currentColor;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round}.nav-divider{height:1px;margin:14px 5px 11px;background:var(--border-soft)}.nav-server-card{display:flex;align-items:center;gap:10px;margin:0 2px 9px;padding:8px}.nav-server-card>div:last-child{min-width:0;display:grid}.nav-server-card strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.nav-server-card span{color:var(--muted-2);font-size:10px}.nav-server-icon{width:30px!important;height:30px!important;font-size:12px!important}
  .main-view{min-width:0;min-height:0;display:flex;flex-direction:column;height:100vh;overflow:hidden}.page-toolbar,.server-toolbar{min-height:82px;display:flex;align-items:center;justify-content:space-between;gap:20px;padding:15px 30px;border-bottom:1px solid var(--border-soft);background:#111315}.page-toolbar h1,.server-toolbar h1{margin:0;font-size:23px}.page-toolbar p{margin:4px 0 0;color:var(--muted);font-size:12px}.top-actions{display:flex;gap:8px}.server-toolbar-main{display:flex;align-items:center;gap:12px}.server-context-meta{display:flex;gap:8px;margin-top:4px;color:var(--muted);font-size:10px}.library-content,.content{width:min(1040px,calc(100% - 56px));margin:0 auto;padding:26px 0 48px;overflow:auto;min-height:0;flex:1}.server-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.server-tile{position:relative;min-height:78px;display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:stretch;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}.server-open{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:12px;padding:13px;border:0;background:transparent;color:var(--text);text-align:left;cursor:pointer}.server-open:hover{background:var(--surface-2)}.server-icon{width:44px;height:44px;display:grid;place-items:center;border-radius:10px;background:#252a2e;border:1px solid #3a4147;font-weight:800}.server-tile-copy{display:grid;gap:3px;min-width:0}.server-tile-copy strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.server-tile-copy span{color:var(--muted);font-size:10px}.runtime-meta{display:flex!important;align-items:center;gap:6px}.runtime-meta i{width:6px;height:6px;border-radius:50%;background:var(--muted-2)}.runtime-meta.running{color:#9ee8b9}.runtime-meta.running i{background:var(--accent)}.runtime-meta.transition{color:var(--info)}.runtime-meta.transition i{background:var(--info)}.runtime-meta.detached{color:var(--warning)}.runtime-meta.detached i{background:var(--warning)}.open-chevron{color:var(--muted-2);font-size:22px}.server-menu-wrap{position:relative;display:flex;align-items:center;padding-right:8px}.server-menu-button{width:34px;height:34px;border:0;border-radius:8px;background:transparent;color:var(--muted);cursor:pointer;letter-spacing:1px}.server-menu-button:hover{background:var(--surface-3);color:var(--text)}.server-menu{position:absolute;z-index:12;right:7px;top:58px;width:190px;display:grid;padding:6px;border:1px solid var(--border);border-radius:9px;background:#171a1d;box-shadow:0 12px 34px rgba(0,0,0,.38)}.server-menu button{padding:9px 10px;border:0;border-radius:6px;background:transparent;color:var(--text-soft);text-align:left;cursor:pointer}.server-menu button:hover{background:var(--surface-2);color:var(--text)}.server-menu .danger-menu-item{color:#ff9aa2}.menu-divider{height:1px;margin:4px 2px;background:var(--border-soft)}.empty-library,.search-empty{min-height:300px;display:grid;place-content:center;justify-items:center;text-align:center;border:1px dashed var(--border);border-radius:12px;background:var(--bg-elevated)}.empty-icon{width:50px;height:50px;display:grid;place-items:center;border-radius:13px;background:var(--surface-2);margin-bottom:13px}.empty-library h2{margin:0}.empty-library p{color:var(--muted);font-size:12px}.empty-actions{display:flex;gap:8px}.search-field{display:flex;gap:8px;margin-bottom:12px;padding:9px 11px;border:1px solid var(--border-soft);border-radius:8px;background:var(--surface)}.search-field input{flex:1;border:0;background:transparent;color:var(--text)}
  .primary-button,.secondary-button,.ghost-button,.icon-button,.update-button,.danger-button{min-height:36px;border-radius:8px;padding:8px 13px;font-weight:650;cursor:pointer}.primary-button{border:1px solid var(--accent);background:var(--accent);color:var(--accent-ink)}.secondary-button{border:1px solid var(--border);background:var(--surface-2);color:var(--text)}.ghost-button,.icon-button{border:0;background:transparent;color:var(--text-soft)}.icon-button{font-size:20px}.update-button{border:1px solid var(--accent-border);background:var(--accent-soft);color:#9ee8b9}.danger-button{border:1px solid #8e3840;background:#7a2d34;color:#fff}.compact-action{min-width:40px;padding-inline:10px}button:disabled{opacity:.5;cursor:default}.error-box{padding:11px 13px;border:1px solid #70343a;border-radius:8px;background:var(--danger-bg);color:#ffd9dc;font-size:12px}.workspace-error{margin-bottom:16px}.setup-card{display:grid;gap:14px;margin-bottom:18px;padding:17px;border:1px solid var(--border);border-radius:10px;background:var(--surface)}.setup-main{display:flex;gap:12px}.setup-icon{width:34px;height:34px;display:grid;place-items:center;border-radius:9px;background:var(--accent-soft);color:var(--accent)}.setup-copy h2{margin:2px 0 4px;font-size:16px}.setup-copy p{margin:0;color:var(--muted);font-size:11px}.setup-progress-row{display:grid;grid-template-columns:1fr auto;gap:10px;align-items:center}.setup-track{height:5px;background:var(--surface-3);border-radius:999px}.setup-track span{display:block;height:100%;background:var(--accent)}.setup-footer{display:flex;justify-content:space-between;align-items:center}.setup-steps{display:flex;gap:5px;flex-wrap:wrap;margin-top:8px}.setup-steps span{padding:3px 6px;border-radius:999px;background:var(--surface-2);font-size:9px}.setup-steps span.done{background:var(--accent-soft);color:#9ee8b9}
  .modal-backdrop{position:fixed;inset:0;z-index:20;display:grid;place-items:center;padding:24px;background:rgba(4,6,8,.72)}.dialog{width:min(500px,100%);display:grid;gap:17px;padding:21px;border:1px solid var(--border);border-radius:14px;background:var(--surface)}.adoption-dialog{width:min(580px,100%)}.action-dialog{width:min(420px,100%)}.danger-dialog{border-color:#6d3036}.dialog-heading{display:flex;justify-content:space-between;gap:18px}.dialog-heading h2{margin:0}.dialog-heading p{margin:5px 0 0;color:var(--muted);font-size:12px}label{display:grid;gap:7px;font-size:12px}.location-row{display:grid;grid-template-columns:1fr auto;gap:8px}label>input,.location-row input{min-height:36px;padding:8px 11px;border:1px solid var(--border-soft);border-radius:8px;background:var(--surface-2);color:var(--text)}.dialog-actions{display:flex;justify-content:flex-end;gap:8px}.detected-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.detected-grid>div{display:grid;padding:13px;border:1px solid var(--border-soft);border-radius:8px}.warning-box{padding:12px;border:1px solid #5f5125;border-radius:8px;background:var(--warning-bg)}.warning-box p{margin:5px 0 0;font-size:11px}.details-list{margin-top:8px;padding:10px;background:var(--bg-elevated)}.action-list{display:grid;gap:5px}.action-list button{padding:11px 12px;border:1px solid var(--border-soft);border-radius:8px;background:var(--surface-2);color:var(--text);text-align:left;cursor:pointer}.action-list button:hover{background:var(--surface-3)}.action-list .danger-action{margin-top:7px;border-color:#6d3036;color:#ffabb1}.included-box,.safe-notice,.identity-notice,.danger-summary,.typed-confirmation{display:grid;gap:8px;padding:13px;border:1px solid var(--border-soft);border-radius:9px;background:var(--bg-elevated)}.included-box span,.safe-notice span,.identity-notice span,.danger-summary .path-copy{color:var(--text-soft);font-size:11px;word-break:break-all}.included-box small,.safe-notice p,.danger-summary p{margin:0;color:var(--muted);font-size:10px}.identity-notice{border-color:var(--accent-border);background:var(--accent-soft)}.identity-notice span{word-break:normal}.storage-row{display:grid;grid-template-columns:repeat(3,1fr);gap:7px}.storage-row>div{display:grid;gap:4px;padding:10px;border:1px solid var(--border-soft);border-radius:8px}.storage-row span{color:var(--muted);font-size:9px}.storage-row strong{font-size:12px}.danger-summary ul{margin:2px 0 2px 18px;padding:0;color:var(--text-soft);font-size:11px}.typed-confirmation code{width:max-content;padding:5px 8px;border-radius:5px;background:var(--surface-3);color:#ffb7bd}
  @media(max-width:760px){.desktop-shell{grid-template-columns:68px minmax(0,1fr)}.navigation-brand strong,.global-nav button span:last-child,.server-nav button span:last-child,.nav-server-card>div:last-child,.navigation-footer{display:none}.global-nav button,.server-nav button{justify-content:center}.page-toolbar,.server-toolbar{padding:15px 18px}.library-content,.content{width:calc(100% - 28px)}.server-grid{grid-template-columns:1fr}.detected-grid,.storage-row{grid-template-columns:1fr}}
</style>
