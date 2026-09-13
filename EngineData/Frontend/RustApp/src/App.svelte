<script lang="ts">
  import { onMount } from 'svelte';
  import Dashboard from './pages/Dashboard.svelte';
  import Worlds from './pages/Worlds.svelte';
  import Plugins from './pages/Plugins.svelte';
  import Settings from './pages/Settings.svelte';
  import { runtimeProduct } from './app/bridge/runtimeProductFacade';
  import type {
    AdoptionPlan,
    RuntimeUpdateStatus,
    WorkspaceEntry,
    WorkspaceProvisioningStatus,
    WorkspaceState
  } from './app/bridge/runtimeApi';

  type Page = 'Overview' | 'Worlds' | 'Plugins' | 'Settings';
  type LauncherMode = 'home' | 'create';

  let page: Page = 'Overview';
  let launcherMode: LauncherMode = 'home';
  let workspaceState: WorkspaceState = { active: null, recent: [] };
  let provisioning: WorkspaceProvisioningStatus | null = null;
  let runtimeUpdates: RuntimeUpdateStatus | null = null;
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

  const pages: Page[] = ['Overview', 'Worlds', 'Plugins', 'Settings'];

  function friendlyError(error: unknown) {
    const message = String(error ?? '').replace(/^Error:\s*/i, '').trim();
    if (message.includes('Stop the active server before changing workspace runtime files')) {
      return 'Stop this server before returning to the server library.';
    }
    if (message.includes('Current server state:')) {
      return 'This action is unavailable while the server is changing state. Wait a moment, then try again.';
    }
    return message || 'Something went wrong. Try again.';
  }

  function visibleServers() {
    const query = librarySearch.trim().toLowerCase();
    if (!query) return workspaceState.recent;
    return workspaceState.recent.filter((server) => server.name.toLowerCase().includes(query));
  }

  function displayLocation(path: string) {
    if (!path) return '';
    const normalized = path.replace(/[\\/]+$/, '');
    return normalized.split(/[\\/]/).pop() || normalized;
  }

  function setupProgress() {
    if (!provisioning) return 0;
    const steps = [
      provisioning.workspaceCreated,
      provisioning.javaReady,
      provisioning.paperReady,
      provisioning.coreModulesReady,
      provisioning.configReady,
      provisioning.eulaAccepted
    ];
    return Math.round((steps.filter(Boolean).length / steps.length) * 100);
  }

  function setupHeadline() {
    if (!provisioning) return 'Preparing server';
    if (!provisioning.javaReady || !provisioning.paperReady || !provisioning.coreModulesReady || !provisioning.configReady) {
      return 'Finish preparing this server';
    }
    if (!provisioning.eulaAccepted) return 'Accept the Minecraft EULA';
    return 'Server setup complete';
  }

  async function refreshWorkspaceState() {
    loadingWorkspace = true;
    workspaceError = '';
    runtimeUpdates = null;
    try {
      workspaceState = await runtimeProduct.workspace.state();
      provisioning = workspaceState.active ? await runtimeProduct.workspace.provisioningStatus() : null;
      if (workspaceState.active) {
        adoptionPlan = null;
        launcherMode = 'home';
      }
    } catch (error) {
      workspaceError = friendlyError(error);
      provisioning = null;
    } finally {
      loadingWorkspace = false;
    }
  }

  async function chooseCreateLocation() {
    workspaceError = '';
    try {
      const selected = await runtimeProduct.workspace.pickParent();
      if (selected) createParent = selected;
    } catch (error) {
      workspaceError = friendlyError(error);
    }
  }

  async function createServer() {
    if (!createName.trim() || !createParent.trim()) return;
    creating = true;
    workspaceError = '';
    try {
      await runtimeProduct.workspace.create(createParent, createName.trim());
      createName = '';
      createParent = '';
      page = 'Overview';
      await refreshWorkspaceState();
    } catch (error) {
      workspaceError = friendlyError(error);
    } finally {
      creating = false;
    }
  }

  async function openServer() {
    workspaceError = '';
    try {
      const opened = await runtimeProduct.workspace.open();
      if (opened) {
        page = 'Overview';
        await refreshWorkspaceState();
      }
    } catch (error) {
      workspaceError = friendlyError(error);
    }
  }

  async function analyzeAdoption() {
    workspaceError = '';
    try {
      adoptionPlan = await runtimeProduct.workspace.pickAdoption();
    } catch (error) {
      workspaceError = friendlyError(error);
      adoptionPlan = null;
    }
  }

  async function adoptServer() {
    if (!adoptionPlan) return;
    adopting = true;
    workspaceError = '';
    try {
      await runtimeProduct.workspace.adopt(adoptionPlan.root, adoptionPlan.name);
      adoptionPlan = null;
      page = 'Overview';
      await refreshWorkspaceState();
    } catch (error) {
      workspaceError = friendlyError(error);
    } finally {
      adopting = false;
    }
  }

  async function activateServer(server: WorkspaceEntry) {
    workspaceError = '';
    try {
      await runtimeProduct.workspace.activate(server.id);
      page = 'Overview';
      await refreshWorkspaceState();
    } catch (error) {
      workspaceError = friendlyError(error);
    }
  }

  async function backToServers() {
    if (!workspaceState.active) return;
    workspaceError = '';
    try {
      await runtimeProduct.workspace.close();
      page = 'Overview';
      await refreshWorkspaceState();
    } catch (error) {
      workspaceError = friendlyError(error);
    }
  }

  async function prepareServer() {
    provisioningServer = true;
    workspaceError = '';
    try {
      const result = await runtimeProduct.workspace.provision();
      provisioning = result.status;
      runtimeUpdates = null;
    } catch (error) {
      workspaceError = friendlyError(error);
      try { provisioning = await runtimeProduct.workspace.provisioningStatus(); } catch { /* keep primary error */ }
    } finally {
      provisioningServer = false;
    }
  }

  async function acceptEula() {
    acceptingEula = true;
    workspaceError = '';
    try {
      provisioning = await runtimeProduct.workspace.acceptEula();
    } catch (error) {
      workspaceError = friendlyError(error);
    } finally {
      acceptingEula = false;
    }
  }

  async function updatePaper() {
    updatingPaper = true;
    workspaceError = '';
    try {
      runtimeUpdates = await runtimeProduct.workspace.updatePaper();
    } catch (error) {
      workspaceError = friendlyError(error);
    } finally {
      updatingPaper = false;
    }
  }

  function formatLastOpened(seconds: number) {
    if (!seconds) return 'Not opened yet';
    const then = new Date(seconds * 1000);
    const now = new Date();
    const day = 24 * 60 * 60 * 1000;
    const diff = now.getTime() - then.getTime();
    if (diff < day && now.getDate() === then.getDate()) return 'Opened today';
    if (diff < day * 2) return 'Opened yesterday';
    return `Opened ${then.toLocaleDateString()}`;
  }

  onMount(refreshWorkspaceState);
