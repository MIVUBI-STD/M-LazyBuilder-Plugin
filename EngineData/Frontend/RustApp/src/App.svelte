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
{:else}
  <div class="desktop-shell">
    <aside class="navigation" aria-label="Launcher navigation">
      <div class="brand-lockup navigation-brand">
        <div class="brand-mark">L</div>
        <strong>LazyBuilder</strong>
      </div>

      <nav class="global-nav" aria-label="Library navigation">
        <button class:active={!workspaceState.active} onclick={backToServers}>
          <span class="nav-icon">▣</span>
          <span>Servers</span>
        </button>
      </nav>

      {#if workspaceState.active}
        <div class="nav-divider"></div>
        <div class="nav-section-label">Current server</div>
        <div class="nav-server-card">
          <div class="server-icon nav-server-icon">{workspaceState.active.name.slice(0, 1).toUpperCase()}</div>
          <div>
            <strong>{workspaceState.active.name}</strong>
            <span>{provisioning?.ready ? 'Ready' : 'Setup required'}</span>
          </div>
        </div>

        <nav class="server-nav" aria-label="Server navigation">
          {#each pages as item}
            <button class:active={page === item} aria-current={page === item ? 'page' : undefined} onclick={() => (page = item)}>
              <span class="nav-icon">{item === 'Overview' ? '⌂' : item === 'Worlds' ? '◇' : item === 'Plugins' ? '⬡' : '⚙'}</span>
              <span>{item}</span>
            </button>
          {/each}
        </nav>
      {/if}

      <div class="navigation-spacer"></div>
      {#if !workspaceState.active}
        <button class="nav-utility" onclick={openServer}>Open workspace</button>
      {/if}
    </aside>

    <section class="main-view">
      {#if !workspaceState.active}
        <header class="page-toolbar">
          <div>
            <h1>Servers</h1>
            <p>Choose a server and get straight back to building.</p>
          </div>
          <div class="top-actions">
            <button class="secondary-button" onclick={analyzeAdoption}>Add existing</button>
            <button class="primary-button" onclick={() => (launcherMode = 'create')}>+ Create server</button>
          </div>
        </header>

        <main class="library-content">
          {#if workspaceError}<div class="error-box" role="alert">{workspaceError}</div>{/if}

          {#if workspaceState.recent.length > 0}
            <div class="library-toolbar">
              <label class="search-field" aria-label="Search servers">
                <span aria-hidden="true">⌕</span>
                <input bind:value={librarySearch} placeholder="Search servers" />
              </label>
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
                    <span class="open-label">Open</span>
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
                <button class="secondary-button" onclick={analyzeAdoption}>Add existing</button>
              </div>
            </section>
          {/if}
        </main>
      {:else}
        <header class="server-toolbar">
          <div class="server-toolbar-main">
            <div class="server-icon header-icon">{workspaceState.active.name.slice(0, 1).toUpperCase()}</div>
            <div>
              <h1>{workspaceState.active.name}</h1>
              <div class="server-context-meta">
                <span>Paper server</span>
                <span>{provisioning?.ready ? 'Ready to manage' : 'Setup required'}</span>
              </div>
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
                      LazyBuilder can prepare everything this server needs automatically.
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
      {/if}
    </section>
  </div>

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
{/if}

<style>
  .loading-screen { min-height:100vh; display:flex; align-items:center; justify-content:center; gap:14px; color:var(--muted); background:var(--bg); }
  .loading-copy { display:grid; gap:1px; }
  .loading-copy strong { color:var(--text); }
  .loading-copy span { font-size:12px; }

  .desktop-shell { display:grid; grid-template-columns:208px minmax(0,1fr); width:100%; height:100vh; background:var(--bg); }
  .navigation { display:flex; flex-direction:column; min-width:0; padding:14px 12px; border-right:1px solid var(--border-soft); background:#0f1113; }
  .navigation-brand { padding:2px 7px 14px; }
  .brand-mark { width:34px; height:34px; display:grid; place-items:center; border-radius:9px; background:var(--accent); color:var(--accent-ink); font-weight:900; box-shadow:0 0 0 1px rgba(255,255,255,.06) inset; }
  .loading-brand { animation:breathe 1.5s ease-in-out infinite; }
  .brand-lockup { display:flex; align-items:center; gap:10px; }
  .brand-lockup strong { letter-spacing:-.02em; }

  .global-nav,.server-nav { display:grid; gap:3px; }
  .global-nav button,.server-nav button { min-height:40px; display:flex; align-items:center; gap:10px; padding:9px 10px; border-radius:var(--radius-sm); background:transparent; color:var(--muted); text-align:left; cursor:pointer; transition:background 120ms ease,color 120ms ease; }
  .global-nav button:hover,.server-nav button:hover { background:var(--surface); color:var(--text); }
  .global-nav button.active,.server-nav button.active { background:var(--surface-2); color:var(--text); font-weight:650; }
  .nav-icon { width:20px; display:inline-grid; place-items:center; color:currentColor; font-size:14px; }
  .nav-divider { height:1px; margin:14px 4px 12px; background:var(--border-soft); }
  .nav-section-label { margin:0 8px 8px; color:var(--muted-2); font-size:10px; font-weight:700; text-transform:uppercase; letter-spacing:.08em; }
  .nav-server-card { display:flex; align-items:center; gap:10px; margin:0 2px 10px; padding:9px; border-radius:var(--radius-sm); background:var(--surface); }
  .nav-server-card>div:last-child { min-width:0; display:grid; gap:1px; }
  .nav-server-card strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:12px; }
  .nav-server-card span { color:var(--muted-2); font-size:10px; }
  .nav-server-icon { width:30px !important; height:30px !important; flex:0 0 30px !important; border-radius:8px !important; font-size:12px !important; }
  .navigation-spacer { flex:1; }
  .nav-utility { min-height:36px; border-radius:var(--radius-sm); background:transparent; color:var(--muted-2); cursor:pointer; font-size:11px; }
  .nav-utility:hover { background:var(--surface); color:var(--text-soft); }

  .main-view { min-width:0; min-height:0; display:flex; flex-direction:column; height:100vh; overflow:hidden; }
  .page-toolbar,.server-toolbar { flex:0 0 auto; min-height:88px; display:flex; align-items:center; justify-content:space-between; gap:20px; padding:18px 28px; border-bottom:1px solid var(--border-soft); background:#111315; }
  .page-toolbar h1,.server-toolbar h1 { margin:0; font-size:24px; }
  .page-toolbar p { margin:5px 0 0; color:var(--muted); font-size:12px; }
  .top-actions,.server-context-actions { display:flex; align-items:center; gap:8px; }
  .server-toolbar-main { min-width:0; display:flex; align-items:center; gap:13px; }
  .server-toolbar-main>div:last-child { min-width:0; }
  .server-context-meta { display:flex; gap:8px; flex-wrap:wrap; margin-top:4px; color:var(--muted); font-size:11px; }
  .server-context-meta span+span::before { content:'•'; margin-right:8px; color:var(--muted-2); }

  .library-content,.content { width:min(1040px,calc(100% - 48px)); margin:0 auto; padding:24px 0 46px; overflow:auto; min-height:0; }
  .content { flex:1; }
  .library-content { flex:1; }
  .library-toolbar { display:flex; align-items:center; gap:10px; margin:0 0 14px; }
  .search-field { flex:1; min-width:220px; height:var(--control-height); display:flex; align-items:center; gap:8px; padding:0 11px; border:1px solid var(--border-soft); border-radius:var(--radius-sm); background:var(--surface); color:var(--muted); }
  .search-field:focus-within { border-color:color-mix(in srgb,var(--accent) 45%,var(--border)); box-shadow:0 0 0 3px color-mix(in srgb,var(--accent) 10%,transparent); }
  .search-field input { min-width:0; flex:1; border:0; outline:0; background:transparent; color:var(--text); padding:0; }

  .server-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; }
  .server-tile { min-width:0; min-height:84px; display:grid; grid-template-columns:auto minmax(0,1fr) auto; align-items:center; gap:13px; padding:14px; border:1px solid var(--border-soft); border-radius:var(--radius); background:var(--surface); color:var(--text); text-align:left; cursor:pointer; box-shadow:var(--shadow-card); transition:background 120ms ease,border-color 120ms ease,transform 120ms ease; }
  .server-tile:hover { background:var(--surface-2); border-color:var(--border-strong); }
  .server-tile:active { transform:scale(.99); }
  .server-icon { width:46px; height:46px; flex:0 0 46px; display:grid; place-items:center; border-radius:10px; background:linear-gradient(145deg,#2d3439,#202428); color:#e1e5e7; border:1px solid #3a4147; font-size:17px; font-weight:800; }
  .server-icon.header-icon { width:52px; height:52px; flex:0 0 52px; border-radius:12px; font-size:18px; }
  .server-tile-copy { min-width:0; display:grid; gap:2px; }
  .server-tile-copy strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:14px; }
  .server-tile-copy span { color:var(--text-soft); font-size:11px; }
  .server-tile-copy small { margin-top:3px; color:var(--muted-2); font-size:10px; }
  .open-label { padding:6px 9px; border-radius:var(--radius-sm); background:var(--surface-2); color:var(--text-soft); font-size:11px; font-weight:650; }
  .server-tile:hover .open-label { background:var(--accent-soft); color:#a9e9bf; }

  .empty-library,.search-empty { min-height:300px; display:grid; place-content:center; justify-items:center; text-align:center; border:1px dashed var(--border); border-radius:var(--radius); background:var(--bg-elevated); }
  .search-empty { min-height:180px; gap:4px; color:var(--muted); }
  .search-empty strong { color:var(--text-soft); }
  .empty-icon { width:52px; height:52px; display:grid; place-items:center; border-radius:13px; background:var(--surface-2); color:var(--muted); font-size:18px; font-weight:800; margin-bottom:14px; }
  .empty-library h2 { margin:0 0 5px; font-size:18px; }
  .empty-library p { max-width:430px; margin:0 0 16px; color:var(--muted); font-size:12px; }
  .empty-actions { display:flex; gap:8px; }

  .primary-button,.secondary-button,.ghost-button,.icon-button,.update-button { min-height:var(--control-height); border-radius:var(--radius-sm); padding:8px 13px; font-weight:650; cursor:pointer; transition:background-color 120ms ease,border-color 120ms ease,transform 120ms ease,color 120ms ease; }
  .primary-button { border:1px solid var(--accent); background:var(--accent); color:var(--accent-ink); }
  .primary-button:hover:not(:disabled) { background:var(--accent-hover); border-color:var(--accent-hover); }
  .primary-button:active:not(:disabled) { transform:scale(.98); background:var(--accent-active); }
  .secondary-button { border:1px solid var(--border); background:var(--surface-2); color:var(--text); }
  .secondary-button:hover:not(:disabled) { background:var(--surface-3); border-color:var(--border-strong); }
  .ghost-button { border:1px solid transparent; background:transparent; color:var(--text-soft); }
  .ghost-button:hover:not(:disabled) { background:var(--surface-2); color:var(--text); }
  .icon-button { width:38px; padding:0; display:grid; place-items:center; border:0; background:transparent; color:var(--muted); font-size:20px; }
  .icon-button:hover:not(:disabled) { background:var(--surface-2); color:var(--text); }
  .utility-icon { font-size:17px; }
  .update-button { border:1px solid var(--accent-border); background:var(--accent-soft); color:#9ee8b9; }

  .modal-backdrop { position:fixed; inset:0; z-index:20; display:grid; place-items:center; padding:24px; background:rgba(4,6,8,.72); backdrop-filter:blur(5px); }
  .dialog { width:min(500px,100%); display:grid; gap:18px; padding:22px; border:1px solid var(--border); border-radius:var(--radius-lg); background:var(--surface); box-shadow:var(--shadow-popover); }
  .adoption-dialog { width:min(580px,100%); }
  .dialog-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:18px; }
  .dialog-heading h2 { margin:0; font-size:20px; letter-spacing:-.025em; }
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
    .desktop-shell { grid-template-columns:72px minmax(0,1fr); }
    .navigation { padding:12px 8px; }
    .navigation-brand strong,.global-nav button span:last-child,.server-nav button span:last-child,.nav-section-label,.nav-server-card>div:last-child,.nav-utility { display:none; }
    .navigation-brand { justify-content:center; padding:2px 0 14px; }
    .global-nav button,.server-nav button { justify-content:center; padding:9px; }
    .nav-server-card { justify-content:center; padding:7px; }
    .page-toolbar,.server-toolbar { padding:16px 18px; }
    .library-content,.content { width:min(100% - 28px,1040px); }
    .top-actions .secondary-button { display:none; }
    .server-grid { grid-template-columns:1fr; }
    .detected-grid { grid-template-columns:1fr; }
    .setup-footer { align-items:flex-start; flex-direction:column; }
  }

  @media (prefers-reduced-motion:reduce) { .loading-brand { animation:none; } }
</style>