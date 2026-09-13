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
  let checkingUpdates = false;
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

  async function checkRuntimeUpdates() {
    checkingUpdates = true;
    workspaceError = '';
    try {
      runtimeUpdates = await runtimeProduct.workspace.runtimeUpdateStatus();
    } catch (error) {
      workspaceError = friendlyError(error);
    } finally {
      checkingUpdates = false;
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

{#if loadingWorkspace}
  <main class="loading-screen" aria-live="polite">
    <div class="brand-mark loading-brand">L</div>
    <div class="loading-copy">
      <strong>LazyBuilder</strong>
      <span>Opening your server library…</span>
    </div>
  </main>
{:else if !workspaceState.active}
  <main class="library-shell">
    <header class="library-topbar">
      <div class="brand-lockup">
        <div class="brand-mark">L</div>
        <strong>LazyBuilder</strong>
      </div>
      <div class="top-actions">
        <button class="secondary-button" onclick={analyzeAdoption}>Add existing server</button>
        <button class="primary-button" onclick={() => (launcherMode = 'create')}>+ Create server</button>
      </div>
    </header>

    <section class="library-content">
      <div class="library-heading">
        <h1>Servers</h1>
        <p>Choose a server and get straight back to building.</p>
      </div>

      {#if workspaceError}<div class="error-box" role="alert">{workspaceError}</div>{/if}

      {#if workspaceState.recent.length > 0}
        <div class="library-toolbar">
          <label class="search-field" aria-label="Search servers">
            <span aria-hidden="true">⌕</span>
            <input bind:value={librarySearch} placeholder="Search servers" />
          </label>
          <button class="quiet-button" onclick={openServer}>Open workspace</button>
        </div>

        {#if visibleServers().length > 0}
          <div class="server-grid">
            {#each visibleServers() as server}
              <button class="server-tile" onclick={() => activateServer(server)}>
                <div class="server-icon">{server.name.slice(0, 1).toUpperCase()}</div>
                <div class="server-tile-copy">
                  <strong>{server.name}</strong>
                  <span>Paper server</span>
                  <small>{formatLastOpened(server.lastOpenedUnixSeconds)}</small>
                </div>
                <span class="open-chevron" aria-hidden="true">›</span>
              </button>
            {/each}
          </div>
        {:else}
          <section class="search-empty">
            <strong>No servers found</strong>
            <span>Try a different server name.</span>
          </section>
        {/if}
      {:else}
        <section class="empty-library">
          <div class="empty-icon">L</div>
          <h2>Start with a server</h2>
          <p>Create a clean Paper workspace, or bring in a server you already use.</p>
          <div class="empty-actions">
            <button class="primary-button" onclick={() => (launcherMode = 'create')}>Create server</button>
            <button class="secondary-button" onclick={analyzeAdoption}>Add existing server</button>
          </div>
          <button class="quiet-link" onclick={openServer}>Open an existing LazyBuilder workspace</button>
        </section>
      {/if}
    </section>

    {#if launcherMode === 'create'}
      <div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && (launcherMode = 'home')}>
        <section class="dialog" role="dialog" aria-modal="true" aria-labelledby="create-server-title">
          <div class="dialog-heading">
            <div>
              <h2 id="create-server-title">Create server</h2>
              <p>LazyBuilder prepares everything needed for a clean Paper workspace.</p>
            </div>
            <button class="icon-button" aria-label="Close" onclick={() => (launcherMode = 'home')}>×</button>
          </div>
          <label>
            Server name
            <input bind:value={createName} placeholder="Build Server" autofocus />
          </label>
          <label>
            Save in
            <div class="location-row">
              <input value={displayLocation(createParent)} title={createParent} readonly placeholder="Choose a folder" />
              <button class="secondary-button" onclick={chooseCreateLocation}>Browse</button>
            </div>
          </label>
          <div class="dialog-actions">
            <button class="ghost-button" onclick={() => (launcherMode = 'home')}>Cancel</button>
            <button class="primary-button" disabled={!createName.trim() || !createParent.trim() || creating} onclick={createServer}>
              {creating ? 'Creating…' : 'Create server'}
            </button>
          </div>
        </section>
      </div>
    {/if}

    {#if adoptionPlan}
      <div class="modal-backdrop">
        <section class="dialog adoption-dialog" role="dialog" aria-modal="true" aria-labelledby="adopt-server-title">
          <div class="dialog-heading">
            <div>
              <h2 id="adopt-server-title">Add {adoptionPlan.name}</h2>
              <p>Review what LazyBuilder found before adding this server.</p>
            </div>
            <button class="icon-button" aria-label="Close" onclick={() => (adoptionPlan = null)}>×</button>
          </div>

          <div class="detected-grid">
            <div><strong>{adoptionPlan.worlds.length}</strong><span>Worlds</span></div>
            <div><strong>{adoptionPlan.serverEntries.length}</strong><span>Server files</span></div>
            <div><strong>{adoptionPlan.legacyPluginsToDisable.length}</strong><span>Legacy plugins</span></div>
          </div>

          {#if adoptionPlan.warnings.length > 0}
            <div class="warning-box">
              <strong>Needs your attention</strong>
              {#each adoptionPlan.warnings as warning}<p>{warning}</p>{/each}
            </div>
          {:else}
            <div class="success-note">This server looks compatible and is ready to add.</div>
          {/if}

          <details>
            <summary>Technical migration details</summary>
            <div class="details-list">
              <p><strong>Location:</strong> {adoptionPlan.root}</p>
              <p><strong>Paper:</strong> {adoptionPlan.paperJar}</p>
              {#if adoptionPlan.worlds.length}<p><strong>Worlds:</strong> {adoptionPlan.worlds.join(', ')}</p>{/if}
              {#if adoptionPlan.preservedEntries.length}<p><strong>Preserved:</strong> {adoptionPlan.preservedEntries.join(', ')}</p>{/if}
            </div>
          </details>

          <div class="dialog-actions">
            <button class="ghost-button" onclick={() => (adoptionPlan = null)}>Cancel</button>
            <button class="primary-button" disabled={adopting} onclick={adoptServer}>{adopting ? 'Adding…' : 'Add server'}</button>
          </div>
        </section>
      </div>
    {/if}
  </main>
{:else}
  <div class="app-shell">
    <aside class="app-rail" aria-label="Application navigation">
      <div class="brand-mark rail-brand" title="LazyBuilder">L</div>
      <button class="rail-button" aria-label="Server library" title="Server library" onclick={backToServers}>←</button>
      <div class="rail-spacer"></div>
    </aside>

    <section class="workspace-view">
      <header class="server-context-header">
        <div class="server-context-inner">
          <div class="server-context-main">
            <div class="server-icon header-icon">{workspaceState.active.name.slice(0, 1).toUpperCase()}</div>
            <div class="server-context-copy">
              <h1>{workspaceState.active.name}</h1>
              <div class="server-context-meta">
                <span>Paper server</span>
                <span>{provisioning?.ready ? 'Ready to manage' : 'Setup required'}</span>
              </div>
            </div>
            <div class="server-context-actions">
              {#if runtimeUpdates?.paperUpdateAvailable}
                <button class="update-button" disabled={updatingPaper} onclick={updatePaper}>
                  {updatingPaper ? 'Updating…' : `Update Paper · ${runtimeUpdates.latestPaperBuild}`}
                </button>
              {:else}
                <button class="icon-button utility-icon" aria-label="Check Paper update" title="Check Paper update" disabled={checkingUpdates} onclick={checkRuntimeUpdates}>
                  {checkingUpdates ? '…' : '↻'}
                </button>
              {/if}
            </div>
          </div>

          <nav class="context-tabs" aria-label="Server sections">
            {#each pages as item}
              <button class="context-tab" class:active={page === item} aria-current={page === item ? 'page' : undefined} onclick={() => (page = item)}>{item}</button>
            {/each}
          </nav>
        </div>
      </header>

      <main class="content">
        {#if workspaceError}<div class="error-box workspace-error" role="alert">{workspaceError}</div>{/if}

        {#if provisioning && !provisioning.ready}
          <section class="setup-card" aria-live="polite">
            <div class="setup-main">
              <div class="setup-icon">{provisioningServer ? '…' : '✓'}</div>
              <div class="setup-copy">
                <span class="setup-label">Server setup</span>
                <h2>{setupHeadline()}</h2>
                <p>
                  {#if !provisioning.javaReady || !provisioning.paperReady || !provisioning.coreModulesReady || !provisioning.configReady}
                    LazyBuilder can prepare the required runtime, Paper files and core components automatically.
                  {:else}
                    One final Minecraft requirement needs your approval before this server can start.
                  {/if}
                </p>
              </div>
            </div>

            <div class="setup-progress-row">
              <div class="setup-track" aria-label={`Server setup ${setupProgress()} percent complete`}>
                <span style={`width:${setupProgress()}%`}></span>
              </div>
              <strong>{setupProgress()}%</strong>
            </div>

            <div class="setup-footer">
              <details class="setup-details">
                <summary>Show setup details</summary>
                <div class="setup-steps">
                  <span class:done={provisioning.workspaceCreated}>Workspace</span>
                  <span class:done={provisioning.javaReady}>Java runtime</span>
                  <span class:done={provisioning.paperReady}>Paper</span>
                  <span class:done={provisioning.coreModulesReady}>LazyBuilder components</span>
                  <span class:done={provisioning.configReady}>Server configuration</span>
                  <span class:done={provisioning.eulaAccepted}>Minecraft EULA</span>
                </div>
              </details>

              {#if !provisioning.javaReady || !provisioning.paperReady || !provisioning.coreModulesReady || !provisioning.configReady}
                <button class="primary-button" disabled={provisioningServer} onclick={prepareServer}>
                  {provisioningServer ? 'Preparing…' : 'Prepare server'}
                </button>
              {:else if !provisioning.eulaAccepted}
                <button class="primary-button" disabled={acceptingEula} onclick={acceptEula}>
                  {acceptingEula ? 'Saving…' : 'Accept EULA'}
                </button>
              {/if}
            </div>
          </section>
        {/if}

        {#if page === 'Overview'}
          <Dashboard serverName={workspaceState.active.name} />
        {:else if page === 'Worlds'}
          <Worlds />
        {:else if page === 'Plugins'}
          <Plugins />
        {:else}
          <Settings />
        {/if}
      </main>
    </section>
  </div>
{/if}

<style>
  .loading-screen { min-height:100vh; display:flex; align-items:center; justify-content:center; gap:14px; color:var(--muted); background:var(--bg); }
  .loading-copy { display:grid; gap:1px; }
  .loading-copy strong { color:var(--text); }
  .loading-copy span { font-size:12px; }

  .brand-mark { width:36px; height:36px; display:grid; place-items:center; border-radius:10px; background:var(--accent); color:var(--accent-ink); font-weight:900; box-shadow:0 0 0 1px rgba(255,255,255,.06) inset; }
  .loading-brand { animation:breathe 1.5s ease-in-out infinite; }
  .rail-brand { width:44px; height:44px; margin-bottom:4px; }
  .brand-lockup { display:flex; align-items:center; gap:10px; }
  .brand-lockup strong { letter-spacing:-.02em; }

  .library-shell { min-height:100vh; height:100vh; overflow:auto; background:var(--bg); }
  .library-topbar { position:sticky; top:0; z-index:5; height:68px; display:flex; align-items:center; justify-content:space-between; gap:20px; padding:0 28px; border-bottom:1px solid var(--border-soft); background:rgba(15,17,19,.96); backdrop-filter:blur(16px); }
  .top-actions { display:flex; align-items:center; gap:8px; }
  .library-content { width:min(var(--content-max), calc(100% - 56px)); margin:0 auto; padding:40px 0 70px; }
  .library-heading { margin-bottom:22px; }
  .library-heading h1 { font-size:30px; }
  .library-heading p { margin:6px 0 0; color:var(--muted); }

  .library-toolbar { display:flex; align-items:center; gap:10px; margin:0 0 16px; }
  .search-field { flex:1; min-width:220px; height:var(--control-height); display:flex; align-items:center; gap:8px; padding:0 11px; border-radius:var(--radius-sm); background:var(--surface-2); color:var(--muted); box-shadow:var(--shadow-inset); }
  .search-field input { min-width:0; flex:1; border:0; outline:0; background:transparent; color:var(--text); padding:0; }

  .server-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(280px,1fr)); gap:12px; }
  .server-tile { min-width:0; min-height:92px; display:flex; align-items:center; gap:14px; padding:16px; border:1px solid var(--border-soft); border-radius:var(--radius); background:var(--surface); color:var(--text); text-align:left; cursor:pointer; box-shadow:var(--shadow-card); transition:background 140ms ease,border-color 140ms ease,transform 140ms ease,filter 140ms ease; }
  .server-tile:hover { background:var(--surface-2); border-color:var(--border-strong); filter:brightness(1.05); }
  .server-tile:active { transform:scale(.985); }
  .server-icon { width:48px; height:48px; flex:0 0 48px; display:grid; place-items:center; border-radius:11px; background:linear-gradient(145deg,#2d3439,#202428); color:#e1e5e7; border:1px solid #3a4147; font-size:18px; font-weight:800; }
  .server-icon.header-icon { width:58px; height:58px; flex-basis:58px; border-radius:13px; font-size:20px; }
  .server-tile-copy { min-width:0; display:grid; gap:2px; flex:1; }
  .server-tile-copy strong { font-size:15px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
  .server-tile-copy span { color:var(--text-soft); font-size:12px; }
  .server-tile-copy small { margin-top:4px; color:var(--muted-2); font-size:11px; }
  .open-chevron { color:var(--muted-2); font-size:24px; transition:transform 140ms ease,color 140ms ease; }
  .server-tile:hover .open-chevron { transform:translateX(2px); color:var(--text-soft); }

  .empty-library,.search-empty { min-height:330px; display:grid; place-content:center; justify-items:center; text-align:center; border:1px dashed var(--border); border-radius:var(--radius-lg); background:var(--bg-elevated); }
  .search-empty { min-height:220px; gap:4px; color:var(--muted); }
  .search-empty strong { color:var(--text-soft); }
  .empty-icon { width:56px; height:56px; display:grid; place-items:center; border-radius:15px; background:var(--surface-2); color:var(--muted); font-size:20px; font-weight:800; margin-bottom:16px; }
  .empty-library h2 { margin:0 0 6px; font-size:20px; }
  .empty-library p { max-width:460px; margin:0 0 18px; color:var(--muted); }
  .empty-actions { display:flex; gap:8px; }
  .quiet-link { margin-top:14px; border:0; background:transparent; color:var(--muted-2); cursor:pointer; font-size:11px; }
  .quiet-link:hover { color:var(--text-soft); text-decoration:underline; }

  .primary-button,.secondary-button,.ghost-button,.quiet-button,.icon-button,.update-button { min-height:var(--control-height); border-radius:var(--radius-sm); padding:8px 13px; font-weight:650; cursor:pointer; transition:background-color 120ms ease,border-color 120ms ease,transform 120ms ease,color 120ms ease; }
  .primary-button { border:1px solid var(--accent); background:var(--accent); color:var(--accent-ink); }
  .primary-button:hover:not(:disabled) { background:var(--accent-hover); border-color:var(--accent-hover); }
  .primary-button:active:not(:disabled) { transform:scale(.98); background:var(--accent-active); }
  .secondary-button { border:1px solid var(--border); background:var(--surface-2); color:var(--text); }
  .secondary-button:hover:not(:disabled) { background:var(--surface-3); border-color:var(--border-strong); }
  .ghost-button,.quiet-button { border:1px solid transparent; background:transparent; color:var(--text-soft); }
  .ghost-button:hover:not(:disabled),.quiet-button:hover:not(:disabled) { background:var(--surface-2); color:var(--text); }
  .quiet-button { color:var(--muted); }
  .icon-button { width:38px; padding:0; display:grid; place-items:center; border:0; background:transparent; color:var(--muted); font-size:20px; }
  .icon-button:hover:not(:disabled) { background:var(--surface-2); color:var(--text); }
  .utility-icon { font-size:17px; }
  .update-button { border:1px solid var(--accent-border); background:var(--accent-soft); color:#9ee8b9; }

  .modal-backdrop { position:fixed; inset:0; z-index:20; display:grid; place-items:center; padding:24px; background:rgba(4,6,8,.72); backdrop-filter:blur(5px); }
  .dialog { width:min(500px,100%); display:grid; gap:18px; padding:22px; border:1px solid var(--border); border-radius:var(--radius-lg); background:var(--surface); box-shadow:var(--shadow-popover); }
  .adoption-dialog { width:min(580px,100%); }
  .dialog-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:18px; }
  .dialog-heading h2 { margin:0; font-size:21px; letter-spacing:-.025em; }
  .dialog-heading p { margin:5px 0 0; color:var(--muted); font-size:12px; }
  label { display:grid; gap:7px; color:var(--text-soft); font-size:12px; font-weight:600; }
  label>input,.location-row input { width:100%; min-height:var(--control-height); padding:8px 11px; border:1px solid transparent; border-radius:var(--radius-sm); background:var(--surface-2); color:var(--text); box-shadow:var(--shadow-inset); }
  .location-row { display:grid; grid-template-columns:1fr auto; gap:8px; }
  .dialog-actions { display:flex; justify-content:flex-end; gap:8px; padding-top:2px; }
  .detected-grid { display:grid; grid-template-columns:repeat(3,1fr); gap:8px; }
  .detected-grid>div { display:grid; gap:2px; padding:13px; border:1px solid var(--border-soft); border-radius:var(--radius-sm); background:var(--bg-elevated); }
  .detected-grid strong { font-size:18px; }
  .detected-grid span { color:var(--muted); font-size:11px; }
  .warning-box { padding:12px 14px; border:1px solid #5f5125; border-radius:var(--radius-sm); background:var(--warning-bg); }
  .warning-box strong { font-size:12px; color:#f3dda1; }
  .warning-box p { margin:5px 0 0; color:#d8ca9e; font-size:11px; line-height:1.45; }
  .success-note { padding:11px 13px; border:1px solid var(--accent-border); border-radius:var(--radius-sm); background:var(--accent-soft); color:#a5e8bd; font-size:12px; }
  details { color:var(--muted); font-size:12px; }
  summary { cursor:pointer; color:var(--text-soft); }
  .details-list { margin-top:8px; padding:10px 12px; border-radius:var(--radius-sm); background:var(--bg-elevated); overflow-wrap:anywhere; }
  .details-list p { margin:4px 0; }

  .error-box { padding:11px 13px; border:1px solid #70343a; border-radius:var(--radius-sm); background:var(--danger-bg); color:#ffd9dc; font-size:12px; }
  .workspace-error { margin-bottom:16px; }
  .setup-card { display:grid; gap:15px; margin-bottom:20px; padding:18px; border:1px solid var(--border); border-radius:var(--radius); background:var(--surface); box-shadow:var(--shadow-card); }
  .setup-main { display:flex; align-items:flex-start; gap:13px; }
  .setup-icon { width:36px; height:36px; flex:0 0 36px; display:grid; place-items:center; border-radius:10px; background:var(--accent-soft); color:var(--accent); font-weight:800; }
  .setup-copy { min-width:0; flex:1; }
  .setup-label { color:var(--muted); font-size:11px; font-weight:700; }
  .setup-copy h2 { margin:2px 0 4px; font-size:17px; }
  .setup-copy p { margin:0; max-width:700px; color:var(--muted); font-size:12px; }
  .setup-progress-row { display:grid; grid-template-columns:minmax(0,1fr) auto; align-items:center; gap:10px; }
  .setup-track { height:6px; overflow:hidden; border-radius:999px; background:var(--surface-3); }
  .setup-track span { display:block; height:100%; border-radius:999px; background:var(--accent); transition:width 180ms ease; }
  .setup-progress-row strong { color:var(--text-soft); font-size:11px; }
  .setup-footer { display:flex; align-items:center; justify-content:space-between; gap:16px; }
  .setup-details summary { color:var(--muted); font-size:11px; }
  .setup-steps { display:flex; flex-wrap:wrap; gap:6px; margin-top:9px; }
  .setup-steps span { padding:4px 7px; border-radius:999px; background:var(--surface-2); color:var(--muted-2); font-size:10px; }
  .setup-steps span.done { background:var(--accent-soft); color:#9ee8b9; }

  @keyframes breathe { 0%,100%{transform:scale(1)} 50%{transform:scale(.94)} }

  @media (max-width:760px) {
    .library-topbar { padding:0 14px; }
    .library-content { width:min(100% - 28px,var(--content-max)); padding-top:28px; }
    .top-actions .secondary-button { display:none; }
    .library-toolbar { align-items:stretch; flex-direction:column; }
    .server-grid { grid-template-columns:1fr; }
    .detected-grid { grid-template-columns:1fr; }
    .server-context-actions .update-button { max-width:150px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    .setup-footer { align-items:flex-start; flex-direction:column; }
  }

  @media (prefers-reduced-motion:reduce) { .loading-brand { animation:none; } }
</style>