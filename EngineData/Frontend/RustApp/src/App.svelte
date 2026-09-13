<script lang="ts">
  import { onMount } from 'svelte';
  import Dashboard from './pages/Dashboard.svelte';
  import Worlds from './pages/Worlds.svelte';
  import Plugins from './pages/Plugins.svelte';
  import Settings from './pages/Settings.svelte';
  import { runtimeProduct } from './app/bridge/runtimeProductFacade';
  import type { AdoptionPlan, RuntimeUpdateStatus, WorkspaceEntry, WorkspaceProvisioningStatus, WorkspaceState } from './app/bridge/runtimeApi';

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
  let createName = '';
  let createParent = '';
  let creating = false;
  let adopting = false;
  let provisioningServer = false;
  let acceptingEula = false;
  let checkingUpdates = false;
  let updatingPaper = false;

  const pages: Page[] = ['Overview', 'Worlds', 'Plugins', 'Settings'];

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
      workspaceError = String(error);
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
      workspaceError = String(error);
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
      workspaceError = String(error);
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
      workspaceError = String(error);
    }
  }

  async function analyzeAdoption() {
    workspaceError = '';
    try {
      adoptionPlan = await runtimeProduct.workspace.pickAdoption();
    } catch (error) {
      workspaceError = String(error);
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
      workspaceError = String(error);
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
      workspaceError = String(error);
    }
  }

  async function backToServers() {
    workspaceError = '';
    try {
      await runtimeProduct.workspace.close();
      page = 'Overview';
      await refreshWorkspaceState();
    } catch (error) {
      workspaceError = String(error);
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
      workspaceError = String(error);
      try { provisioning = await runtimeProduct.workspace.provisioningStatus(); } catch { /* preserve primary error */ }
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
      workspaceError = String(error);
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
      workspaceError = String(error);
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
      workspaceError = String(error);
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
  <main class="loading-screen">
    <div class="brand-mark">L</div>
    <span>Opening LazyBuilder…</span>
  </main>
{:else if !workspaceState.active}
  <main class="library-shell">
    <header class="library-topbar">
      <div class="brand-lockup">
        <div class="brand-mark">L</div>
        <strong>LazyBuilder</strong>
      </div>
      <div class="top-actions">
        <button class="ghost-button" onclick={openServer}>Open workspace</button>
        <button class="secondary-button" onclick={analyzeAdoption}>Add existing server</button>
        <button class="primary-button" onclick={() => (launcherMode = 'create')}>+ Create server</button>
      </div>
    </header>

    <section class="library-content">
      <div class="library-heading">
        <div>
          <p class="eyebrow">Server library</p>
          <h1>Your servers</h1>
          <p>Pick a server to continue, or add another workspace.</p>
        </div>
      </div>

      {#if workspaceError}<div class="error-box">{workspaceError}</div>{/if}

      {#if workspaceState.recent.length > 0}
        <div class="server-grid">
          {#each workspaceState.recent as server}
            <button class="server-tile" onclick={() => activateServer(server)} title={server.path}>
              <div class="server-icon">{server.name.slice(0, 1).toUpperCase()}</div>
              <div class="server-tile-copy">
                <strong>{server.name}</strong>
                <span>Paper server</span>
                <small>{formatLastOpened(server.lastOpenedUnixSeconds)}</small>
              </div>
              <span class="open-chevron">›</span>
            </button>
          {/each}
        </div>
      {:else}
        <section class="empty-library">
          <div class="empty-icon">L</div>
          <h2>No servers yet</h2>
          <p>Create a new Paper server or add one you already use.</p>
          <div class="empty-actions">
            <button class="primary-button" onclick={() => (launcherMode = 'create')}>Create server</button>
            <button class="secondary-button" onclick={analyzeAdoption}>Add existing server</button>
          </div>
        </section>
      {/if}
    </section>

    {#if launcherMode === 'create'}
      <div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && (launcherMode = 'home')}>
        <section class="dialog" role="dialog" aria-modal="true" aria-labelledby="create-server-title">
          <div class="dialog-heading">
            <div>
              <p class="eyebrow">New server</p>
              <h2 id="create-server-title">Create a server</h2>
            </div>
            <button class="icon-button" aria-label="Close" onclick={() => (launcherMode = 'home')}>×</button>
          </div>
          <label>
            Server name
            <input bind:value={createName} placeholder="Build Server" autofocus />
          </label>
          <label>
            Location
            <div class="location-row">
              <input value={createParent} readonly placeholder="Choose where to create it" />
              <button class="secondary-button" onclick={chooseCreateLocation}>Browse</button>
            </div>
          </label>
          <p class="hint">LazyBuilder prepares the workspace structure for you.</p>
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
              <p class="eyebrow">Existing server</p>
              <h2 id="adopt-server-title">Add {adoptionPlan.name}</h2>
              <p class="dialog-path" title={adoptionPlan.root}>{adoptionPlan.root}</p>
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
              <strong>Review before adding</strong>
              {#each adoptionPlan.warnings as warning}<p>{warning}</p>{/each}
            </div>
          {:else}
            <p class="adopt-summary">LazyBuilder detected a compatible Paper server and can organize it into a managed workspace.</p>
          {/if}

          <details>
            <summary>Show migration details</summary>
            <div class="details-list">
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
  <div class="shell">
    <aside class="sidebar">
      <div class="brand-lockup sidebar-brand">
        <div class="brand-mark small">L</div>
        <strong>LazyBuilder</strong>
      </div>

      <button class="back-button" onclick={backToServers}>‹ <span>Servers</span></button>

      <div class="server-identity">
        <div class="server-icon small">{workspaceState.active.name.slice(0, 1).toUpperCase()}</div>
        <div>
          <strong>{workspaceState.active.name}</strong>
          <span>Paper server</span>
        </div>
      </div>

      <nav>
        {#each pages as item}
          <button class:active={page === item} onclick={() => (page = item)}>{item}</button>
        {/each}
      </nav>

      <div class="sidebar-footer">
        {#if runtimeUpdates?.paperUpdateAvailable}
          <div class="update-notice">
            <span class="update-dot"></span>
            <div><strong>Paper update ready</strong><small>Build {runtimeUpdates.latestPaperBuild}</small></div>
            <button disabled={updatingPaper} onclick={updatePaper}>{updatingPaper ? 'Updating…' : 'Update'}</button>
          </div>
        {:else}
          <button class="check-update" disabled={checkingUpdates} onclick={checkRuntimeUpdates}>{checkingUpdates ? 'Checking Paper…' : 'Check Paper update'}</button>
        {/if}
      </div>
    </aside>

    <main class="content">
      {#if workspaceError}<div class="error-box workspace-error">{workspaceError}</div>{/if}

      {#if provisioning && !provisioning.ready}
        <section class="setup-card">
          <div class="setup-heading">
            <div>
              <p class="eyebrow">Server setup</p>
              <h2>{provisioning.nextStep}</h2>
              <p>Complete setup once, then LazyBuilder will manage this server normally.</p>
            </div>
          </div>
          <div class="setup-progress" aria-label="Server setup progress">
            <span class:done={provisioning.workspaceCreated}>Workspace</span>
            <span class:done={provisioning.javaReady}>Java</span>
            <span class:done={provisioning.paperReady}>Paper</span>
            <span class:done={provisioning.coreModulesReady}>Core</span>
            <span class:done={provisioning.configReady}>Config</span>
            <span class:done={provisioning.eulaAccepted}>EULA</span>
          </div>
          {#if !provisioning.javaReady || !provisioning.paperReady || !provisioning.coreModulesReady || !provisioning.configReady}
            <button class="primary-button setup-action" disabled={provisioningServer} onclick={prepareServer}>{provisioningServer ? 'Preparing server…' : 'Prepare server'}</button>
          {:else if !provisioning.eulaAccepted}
            <div class="eula-copy">
              <p>Minecraft requires EULA acceptance before the server can start.</p>
              <button class="primary-button" disabled={acceptingEula} onclick={acceptEula}>{acceptingEula ? 'Saving…' : 'Accept Minecraft EULA'}</button>
            </div>
          {/if}
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
  </div>
{/if}

<style>
  .loading-screen { min-height: 100vh; display: grid; place-content: center; justify-items: center; gap: 12px; color: var(--muted); }
  .brand-mark { width: 34px; height: 34px; display: grid; place-items: center; border-radius: 10px; background: var(--accent); color: var(--accent-ink); font-weight: 900; }
  .brand-mark.small { width: 28px; height: 28px; border-radius: 8px; font-size: 13px; }
  .brand-lockup { display: flex; align-items: center; gap: 10px; }
  .brand-lockup strong { letter-spacing: -.02em; }

  .library-shell { min-height: 100vh; background: var(--bg); }
  .library-topbar { height: 68px; display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 0 28px; border-bottom: 1px solid var(--border-soft); background: #101214; }
  .top-actions { display: flex; align-items: center; gap: 8px; }
  .library-content { width: min(1120px, calc(100% - 56px)); margin: 0 auto; padding: 46px 0 70px; }
  .library-heading { display: flex; justify-content: space-between; align-items: end; margin-bottom: 24px; }
  .library-heading h1 { margin: 2px 0 3px; font-size: 30px; line-height: 1.12; letter-spacing: -.035em; }
  .library-heading p:last-child { margin: 0; color: var(--muted); }
  .eyebrow { margin: 0 0 4px; color: var(--muted-2); text-transform: uppercase; letter-spacing: .12em; font-size: 10px; font-weight: 750; }

  .server-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(270px, 1fr)); gap: 12px; }
  .server-tile { min-width: 0; display: flex; align-items: center; gap: 14px; padding: 17px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); color: var(--text); text-align: left; cursor: pointer; transition: background .12s ease, border-color .12s ease, transform .12s ease; }
  .server-tile:hover { background: var(--surface-2); border-color: #434a51; transform: translateY(-1px); }
  .server-icon { width: 48px; height: 48px; flex: 0 0 48px; display: grid; place-items: center; border-radius: 11px; background: linear-gradient(145deg, #2d3439, #202428); color: #d9dde0; border: 1px solid #3a4147; font-size: 18px; font-weight: 800; }
  .server-icon.small { width: 34px; height: 34px; flex-basis: 34px; border-radius: 8px; font-size: 13px; }
  .server-tile-copy { min-width: 0; display: grid; gap: 2px; flex: 1; }
  .server-tile-copy strong { font-size: 15px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .server-tile-copy span { color: var(--text-soft); font-size: 12px; }
  .server-tile-copy small { margin-top: 4px; color: var(--muted-2); font-size: 11px; }
  .open-chevron { color: var(--muted-2); font-size: 24px; }

  .empty-library { min-height: 360px; display: grid; place-content: center; justify-items: center; text-align: center; border: 1px dashed #343a40; border-radius: 14px; background: var(--bg-elevated); }
  .empty-icon { width: 56px; height: 56px; display: grid; place-items: center; border-radius: 15px; background: var(--surface-2); color: var(--muted); font-size: 20px; font-weight: 800; margin-bottom: 16px; }
  .empty-library h2 { margin: 0 0 6px; font-size: 20px; }
  .empty-library p { margin: 0 0 18px; color: var(--muted); }
  .empty-actions { display: flex; gap: 8px; }

  .primary-button, .secondary-button, .ghost-button, .icon-button { border-radius: 8px; padding: 9px 13px; font-weight: 650; cursor: pointer; }
  .primary-button { border: 1px solid var(--accent); background: var(--accent); color: var(--accent-ink); }
  .primary-button:hover:not(:disabled) { background: var(--accent-hover); border-color: var(--accent-hover); }
  .secondary-button { border: 1px solid var(--border); background: var(--surface-2); color: var(--text); }
  .secondary-button:hover:not(:disabled) { background: var(--surface-3); }
  .ghost-button { border: 1px solid transparent; background: transparent; color: var(--text-soft); }
  .ghost-button:hover:not(:disabled) { background: var(--surface-2); color: var(--text); }
  .icon-button { width: 34px; height: 34px; padding: 0; border: 0; background: transparent; color: var(--muted); font-size: 22px; }
  .icon-button:hover { background: var(--surface-2); color: var(--text); }

  .modal-backdrop { position: fixed; inset: 0; z-index: 20; display: grid; place-items: center; padding: 24px; background: rgba(4, 6, 8, .72); backdrop-filter: blur(4px); }
  .dialog { width: min(500px, 100%); display: grid; gap: 17px; padding: 22px; border: 1px solid var(--border); border-radius: 14px; background: #181b1e; box-shadow: var(--shadow-popover); }
  .adoption-dialog { width: min(580px, 100%); }
  .dialog-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; }
  .dialog-heading h2 { margin: 0; font-size: 21px; letter-spacing: -.025em; }
  .dialog-path { max-width: 430px; margin: 5px 0 0; color: var(--muted); font-size: 11px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  label { display: grid; gap: 7px; color: var(--text-soft); font-size: 12px; font-weight: 600; }
  input { width: 100%; padding: 10px 11px; border: 1px solid var(--border); border-radius: 8px; background: #111315; color: var(--text); }
  .location-row { display: grid; grid-template-columns: 1fr auto; gap: 8px; }
  .hint { margin: -5px 0 0; color: var(--muted-2); font-size: 11px; }
  .dialog-actions { display: flex; justify-content: flex-end; gap: 8px; padding-top: 4px; }
  .detected-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
  .detected-grid > div { display: grid; gap: 2px; padding: 12px; border: 1px solid var(--border-soft); border-radius: 9px; background: #131619; }
  .detected-grid strong { font-size: 18px; }
  .detected-grid span { color: var(--muted); font-size: 11px; }
  .warning-box { padding: 12px 14px; border: 1px solid #5f5125; border-radius: 9px; background: #282317; }
  .warning-box strong { font-size: 12px; color: #f3dda1; }
  .warning-box p { margin: 5px 0 0; color: #d8ca9e; font-size: 11px; line-height: 1.45; }
  .adopt-summary { margin: 0; color: var(--muted); }
  details { color: var(--muted); font-size: 12px; }
  summary { cursor: pointer; color: var(--text-soft); }
  .details-list { margin-top: 8px; padding: 10px 12px; border-radius: 8px; background: #131619; overflow-wrap: anywhere; }
  .details-list p { margin: 4px 0; }

  .sidebar-brand { padding: 3px 7px 14px; }
  .back-button { display: flex; align-items: center; gap: 7px; margin-top: 2px; font-size: 13px; color: var(--muted) !important; }
  .back-button:hover { color: var(--text) !important; }
  .server-identity { display: flex; align-items: center; gap: 10px; margin: 10px 5px 2px; padding: 11px; border-radius: 10px; background: var(--surface); border: 1px solid var(--border-soft); }
  .server-identity > div:last-child { min-width: 0; display: grid; gap: 1px; }
  .server-identity strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
  .server-identity span { color: var(--muted-2); font-size: 10px; }
  .sidebar-footer { margin-top: auto; padding: 10px 4px 0; }
  .check-update { width: 100%; padding: 8px !important; text-align: center !important; color: var(--muted-2) !important; font-size: 11px; }
  .update-notice { display: grid; grid-template-columns: auto 1fr; gap: 8px; padding: 9px; border: 1px solid #28583a; border-radius: 9px; background: #14241a; }
  .update-notice > div { display: grid; min-width: 0; }
  .update-notice strong { font-size: 11px; }
  .update-notice small { color: #8cb99a; font-size: 10px; }
  .update-notice button { grid-column: 1 / -1; padding: 6px 8px; text-align: center; background: #1d6e3c; font-size: 11px; }
  .update-dot { width: 7px; height: 7px; margin-top: 5px; border-radius: 50%; background: var(--accent); }

  .error-box { padding: 11px 13px; border: 1px solid #70343a; border-radius: 9px; background: var(--danger-bg); color: #ffd9dc; font-size: 12px; }
  .workspace-error { margin-bottom: 16px; }
  .setup-card { display: grid; gap: 16px; margin-bottom: 24px; padding: 18px; border: 1px solid #4a4532; border-radius: 12px; background: #1f1d16; }
  .setup-heading h2 { margin: 0 0 4px; font-size: 18px; }
  .setup-heading p:last-child { margin: 0; color: #b8b09a; font-size: 12px; }
  .setup-progress { display: flex; gap: 6px; flex-wrap: wrap; }
  .setup-progress span { padding: 5px 8px; border-radius: 999px; background: #2a2924; color: #77746a; font-size: 10px; }
  .setup-progress span.done { background: #1b3424; color: #9ac6a5; }
  .setup-action { justify-self: start; }
  .eula-copy { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
  .eula-copy p { margin: 0; color: #b8b09a; font-size: 12px; }

  @media (max-width: 760px) {
    .library-topbar { padding: 0 16px; }
    .library-content { width: min(100% - 28px, 1120px); padding-top: 28px; }
    .top-actions .ghost-button { display: none; }
    .server-grid { grid-template-columns: 1fr; }
    .detected-grid { grid-template-columns: 1fr; }
  }
</style>
