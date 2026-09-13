<script lang="ts">
  import { onMount } from 'svelte';
  import Dashboard from './pages/Dashboard.svelte';
  import Worlds from './pages/Worlds.svelte';
  import Plugins from './pages/Plugins.svelte';
  import Settings from './pages/Settings.svelte';
  import { runtimeProduct } from './app/bridge/runtimeProductFacade';
  import type { WorkspaceEntry, WorkspaceState } from './app/bridge/runtimeApi';

  type Page = 'Dashboard' | 'Worlds' | 'Plugins' | 'Settings';
  let page: Page = 'Dashboard';
  let workspaceState: WorkspaceState = { active: null, recent: [] };
  let loadingWorkspace = true;
  let workspaceError = '';
  let createName = '';
  let createParent = '';
  let creating = false;

  const pages: Page[] = ['Dashboard', 'Worlds', 'Plugins', 'Settings'];

  async function refreshWorkspaceState() {
    loadingWorkspace = true;
    workspaceError = '';
    try {
      workspaceState = await runtimeProduct.workspace.state();
    } catch (error) {
      workspaceError = String(error);
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

  async function activateServer(server: WorkspaceEntry) {
    workspaceError = '';
    try {
      await runtimeProduct.workspace.activate(server.id);
      await refreshWorkspaceState();
    } catch (error) {
      workspaceError = String(error);
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
        <p>Create a new managed server or open one you already use with LazyBuilder.</p>
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
        <button class="primary" disabled={!createName.trim() || !createParent.trim() || creating} onclick={createServer}>
          {creating ? 'Creating…' : 'Create Server'}
        </button>
      </div>

      <div class="open-row">
        <button class="secondary wide" onclick={openServer}>Open Existing Server</button>
      </div>

      {#if workspaceState.recent.length > 0}
        <div class="recent-panel">
          <h2>Recent Servers</h2>
          {#each workspaceState.recent as server}
            <button class="recent-server" onclick={() => activateServer(server)}>
              <span>
                <strong>{server.name}</strong>
                <small>{server.path}</small>
              </span>
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
      </div>
      <nav>
        {#each pages as item}
          <button class:active={page === item} onclick={() => (page = item)}>{item}</button>
        {/each}
      </nav>
    </aside>

    <main class="content">
      {#if page === 'Dashboard'}
        <Dashboard />
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
  .launcher {
    min-height: 100vh;
    display: grid;
    place-items: center;
    padding: 40px;
    background: var(--app-bg, #101214);
    color: var(--text, #f3f4f6);
  }

  .launcher.loading { font-size: 14px; opacity: 0.75; }
  .launcher-card { width: min(760px, 100%); display: grid; gap: 22px; }
  .launcher-heading h1 { margin: 4px 0 8px; font-size: 34px; }
  .launcher-heading p { margin: 0; color: #aeb4bd; }
  .eyebrow { text-transform: uppercase; letter-spacing: .16em; font-size: 12px; }

  .create-panel, .recent-panel {
    display: grid;
    gap: 14px;
    padding: 22px;
    border: 1px solid #2d3238;
    border-radius: 14px;
    background: #171a1e;
  }

  .create-panel h2, .recent-panel h2 { margin: 0; font-size: 18px; }
  label { display: grid; gap: 7px; font-size: 13px; color: #c6cbd2; }
  input {
    width: 100%;
    box-sizing: border-box;
    padding: 11px 12px;
    border-radius: 8px;
    border: 1px solid #343a42;
    background: #101214;
    color: #f3f4f6;
  }
  .location-row { display: grid; grid-template-columns: 1fr auto; gap: 8px; }
  button { cursor: pointer; }
  button:disabled { cursor: default; opacity: .45; }
  .primary, .secondary {
    border-radius: 8px;
    padding: 11px 14px;
    font-weight: 650;
  }
  .primary { border: 1px solid #f3f4f6; background: #f3f4f6; color: #111315; }
  .secondary { border: 1px solid #3a4048; background: #20242a; color: #f3f4f6; }
  .wide { width: 100%; }
  .open-row { display: flex; }
  .error-box { padding: 12px 14px; border-radius: 9px; background: #31191b; border: 1px solid #70343a; color: #ffd9dc; font-size: 13px; }

  .recent-server {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 18px;
    width: 100%;
    text-align: left;
    padding: 12px 0;
    border: 0;
    border-top: 1px solid #2b3036;
    background: transparent;
    color: inherit;
  }
  .recent-server span:first-child { min-width: 0; display: grid; gap: 4px; }
  .recent-server small { color: #8f97a2; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

  .active-workspace {
    display: grid;
    gap: 4px;
    margin: 14px 0 18px;
    padding: 10px;
    border-radius: 8px;
    background: rgba(255,255,255,.04);
  }
  .active-workspace small { opacity: .6; }
  .active-workspace span { font-size: 11px; opacity: .55; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