</script>

{#snippet navIcon(item: Page | 'Servers')}
  {#if item === 'Servers'}
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5.5h16v5H4zM4 13.5h16v5H4z"/><path d="M7 8h.01M7 16h.01"/></svg>
  {:else if item === 'Overview'}
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 11 8-7 8 7"/><path d="M6.5 10v9h11v-9M10 19v-5h4v5"/></svg>
  {:else if item === 'Worlds'}
    <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8.5"/><path d="M3.8 12h16.4M12 3.5c2.3 2.4 3.4 5.2 3.4 8.5S14.3 18.1 12 20.5M12 3.5C9.7 5.9 8.6 8.7 8.6 12s1.1 6.1 3.4 8.5"/></svg>
  {:else if item === 'Plugins'}
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9.5 4.5v4h-4v7h4v4h7v-4h4v-7h-4v-4z"/></svg>
  {:else}
    <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M12 3.5v2M12 18.5v2M20.5 12h-2M5.5 12h-2M18 6l-1.4 1.4M7.4 16.6 6 18M18 18l-1.4-1.4M7.4 7.4 6 6"/></svg>
  {/if}
{/snippet}

{#if loadingWorkspace}
  <main class="loading-screen" aria-live="polite">
    <div class="brand-mark loading-brand">L</div>
    <div class="loading-copy"><strong>LazyBuilder</strong><span>Opening your server library…</span></div>
  </main>
{:else}
  <div class="desktop-shell">
    <aside class="navigation" aria-label="Launcher navigation">
      <div class="brand-lockup navigation-brand"><div class="brand-mark">L</div><strong>LazyBuilder</strong></div>

      <nav class="global-nav" aria-label="Library navigation">
        <button class:active={!workspaceState.active} onclick={backToServers}>
          <span class="nav-icon">{@render navIcon('Servers')}</span><span>Servers</span>
        </button>
      </nav>

      {#if workspaceState.active}
        <div class="nav-divider"></div>
        <div class="nav-server-card">
          <div class="server-icon nav-server-icon">{workspaceState.active.name.slice(0, 1).toUpperCase()}</div>
          <div><strong>{workspaceState.active.name}</strong><span>{provisioning?.ready ? 'Ready' : 'Setup required'}</span></div>
        </div>

        <nav class="server-nav" aria-label="Server navigation">
          {#each pages as item}
            <button class:active={page === item} aria-current={page === item ? 'page' : undefined} onclick={() => (page = item)}>
              <span class="nav-icon">{@render navIcon(item)}</span><span>{item}</span>
            </button>
          {/each}
        </nav>
      {/if}

      <div class="navigation-spacer"></div>
      <div class="navigation-footer"><span>Builder workspace</span></div>
    </aside>

    <section class="main-view">
      {#if !workspaceState.active}
        <header class="page-toolbar">
          <div><h1>Servers</h1><p>Choose a server and get back to building.</p></div>
          <div class="top-actions">
            <button class="secondary-button" onclick={analyzeAdoption}>Add existing</button>
            <button class="primary-button" onclick={() => (launcherMode = 'create')}>+ Create server</button>
          </div>
        </header>

        <main class="library-content">
          {#if workspaceError}<div class="error-box" role="alert">{workspaceError}</div>{/if}

          {#if workspaceState.recent.length > 0}
            {#if workspaceState.recent.length > 4}
              <div class="library-toolbar">
                <label class="search-field" aria-label="Search servers"><span aria-hidden="true">⌕</span><input bind:value={librarySearch} placeholder="Search servers" /></label>
              </div>
            {/if}

            {#if visibleServers().length > 0}
              <div class="server-grid">
                {#each visibleServers() as server}
                  <button class="server-tile" onclick={() => activateServer(server)}>
                    <div class="server-icon">{server.name.slice(0, 1).toUpperCase()}</div>
                    <div class="server-tile-copy"><strong>{server.name}</strong><span>{formatLastOpened(server.lastOpenedUnixSeconds)}</span></div>
                    <span class="open-chevron" aria-hidden="true">›</span>
                  </button>
                {/each}
              </div>
            {:else}
              <section class="search-empty"><strong>No servers found</strong><span>Try a different server name.</span><button onclick={() => (librarySearch = '')}>Clear search</button></section>
            {/if}
          {:else}
            <section class="empty-library">
              <div class="empty-icon">L</div><h2>Start with a server</h2>
              <p>Create a new build server, or add one you already use.</p>
              <div class="empty-actions"><button class="primary-button" onclick={() => (launcherMode = 'create')}>Create server</button><button class="secondary-button" onclick={analyzeAdoption}>Add existing</button></div>
            </section>
          {/if}
        </main>
      {:else}
        <header class="server-toolbar">
          <div class="server-toolbar-main">
            <div class="server-icon header-icon">{workspaceState.active.name.slice(0, 1).toUpperCase()}</div>
            <div><h1>{workspaceState.active.name}</h1><div class="server-context-meta"><span>Paper 1.21.4</span><span>{provisioning?.ready ? 'Ready' : 'Setup required'}</span></div></div>
          </div>
          {#if runtimeUpdates?.paperUpdateAvailable}
            <button class="update-button" disabled={updatingPaper} onclick={updatePaper}>{updatingPaper ? 'Updating…' : 'Update Paper'}</button>
          {/if}
        </header>

        <main class="content">
          {#if workspaceError}<div class="error-box workspace-error" role="alert">{workspaceError}</div>{/if}

          {#if provisioning && !provisioning.ready}
            <section class="setup-card" aria-live="polite">
              <div class="setup-main">
                <div class="setup-icon">{provisioningServer ? '…' : '✓'}</div>
                <div class="setup-copy"><span class="setup-label">Server setup</span><h2>{setupHeadline()}</h2><p>{!provisioning.javaReady || !provisioning.paperReady || !provisioning.coreModulesReady || !provisioning.configReady ? 'LazyBuilder can prepare everything this server needs automatically.' : 'Accept the Minecraft EULA to finish setup.'}</p></div>
              </div>
              <div class="setup-progress-row"><div class="setup-track" aria-label={`Server setup ${setupProgress()} percent complete`}><span style={`width:${setupProgress()}%`}></span></div><strong>{setupProgress()}%</strong></div>
              <div class="setup-footer">
                <details class="setup-details"><summary>Setup details</summary><div class="setup-steps"><span class:done={provisioning.workspaceCreated}>Workspace</span><span class:done={provisioning.javaReady}>Java</span><span class:done={provisioning.paperReady}>Paper</span><span class:done={provisioning.coreModulesReady}>Components</span><span class:done={provisioning.configReady}>Configuration</span><span class:done={provisioning.eulaAccepted}>EULA</span></div></details>
                {#if !provisioning.javaReady || !provisioning.paperReady || !provisioning.coreModulesReady || !provisioning.configReady}
                  <button class="primary-button" disabled={provisioningServer} onclick={prepareServer}>{provisioningServer ? 'Preparing…' : 'Prepare server'}</button>
                {:else if !provisioning.eulaAccepted}
                  <button class="primary-button" disabled={acceptingEula} onclick={acceptEula}>{acceptingEula ? 'Saving…' : 'Accept EULA'}</button>
                {/if}
              </div>
            </section>
          {/if}

          {#if page === 'Overview'}<Dashboard serverName={workspaceState.active.name} />
          {:else if page === 'Worlds'}<Worlds />
          {:else if page === 'Plugins'}<Plugins />
          {:else}<Settings />{/if}
        </main>
      {/if}
    </section>
  </div>

  {#if launcherMode === 'create'}
    <div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && (launcherMode = 'home')}>
      <section class="dialog" role="dialog" aria-modal="true" aria-labelledby="create-server-title">
        <div class="dialog-heading"><div><h2 id="create-server-title">Create server</h2><p>Set a name and choose where LazyBuilder should keep it.</p></div><button class="icon-button" aria-label="Close" onclick={() => (launcherMode = 'home')}>×</button></div>
        <label>Server name<input bind:value={createName} placeholder="Build Server" autofocus /></label>
        <label>Save in<div class="location-row"><input value={displayLocation(createParent)} title={createParent} readonly placeholder="Choose a folder" /><button class="secondary-button" onclick={chooseCreateLocation}>Browse</button></div></label>
        <div class="dialog-actions"><button class="ghost-button" onclick={() => (launcherMode = 'home')}>Cancel</button><button class="primary-button" disabled={!createName.trim() || !createParent.trim() || creating} onclick={createServer}>{creating ? 'Creating…' : 'Create server'}</button></div>
      </section>
    </div>
  {/if}

  {#if adoptionPlan}
    <div class="modal-backdrop">
      <section class="dialog adoption-dialog" role="dialog" aria-modal="true" aria-labelledby="adopt-server-title">
        <div class="dialog-heading"><div><h2 id="adopt-server-title">Add {adoptionPlan.name}</h2><p>Review what LazyBuilder found before adding this server.</p></div><button class="icon-button" aria-label="Close" onclick={() => (adoptionPlan = null)}>×</button></div>
        <div class="detected-grid"><div><strong>{adoptionPlan.worlds.length}</strong><span>Worlds</span></div><div><strong>{adoptionPlan.serverEntries.length}</strong><span>Server files</span></div><div><strong>{adoptionPlan.legacyPluginsToDisable.length}</strong><span>Legacy plugins</span></div></div>
        {#if adoptionPlan.warnings.length > 0}<div class="warning-box"><strong>Needs your attention</strong>{#each adoptionPlan.warnings as warning}<p>{warning}</p>{/each}</div>{:else}<div class="success-note">This server looks compatible and is ready to add.</div>{/if}
        <details><summary>Technical migration details</summary><div class="details-list"><p><strong>Location:</strong> {adoptionPlan.root}</p><p><strong>Paper:</strong> {adoptionPlan.paperJar}</p>{#if adoptionPlan.worlds.length}<p><strong>Worlds:</strong> {adoptionPlan.worlds.join(', ')}</p>{/if}{#if adoptionPlan.preservedEntries.length}<p><strong>Preserved:</strong> {adoptionPlan.preservedEntries.join(', ')}</p>{/if}</div></details>
        <div class="dialog-actions"><button class="ghost-button" onclick={() => (adoptionPlan = null)}>Cancel</button><button class="primary-button" disabled={adopting} onclick={adoptServer}>{adopting ? 'Adding…' : 'Add server'}</button></div>
      </section>
    </div>
  {/if}
{/if}

<style>
  .loading-screen{min-height:100vh;display:flex;align-items:center;justify-content:center;gap:14px;color:var(--muted);background:var(--bg)}
  .loading-copy{display:grid;gap:1px}.loading-copy strong{color:var(--text)}.loading-copy span{font-size:12px}
  .brand-mark{width:34px;height:34px;display:grid;place-items:center;border-radius:9px;background:var(--accent);color:var(--accent-ink);font-weight:900}.loading-brand{animation:breathe 1.5s ease-in-out infinite}.brand-lockup{display:flex;align-items:center;gap:10px}.brand-lockup strong{letter-spacing:-.02em}

  .desktop-shell{display:grid;grid-template-columns:220px minmax(0,1fr);width:100%;height:100vh;background:var(--bg)}
  .navigation{display:flex;flex-direction:column;min-width:0;padding:14px 11px;border-right:1px solid var(--border-soft);background:#0e1012}
  .navigation-brand{padding:2px 8px 16px}.navigation-spacer{flex:1}.navigation-footer{padding:10px 9px 2px;color:var(--muted-2);font-size:9px;text-transform:uppercase;letter-spacing:.08em}
  .global-nav,.server-nav{display:grid;gap:3px}.global-nav button,.server-nav button{position:relative;min-height:40px;display:flex;align-items:center;gap:10px;padding:9px 10px;border-radius:8px;background:transparent;color:var(--muted);text-align:left;cursor:pointer;transition:background 120ms ease,color 120ms ease}.global-nav button:hover,.server-nav button:hover{background:var(--surface);color:var(--text)}.global-nav button.active,.server-nav button.active{background:var(--surface-2);color:var(--text);font-weight:650}.global-nav button.active::before,.server-nav button.active::before{content:'';position:absolute;left:-5px;top:9px;bottom:9px;width:2px;border-radius:999px;background:var(--accent)}
  .nav-icon{width:19px;height:19px;display:grid;place-items:center;flex:0 0 19px}.nav-icon :global(svg){width:17px;height:17px;fill:none;stroke:currentColor;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round}
  .nav-divider{height:1px;margin:14px 5px 11px;background:var(--border-soft)}
  .nav-server-card{display:flex;align-items:center;gap:10px;margin:0 2px 9px;padding:8px;border-radius:9px}.nav-server-card>div:last-child{min-width:0;display:grid;gap:1px}.nav-server-card strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.nav-server-card span{color:var(--muted-2);font-size:10px}.nav-server-icon{width:30px!important;height:30px!important;flex:0 0 30px!important;border-radius:8px!important;font-size:12px!important}

  .main-view{min-width:0;min-height:0;display:flex;flex-direction:column;height:100vh;overflow:hidden}.page-toolbar,.server-toolbar{flex:0 0 auto;min-height:82px;display:flex;align-items:center;justify-content:space-between;gap:20px;padding:15px 30px;border-bottom:1px solid var(--border-soft);background:#111315}.page-toolbar h1,.server-toolbar h1{margin:0;font-size:23px}.page-toolbar p{margin:4px 0 0;color:var(--muted);font-size:12px}.top-actions{display:flex;align-items:center;gap:8px}.server-toolbar-main{min-width:0;display:flex;align-items:center;gap:12px}.server-toolbar-main>div:last-child{min-width:0}.server-context-meta{display:flex;gap:8px;flex-wrap:wrap;margin-top:4px;color:var(--muted);font-size:10px}.server-context-meta span+span::before{content:'•';margin-right:8px;color:var(--muted-2)}

  .library-content,.content{width:min(1040px,calc(100% - 56px));margin:0 auto;padding:26px 0 48px;overflow:auto;min-height:0}.content{flex:1}.library-content{flex:1}.library-toolbar{margin-bottom:12px}.search-field{display:flex;align-items:center;gap:8px;min-height:38px;padding:0 11px;border:1px solid var(--border-soft);border-radius:8px;background:var(--surface);color:var(--muted)}.search-field:focus-within{border-color:var(--accent-border);box-shadow:0 0 0 3px var(--accent-soft)}.search-field input{min-width:0;flex:1;border:0;outline:0;background:transparent;color:var(--text);padding:0}
  .server-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.server-tile{min-width:0;min-height:78px;display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:12px;padding:13px;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface);color:var(--text);text-align:left;cursor:pointer;transition:background 120ms ease,border-color 120ms ease,transform 120ms ease}.server-tile:hover{background:var(--surface-2);border-color:var(--border-strong)}.server-tile:active{transform:scale(.99)}
  .server-icon{width:44px;height:44px;flex:0 0 44px;display:grid;place-items:center;border-radius:10px;background:linear-gradient(145deg,#2d3439,#202428);color:#e1e5e7;border:1px solid #3a4147;font-size:16px;font-weight:800}.server-icon.header-icon{width:48px;height:48px;flex:0 0 48px;border-radius:11px;font-size:17px}.server-tile-copy{min-width:0;display:grid;gap:3px}.server-tile-copy strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px}.server-tile-copy span{color:var(--muted);font-size:10px}.open-chevron{color:var(--muted-2);font-size:22px;transition:transform 120ms ease,color 120ms ease}.server-tile:hover .open-chevron{transform:translateX(2px);color:var(--text-soft)}

  .empty-library,.search-empty{min-height:300px;display:grid;place-content:center;justify-items:center;text-align:center;border:1px dashed var(--border);border-radius:12px;background:var(--bg-elevated)}.search-empty{min-height:180px;gap:5px;color:var(--muted)}.search-empty button{margin-top:5px;background:transparent;color:var(--accent);cursor:pointer}.empty-icon{width:50px;height:50px;display:grid;place-items:center;border-radius:13px;background:var(--surface-2);color:var(--muted);font-size:18px;font-weight:800;margin-bottom:13px}.empty-library h2{margin:0 0 5px;font-size:18px}.empty-library p{max-width:420px;margin:0 0 16px;color:var(--muted);font-size:12px}.empty-actions{display:flex;gap:8px}

  .primary-button,.secondary-button,.ghost-button,.icon-button,.update-button{min-height:var(--control-height);border-radius:8px;padding:8px 13px;font-weight:650;cursor:pointer;transition:background 120ms ease,border-color 120ms ease,transform 120ms ease,color 120ms ease}.primary-button{border:1px solid var(--accent);background:var(--accent);color:var(--accent-ink)}.primary-button:hover:not(:disabled){background:var(--accent-hover)}.primary-button:active:not(:disabled){transform:scale(.98)}.secondary-button{border:1px solid var(--border);background:var(--surface-2);color:var(--text)}.secondary-button:hover:not(:disabled){background:var(--surface-3);border-color:var(--border-strong)}.ghost-button{border:1px solid transparent;background:transparent;color:var(--text-soft)}.ghost-button:hover:not(:disabled){background:var(--surface-2);color:var(--text)}.icon-button{width:38px;padding:0;display:grid;place-items:center;background:transparent;color:var(--muted);font-size:20px}.icon-button:hover:not(:disabled){background:var(--surface-2);color:var(--text)}.update-button{border:1px solid var(--accent-border);background:var(--accent-soft);color:#9ee8b9}

  .modal-backdrop{position:fixed;inset:0;z-index:20;display:grid;place-items:center;padding:24px;background:rgba(4,6,8,.72);backdrop-filter:blur(5px)}.dialog{width:min(500px,100%);display:grid;gap:17px;padding:21px;border:1px solid var(--border);border-radius:14px;background:var(--surface);box-shadow:var(--shadow-popover)}.adoption-dialog{width:min(580px,100%)}.dialog-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:18px}.dialog-heading h2{margin:0;font-size:20px}.dialog-heading p{margin:5px 0 0;color:var(--muted);font-size:12px}label{display:grid;gap:7px;color:var(--text-soft);font-size:12px;font-weight:600}label>input,.location-row input{width:100%;min-height:var(--control-height);padding:8px 11px;border:1px solid transparent;border-radius:8px;background:var(--surface-2);color:var(--text)}.location-row{display:grid;grid-template-columns:1fr auto;gap:8px}.dialog-actions{display:flex;justify-content:flex-end;gap:8px}.detected-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.detected-grid>div{display:grid;gap:2px;padding:13px;border:1px solid var(--border-soft);border-radius:8px;background:var(--bg-elevated)}.detected-grid strong{font-size:18px}.detected-grid span{color:var(--muted);font-size:11px}.warning-box{padding:12px 14px;border:1px solid #5f5125;border-radius:8px;background:var(--warning-bg)}.warning-box strong{font-size:12px;color:#f3dda1}.warning-box p{margin:5px 0 0;color:#d8ca9e;font-size:11px}.success-note{padding:11px 13px;border:1px solid var(--accent-border);border-radius:8px;background:var(--accent-soft);color:#a5e8bd;font-size:12px}details{color:var(--muted);font-size:12px}summary{cursor:pointer;color:var(--text-soft)}.details-list{margin-top:8px;padding:10px 12px;border-radius:8px;background:var(--bg-elevated);overflow-wrap:anywhere}.details-list p{margin:4px 0}
  .error-box{padding:11px 13px;border:1px solid #70343a;border-radius:8px;background:var(--danger-bg);color:#ffd9dc;font-size:12px}.workspace-error{margin-bottom:16px}.setup-card{display:grid;gap:14px;margin-bottom:18px;padding:17px;border:1px solid var(--border);border-radius:10px;background:var(--surface)}.setup-main{display:flex;align-items:flex-start;gap:12px}.setup-icon{width:34px;height:34px;flex:0 0 34px;display:grid;place-items:center;border-radius:9px;background:var(--accent-soft);color:var(--accent);font-weight:800}.setup-copy{min-width:0;flex:1}.setup-label{color:var(--muted);font-size:10px;font-weight:700}.setup-copy h2{margin:2px 0 4px;font-size:16px}.setup-copy p{margin:0;color:var(--muted);font-size:11px}.setup-progress-row{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:10px}.setup-track{height:5px;overflow:hidden;border-radius:999px;background:var(--surface-3)}.setup-track span{display:block;height:100%;border-radius:999px;background:var(--accent)}.setup-progress-row strong{color:var(--text-soft);font-size:10px}.setup-footer{display:flex;align-items:center;justify-content:space-between;gap:16px}.setup-details summary{color:var(--muted);font-size:10px}.setup-steps{display:flex;flex-wrap:wrap;gap:5px;margin-top:8px}.setup-steps span{padding:3px 6px;border-radius:999px;background:var(--surface-2);color:var(--muted-2);font-size:9px}.setup-steps span.done{background:var(--accent-soft);color:#9ee8b9}

  @keyframes breathe{0%,100%{transform:scale(1)}50%{transform:scale(.94)}}
  @media(max-width:760px){.desktop-shell{grid-template-columns:68px minmax(0,1fr)}.navigation{padding:12px 7px}.navigation-brand strong,.global-nav button span:last-child,.server-nav button span:last-child,.nav-server-card>div:last-child,.navigation-footer{display:none}.navigation-brand{justify-content:center;padding:2px 0 14px}.global-nav button,.server-nav button{justify-content:center;padding:9px}.nav-server-card{justify-content:center;padding:7px}.page-toolbar,.server-toolbar{padding:15px 18px}.library-content,.content{width:min(100% - 28px,1040px)}.top-actions .secondary-button{display:none}.server-grid{grid-template-columns:1fr}.detected-grid{grid-template-columns:1fr}.setup-footer{align-items:flex-start;flex-direction:column}}
  @media(prefers-reduced-motion:reduce){.loading-brand{animation:none}}
</style>
