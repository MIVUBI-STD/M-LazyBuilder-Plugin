<script lang="ts">
  import { onMount } from 'svelte';
  import Dashboard from './pages/Dashboard.svelte';
  import Worlds from './pages/Worlds.svelte';
  import Plugins from './pages/Plugins.svelte';
  import Settings from './pages/Settings.svelte';
  import { runtimeProduct } from './app/bridge/runtimeProductFacade';
  import type { AdoptionPlan, RuntimeUpdateStatus, WorkspaceEntry, WorkspaceProvisioningStatus, WorkspaceState } from './app/bridge/runtimeApi';

  type Page = 'Dashboard' | 'Worlds' | 'Plugins' | 'Settings';
  let page: Page = 'Dashboard';
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

  const pages: Page[] = ['Dashboard', 'Worlds', 'Plugins', 'Settings'];

  async function refreshWorkspaceState() {
    loadingWorkspace = true;
    workspaceError = '';
    runtimeUpdates = null;
    try {
      workspaceState = await runtimeProduct.workspace.state();
      provisioning = workspaceState.active ? await runtimeProduct.workspace.provisioningStatus() : null;
      if (workspaceState.active) adoptionPlan = null;
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
      if (opened) await refreshWorkspaceState();
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
      await refreshWorkspaceState();
    } catch (error) {
      workspaceError = String(error);
    }
  }

  async function switchServer() {
    workspaceError = '';
    try {
      await runtimeProduct.workspace.close();
      page = 'Dashboard';
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

  onMount(refreshWorkspaceState);
</script>

{#if loadingWorkspace}
  <main class="launcher loading">Loading LazyBuilder…</main>
{:else if !workspaceState.active}
  <main class="launcher">
    <section class="launcher-card">
      <div class="launcher-heading">
        <p class="eyebrow">LazyBuilder</p>
        <h1>Minecraft Server Workspace</h1>
        <p>Create a new managed server, open a LazyBuilder workspace, or safely adopt an existing Paper server.</p>
      </div>

      {#if workspaceError}
        <div class="error-box">{workspaceError}</div>
      {/if}

      <div class="create-panel">
        <h2>Create New Server</h2>
        <label>
          Server name
          <input bind:value={createName} placeholder="Work Server" />
        </label>
        <label>
          Location
          <div class="location-row">
            <input value={createParent} readonly placeholder="Choose a folder…" />
            <button class="secondary" onclick={chooseCreateLocation}>Browse…</button>
          </div>
        </label>
        <p class="hint">LazyBuilder will create the server folder inside this location.</p>
        <button class="primary" disabled={!createName.trim() || !createParent.trim() || creating} onclick={createServer}>
          {creating ? 'Creating…' : 'Create Server'}
        </button>
      </div>

      <div class="open-actions">
        <button class="secondary wide" onclick={openServer}>Open Existing LazyBuilder Server</button>
        <button class="secondary wide" onclick={analyzeAdoption}>Adopt Existing Paper Server</button>
      </div>

      {#if adoptionPlan}
        <section class="adoption-panel">
          <div class="adoption-heading">
            <div>
              <small>Adoption Review</small>
              <h2>{adoptionPlan.name}</h2>
              <p>{adoptionPlan.root}</p>
            </div>
            <button class="text-button" onclick={() => (adoptionPlan = null)}>Cancel</button>
          </div>

          <div class="adoption-grid">
            <div><small>Paper JAR</small><strong>{adoptionPlan.paperJar}</strong></div>
            <div><small>Detected worlds</small><strong>{adoptionPlan.worlds.length}</strong></div>
            <div><small>Runtime entries to move</small><strong>{adoptionPlan.serverEntries.length}</strong></div>
            <div><small>Root items preserved</small><strong>{adoptionPlan.preservedEntries.length}</strong></div>
          </div>

          {#if adoptionPlan.worlds.length > 0}
            <div class="migration-list">
              <small>Worlds → world-system/worlds/</small>
              <p>{adoptionPlan.worlds.join(', ')}</p>
            </div>
          {/if}
          <div class="migration-list">
            <small>Paper runtime → server/</small>
            <p>{adoptionPlan.serverEntries.join(', ')}</p>
          </div>
          {#if adoptionPlan.legacyPluginsToDisable.length > 0}
            <div class="migration-list disabled-list">
              <small>Replaced plugins → tools/lazybuilder/disabled-plugins/</small>
              <p>{adoptionPlan.legacyPluginsToDisable.join(', ')}</p>
            </div>
          {/if}
          {#if adoptionPlan.preservedEntries.length > 0}
            <div class="migration-list preserved">
              <small>Untouched in original root</small>
              <p>{adoptionPlan.preservedEntries.join(', ')}</p>
            </div>
          {/if}

          <div class="warning-list">
            {#each adoptionPlan.warnings as warning}<p>• {warning}</p>{/each}
          </div>

          <button class="primary" disabled={adopting} onclick={adoptServer}>
            {adopting ? 'Adopting Server…' : 'Adopt Server'}
          </button>
        </section>
      {/if}

      {#if workspaceState.recent.length > 0}
        <div class="recent-panel">
          <h2>Recent Servers</h2>
          {#each workspaceState.recent as server}
            <button class="recent-server" onclick={() => activateServer(server)}>
              <span><strong>{server.name}</strong><small>{server.path}</small></span>
              <span>Open</span>
            </button>
          {/each}
        </div>
      {/if}
    </section>
  </main>
{:else}
  <div class="shell">
    <aside class="sidebar">
      <h2>LazyBuilder</h2>
      <div class="active-workspace">
        <small>Active Server</small>
        <strong>{workspaceState.active.name}</strong>
        <span title={workspaceState.active.path}>{workspaceState.active.path}</span>
        <button class="switch-button" onclick={switchServer}>Switch Server</button>
      </div>
      <nav>
        {#each pages as item}<button class:active={page === item} onclick={() => (page = item)}>{item}</button>{/each}
      </nav>
    </aside>

    <main class="content">
      {#if workspaceError}<div class="error-box workspace-error">{workspaceError}</div>{/if}

      {#if provisioning && !provisioning.ready}
        <section class="provision-card">
          <div><small>Server Setup</small><strong>{provisioning.nextStep}</strong></div>
          <div class="provision-steps">
            <span class:done={provisioning.workspaceCreated}>Workspace</span>
            <span class:done={provisioning.javaReady}>Java 21</span>
            <span class:done={provisioning.paperReady}>Paper</span>
            <span class:done={provisioning.coreModulesReady}>Core Modules</span>
            <span class:done={provisioning.configReady}>Config</span>
            <span class:done={provisioning.eulaAccepted}>EULA</span>
          </div>

          {#if !provisioning.javaReady || !provisioning.paperReady || !provisioning.coreModulesReady || !provisioning.configReady}
            <div class="prepare-row">
              <p>LazyBuilder will prepare managed Java 21, a stable Paper 1.21.4 build when one is not already pinned, and matching core modules.</p>
              <button class="primary" disabled={provisioningServer} onclick={prepareServer}>{provisioningServer ? 'Preparing Server…' : 'Prepare Server'}</button>
            </div>
          {:else if !provisioning.eulaAccepted}
            <div class="eula-row">
              <p>Server files are ready. Before Minecraft can run, you must explicitly accept the Minecraft EULA.</p>
              <button class="secondary" disabled={acceptingEula} onclick={acceptEula}>{acceptingEula ? 'Saving…' : 'I Agree to the Minecraft EULA'}</button>
            </div>
          {/if}
        </section>
      {:else if provisioning?.ready}
        <section class="runtime-update-card">
          <div class="runtime-update-heading">
            <div><small>Paper Runtime</small><strong>Paper updates are manual and server-scoped</strong></div>
            <button class="secondary compact" disabled={checkingUpdates} onclick={checkRuntimeUpdates}>{checkingUpdates ? 'Checking…' : 'Check Paper Update'}</button>
          </div>
          {#if runtimeUpdates}
            <div class="runtime-update-grid">
              <div>
                <small>Paper</small>
                <strong>{runtimeUpdates.currentPaperBuild ?? 'Unknown'} → {runtimeUpdates.latestPaperBuild}</strong>
                <span>{runtimeUpdates.paperUpdateAvailable ? 'Stable update available' : 'Current stable build'}</span>
                {#if runtimeUpdates.paperUpdateAvailable}
                  <button class="secondary compact" disabled={updatingPaper} onclick={updatePaper}>{updatingPaper ? 'Updating…' : 'Update Paper'}</button>
                {/if}
              </div>
            </div>
          {:else}
            <p class="hint">No network check is performed automatically. Check only when you want to review the Paper runtime.</p>
          {/if}
        </section>
      {/if}

      {#if page === 'Dashboard'}<Dashboard />{:else if page === 'Worlds'}<Worlds />{:else if page === 'Plugins'}<Plugins />{:else}<Settings />{/if}
    </main>
  </div>
{/if}

<style>
  .launcher { min-height: 100vh; display: grid; place-items: center; padding: 40px; background: var(--app-bg, #101214); color: var(--text, #f3f4f6); }
  .launcher.loading { font-size: 14px; opacity: 0.75; }
  .launcher-card { width: min(760px, 100%); display: grid; gap: 22px; }
  .launcher-heading h1 { margin: 4px 0 8px; font-size: 34px; }
  .launcher-heading p { margin: 0; color: #aeb4bd; }
  .eyebrow { text-transform: uppercase; letter-spacing: .16em; font-size: 12px; }
  .create-panel, .recent-panel, .provision-card, .adoption-panel, .runtime-update-card { display: grid; gap: 14px; padding: 22px; border: 1px solid #2d3238; border-radius: 14px; background: #171a1e; }
  .create-panel h2, .recent-panel h2, .adoption-panel h2 { margin: 0; font-size: 18px; }
  label { display: grid; gap: 7px; font-size: 13px; color: #c6cbd2; }
  input { width: 100%; box-sizing: border-box; padding: 11px 12px; border-radius: 8px; border: 1px solid #343a42; background: #101214; color: #f3f4f6; }
  .location-row { display: grid; grid-template-columns: 1fr auto; gap: 8px; }
  .hint { margin: -4px 0 0; color: #7f8792; font-size: 12px; }
  button { cursor: pointer; }
  button:disabled { cursor: default; opacity: .45; }
  .primary, .secondary { border-radius: 8px; padding: 11px 14px; font-weight: 650; }
  .primary { border: 1px solid #f3f4f6; background: #f3f4f6; color: #111315; }
  .secondary { border: 1px solid #3a4048; background: #20242a; color: #f3f4f6; }
  .compact { padding: 7px 10px; font-size: 12px; }
  .wide { width: 100%; }
  .open-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
  .error-box { padding: 12px 14px; border-radius: 9px; background: #31191b; border: 1px solid #70343a; color: #ffd9dc; font-size: 13px; }
  .workspace-error { margin-bottom: 14px; }
  .recent-server { display: flex; align-items: center; justify-content: space-between; gap: 18px; width: 100%; text-align: left; padding: 12px 0; border: 0; border-top: 1px solid #2b3036; background: transparent; color: inherit; }
  .recent-server span:first-child { min-width: 0; display: grid; gap: 4px; }
  .recent-server small { color: #8f97a2; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .active-workspace { display: grid; gap: 4px; margin: 14px 0 18px; padding: 10px; border-radius: 8px; background: rgba(255,255,255,.04); }
  .active-workspace small { opacity: .6; }
  .active-workspace span { font-size: 11px; opacity: .55; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .switch-button { margin-top: 7px; padding: 7px 9px; border-radius: 6px; border: 1px solid rgba(255,255,255,.13); background: transparent; color: inherit; font-size: 12px; }
  .provision-card, .runtime-update-card { margin-bottom: 18px; }
  .provision-card > div:first-child { display: grid; gap: 3px; }
  .provision-card small, .adoption-panel small, .runtime-update-card small { color: #8f97a2; }
  .provision-steps { display: flex; gap: 8px; flex-wrap: wrap; }
  .provision-steps span { padding: 6px 9px; border-radius: 999px; background: #252a30; color: #8f97a2; font-size: 12px; }
  .provision-steps span.done { color: #f3f4f6; background: #343a42; }
  .prepare-row, .eula-row { display: grid; gap: 10px; border-top: 1px solid #2d3238; padding-top: 14px; }
  .prepare-row p, .eula-row p { margin: 0; color: #aeb4bd; font-size: 13px; line-height: 1.5; }
  .adoption-heading, .runtime-update-heading { display: flex; justify-content: space-between; align-items: start; gap: 16px; }
  .adoption-heading > div, .runtime-update-heading > div { display: grid; gap: 4px; min-width: 0; }
  .adoption-heading p { margin: 0; color: #8f97a2; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .text-button { border: 0; background: transparent; color: #aeb4bd; padding: 4px; }
  .adoption-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
  .runtime-update-grid { display: grid; grid-template-columns: 1fr; gap: 10px; }
  .adoption-grid > div, .runtime-update-grid > div { display: grid; gap: 6px; padding: 10px; background: #101214; border-radius: 8px; }
  .runtime-update-grid span { color: #8f97a2; font-size: 12px; }
  .runtime-update-grid button { margin-top: 4px; justify-self: start; }
  .migration-list { display: grid; gap: 4px; padding-top: 10px; border-top: 1px solid #2d3238; }
  .migration-list p { margin: 0; color: #c6cbd2; font-size: 12px; line-height: 1.5; overflow-wrap: anywhere; }
  .migration-list.preserved p { color: #9fa7b0; }
  .disabled-list p { color: #e5c88e; }
  .warning-list { display: grid; gap: 4px; padding: 10px 12px; border-radius: 8px; background: #2a2417; border: 1px solid #554924; }
  .warning-list p { margin: 0; color: #eadcae; font-size: 12px; line-height: 1.45; }
</style>