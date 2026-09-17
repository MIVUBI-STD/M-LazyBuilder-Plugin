<script lang="ts">
  import { onMount } from 'svelte';
  import LauncherSettingsPage from './pages/LauncherSettings.svelte';
  import Client from './pages/Client.svelte';
  import Activity from './pages/Activity.svelte';
  import ServersLibrary from './pages/ServersLibrary.svelte';
  import ActiveServer from './pages/ActiveServer.svelte';
  import ActivityNavStatus from './components/ActivityNavStatus.svelte';
  import { installLauncherCloseGuard } from './app/closeGuard';
  import { runtimeProduct } from './app/bridge/runtimeProductFacade';
  import { RuntimeError } from './app/bridge/runtimeApi';
  import type { ServerRuntimeSummary, WorkspaceEntry, WorkspaceProvisioningStatus, WorkspaceState } from './app/bridge/runtimeApi';

  type Page = 'Overview' | 'Worlds' | 'Plugins' | 'Settings';
  type GlobalPage = 'Servers' | 'Activity' | 'Client' | 'Settings';

  let page: Page = 'Overview';
  let globalPage: GlobalPage = 'Servers';
  let workspaceState: WorkspaceState = { active: null, recent: [] };
  let serverRuntimes: ServerRuntimeSummary[] = [];
  let provisioning: WorkspaceProvisioningStatus | null = null;
  let loadingWorkspace = true;
  let workspaceError = '';
  let closeGuardUnlisten: (() => void) | null = null;

  const pages: Page[] = ['Overview', 'Worlds', 'Plugins', 'Settings'];

  function friendlyError(error: unknown) {
    if (error instanceof RuntimeError && error.code === 'SERVER_BUSY') return 'Stop this server before changing it.';
    if (error instanceof Error && error.message.trim()) return error.message.trim();
    const message = String(error ?? '').replace(/^Error:\s*/i, '').trim();
    return message || 'Something went wrong. Try again.';
  }

  async function refreshWorkspaceState() {
    loadingWorkspace = true;
    workspaceError = '';
    try {
      const [nextWorkspaceState, nextRuntimes] = await Promise.all([
        runtimeProduct.workspace.state(),
        runtimeProduct.server.runtimes().catch(() => [] as ServerRuntimeSummary[])
      ]);
      workspaceState = nextWorkspaceState;
      serverRuntimes = nextRuntimes;
      provisioning = workspaceState.active
        ? await runtimeProduct.workspace.provisioningStatus().catch(() => null)
        : null;
    } catch (error) {
      workspaceError = friendlyError(error);
      provisioning = null;
    } finally {
      loadingWorkspace = false;
    }
  }

  async function activateServer(server: WorkspaceEntry) {
    workspaceError = '';
    try {
      await runtimeProduct.workspace.activate(server.id);
      page = 'Overview';
      globalPage = 'Servers';
      await refreshWorkspaceState();
    } catch (error) {
      workspaceError = friendlyError(error);
      throw error;
    }
  }

  async function backToServers() {
    globalPage = 'Servers';
    if (!workspaceState.active) {
      await refreshWorkspaceState();
      return;
    }
    workspaceError = '';
    try {
      await runtimeProduct.workspace.close();
      page = 'Overview';
      await refreshWorkspaceState();
    } catch (error) {
      workspaceError = friendlyError(error);
    }
  }

  onMount(() => {
    void refreshWorkspaceState();
    void (async () => {
      try { closeGuardUnlisten = await installLauncherCloseGuard(); }
      catch { closeGuardUnlisten = null; }
    })();
    return () => {
      closeGuardUnlisten?.();
      closeGuardUnlisten = null;
    };
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
        <button class:active={globalPage === 'Activity'} aria-current={globalPage === 'Activity' ? 'page' : undefined} onclick={() => (globalPage = 'Activity')}><span class="nav-icon">{@render navIcon('Activity')}</span><span>Activity</span><ActivityNavStatus /></button>
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
        <ServersLibrary
          recent={workspaceState.recent}
          runtimes={serverRuntimes}
          error={workspaceError}
          onOpenServer={activateServer}
          onChanged={refreshWorkspaceState}
        />
      {:else}
        <ActiveServer
          server={workspaceState.active}
          {page}
          initialProvisioning={provisioning}
          onChanged={refreshWorkspaceState}
        />
      {/if}
    </section>
  </div>
{/if}

<style>
  .loading-screen{min-height:100vh;display:flex;align-items:center;justify-content:center;gap:14px;color:var(--muted);background:var(--bg)}.loading-copy{display:grid;gap:1px}.loading-copy strong{color:var(--text)}.loading-copy span{font-size:12px}.brand-mark{width:34px;height:34px;display:grid;place-items:center;border-radius:9px;background:var(--accent);color:var(--accent-ink);font-weight:900}.brand-lockup{display:flex;align-items:center;gap:10px}.desktop-shell{display:grid;grid-template-columns:220px minmax(0,1fr);width:100%;height:100vh;background:var(--bg)}.navigation{display:flex;flex-direction:column;padding:14px 11px;border-right:1px solid var(--border-soft);background:#0e1012}.navigation-brand{padding:2px 8px 16px}.navigation-spacer{flex:1}.navigation-footer{padding:10px 9px 2px;color:var(--muted-2);font-size:9px;text-transform:uppercase}.global-nav,.server-nav{display:grid;gap:3px}.global-nav button,.server-nav button{position:relative;min-height:40px;display:flex;align-items:center;gap:10px;padding:9px 10px;border:0;border-radius:8px;background:transparent;color:var(--muted);text-align:left;cursor:pointer}.global-nav button:hover,.server-nav button:hover{background:var(--surface);color:var(--text)}.global-nav button.active,.server-nav button.active{background:var(--surface-2);color:var(--text);font-weight:650}.global-nav button.active::before,.server-nav button.active::before{content:'';position:absolute;left:-5px;top:9px;bottom:9px;width:2px;background:var(--accent)}.nav-icon{width:19px;height:19px;display:grid;place-items:center}.nav-icon :global(svg){width:17px;height:17px;fill:none;stroke:currentColor;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round}.nav-divider{height:1px;margin:14px 5px 11px;background:var(--border-soft)}.nav-server-card{display:flex;align-items:center;gap:10px;margin:0 2px 9px;padding:8px}.nav-server-card>div:last-child{min-width:0;display:grid}.nav-server-card strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.nav-server-card span{color:var(--muted-2);font-size:10px}.server-icon{width:44px;height:44px;display:grid;place-items:center;border-radius:10px;background:#252a2e;border:1px solid #3a4147;font-weight:800}.nav-server-icon{width:30px!important;height:30px!important;font-size:12px!important}.main-view{min-width:0;min-height:0;display:flex;flex-direction:column;height:100vh;overflow:hidden}.page-toolbar{min-height:82px;display:flex;align-items:center;justify-content:space-between;gap:20px;padding:15px 30px;border-bottom:1px solid var(--border-soft);background:#111315}.page-toolbar h1{margin:0;font-size:23px}.page-toolbar p{margin:4px 0 0;color:var(--muted);font-size:12px}.content{width:min(1040px,calc(100% - 56px));margin:0 auto;padding:26px 0 48px;overflow:auto;min-height:0;flex:1}@media(max-width:760px){.desktop-shell{grid-template-columns:68px minmax(0,1fr)}.navigation-brand strong,.global-nav button span:last-child,.server-nav button span:last-child,.nav-server-card>div:last-child,.navigation-footer{display:none}.global-nav button,.server-nav button{justify-content:center}.global-nav button :global(.activity-status){margin-left:0}.page-toolbar{padding:15px 18px}.content{width:calc(100% - 28px)}}
</style>
