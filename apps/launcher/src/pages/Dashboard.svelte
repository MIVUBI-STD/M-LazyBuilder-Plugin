<script lang="ts">
  import { onMount } from 'svelte';
  import ServerConsole from '../components/ServerConsole.svelte';
  import BackupPanel from './BackupPanel.svelte';
  import HealthPanel from './HealthPanel.svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { ServerLogTail, ServerPreflight, ServerRuntimeSummary, ServerSnapshot, ServerState } from '../app/bridge/runtimeApi';

  export let serverName = 'Server';

  let snapshot: ServerSnapshot = { state: 'Offline', health: 'Offline', cpuLoadPercent: 0, usedMemoryBytes: 0, maxMemoryBytes: 0, pid: null, logPath: '' };
  let preflight: ServerPreflight = { ready: false, workspace: '', serverDirectory: '', paperJar: '', worldsDirectory: '', javaPath: '', javaVersion: '', logDirectory: '', issues: [] };
  let connectionPort: number | null = null;
  let runtimes: ServerRuntimeSummary[] = [];
  let logTail: ServerLogTail = { path: '', content: '', truncated: false };
  let logOpen = false;
  let consoleOpen = false;
  let logBusy = false;
  let error = '';
  let notice = '';
  let busy = false;
  let runtimePollInFlight = false;

  const ACTIVE_RUNTIME_STATES = new Set<ServerState>(['Starting', 'Online', 'Stopping', 'Detached']);
  const ACTIVE_RUNTIME_POLL_MS = 3000;
  const IDLE_RUNTIME_POLL_MS = 15000;
  const MAX_CONCURRENT_SERVERS = 3;

  function friendlyError(value: unknown) { return value instanceof Error && value.message.trim() ? value.message.trim() : String(value).replace(/^Error:\s*/i, '').trim() || 'Something went wrong. Try again.'; }
  function category(message: string) {
    const value = message.toLowerCase();
    if (value.includes('java')) return 'Java'; if (value.includes('paper') || value.includes('jar')) return 'Paper';
    if (value.includes('workspace') || value.includes('directory') || value.includes('path')) return 'Workspace';
    if (value.includes('pid') || value.includes('process') || value.includes('detached')) return 'Process';
    if (value.includes('port') || value.includes('address') || value.includes('bind')) return 'Network';
    if (value.includes('permission') || value.includes('denied') || value.includes('filesystem')) return 'Files';
    return 'Server';
  }
  function stateLabel(state: ServerState) { if (state === 'Online') return 'Running'; if (state === 'Detached') return 'Running externally'; return state; }
  function stateTone(state: ServerState) { if (state === 'Online') return 'running'; if (state === 'Starting' || state === 'Stopping') return 'transition'; if (state === 'Detached') return 'warning'; if (state === 'Crashed') return 'danger'; return 'offline'; }
  function stateDescription(state: ServerState) {
    if (state === 'Online') return 'Ready for builders to join.';
    if (state === 'Starting') return 'Starting the server. You can stop it if startup stalls.';
    if (state === 'Stopping') return 'Stopping safely…';
    if (state === 'Detached') return 'The server is running outside this launcher session.';
    if (state === 'Crashed') return 'The server stopped unexpectedly.';
    return 'Start the server when your team is ready to build.';
  }
  function activeRuntimes() { return runtimes.filter((runtime) => ACTIVE_RUNTIME_STATES.has(runtime.state)); }
  function managedMemoryBytes() { return activeRuntimes().reduce((total, runtime) => total + runtime.usedMemoryBytes, 0); }
  async function refreshRuntime() {
    const [nextSnapshot, nextPort, nextRuntimes] = await Promise.all([
      runtimeProduct.server.snapshot(),
      runtimeProduct.server.connectionPort(),
      runtimeProduct.server.runtimes()
    ]);
    snapshot = nextSnapshot;
    connectionPort = nextPort;
    runtimes = nextRuntimes;
  }
  async function refreshPreflight() { preflight = await runtimeProduct.server.preflight(); }
  async function refreshAll() { try { await Promise.all([refreshRuntime(), refreshPreflight()]); error = ''; } catch (e) { error = friendlyError(e); } }
  async function pollRuntime() {
    if (runtimePollInFlight) return;
    runtimePollInFlight = true;
    try { await refreshRuntime(); } catch (e) { if (!error) error = friendlyError(e); }
    finally { runtimePollInFlight = false; }
  }
  async function action(run: () => Promise<void>) {
    if (busy) return; busy = true; notice = ''; error = '';
    try { await run(); await refreshAll(); }
    catch (e) { const operationError = friendlyError(e); try { await Promise.all([refreshRuntime(), refreshPreflight()]); } catch {} error = operationError; }
    finally { busy = false; }
  }
  async function stopDetachedProcess() {
    if (busy || snapshot.state !== 'Detached') return;
    if (!window.confirm('Stop the externally running server so LazyBuilder can manage it again?')) return;
    busy = true; error = ''; notice = '';
    try { const result = await runtimeProduct.server.recoverDetached(); notice = result.message; await refreshAll(); }
    catch (e) { error = friendlyError(e); } finally { busy = false; }
  }
  async function loadLog() {
    if (logBusy) return; logBusy = true;
    try { logTail = await runtimeProduct.server.logTail(snapshot.logPath || ''); logOpen = true; }
    catch (e) { error = friendlyError(e); } finally { logBusy = false; }
  }
  onMount(() => {
    let disposed = false;
    let timer: number | null = null;

    const schedule = () => {
      if (disposed || document.hidden) return;
      const delay = ACTIVE_RUNTIME_STATES.has(snapshot.state) ? ACTIVE_RUNTIME_POLL_MS : IDLE_RUNTIME_POLL_MS;
      timer = window.setTimeout(async () => {
        timer = null;
        if (disposed || document.hidden) return;
        await pollRuntime();
        schedule();
      }, delay);
    };

    const refreshNow = () => {
      if (disposed || document.hidden) return;
      if (timer !== null) { window.clearTimeout(timer); timer = null; }
      void pollRuntime().finally(schedule);
    };

    const handleVisibility = () => {
      if (document.hidden) {
        if (timer !== null) { window.clearTimeout(timer); timer = null; }
        return;
      }
      refreshNow();
    };

    void refreshAll().finally(schedule);
    document.addEventListener('visibilitychange', handleVisibility);
    window.addEventListener('focus', refreshNow);

    return () => {
      disposed = true;
      if (timer !== null) window.clearTimeout(timer);
      document.removeEventListener('visibilitychange', handleVisibility);
      window.removeEventListener('focus', refreshNow);
    };
  });
  const gb = (bytes: number) => bytes / 1024 / 1024 / 1024;
</script>

<section class="overview" aria-label={`${serverName} overview`}>
  <header class="page-head">
    <div><h2>Overview</h2><p>Your server at a glance.</p></div>
    <div class="primary-actions">
      {#if ['Offline','Crashed'].includes(snapshot.state)}
        <button class="primary" disabled={busy || !preflight.ready} onclick={() => action(runtimeProduct.server.start)}>{busy ? 'Starting…' : 'Start server'}</button>
      {:else if snapshot.state === 'Online' || snapshot.state === 'Starting'}
        <button class="stop" disabled={busy} onclick={() => action(runtimeProduct.server.stop)}>{busy ? 'Stopping…' : 'Stop server'}</button>
      {:else if snapshot.state === 'Detached'}
        <button class="stop" disabled={busy} onclick={stopDetachedProcess}>Stop external server</button>
      {:else}<button class="secondary" disabled>{stateLabel(snapshot.state)}…</button>{/if}
      <details class="more-menu"><summary aria-label="More server actions">•••</summary><div class="menu-popover">{#if snapshot.state === 'Online'}<button disabled={busy} onclick={() => action(runtimeProduct.server.restart)}>Restart server</button>{/if}<button disabled={snapshot.state !== 'Online'} onclick={() => (consoleOpen = true)}>Open server console</button><button disabled={logBusy} onclick={loadLog}>{logBusy ? 'Loading log…' : 'View server log'}</button></div></details>
    </div>
  </header>

  <section class="status-card {stateTone(snapshot.state)}">
    <div class="status-copy"><span class="status-dot {stateTone(snapshot.state)}"></span><div><strong>{stateLabel(snapshot.state)}</strong><p>{stateDescription(snapshot.state)}</p></div></div>
    {#if snapshot.state === 'Online'}<div class="live-facts">{#if connectionPort}<div><span>Address</span><strong>localhost:{connectionPort}</strong></div>{/if}<div><span>Memory</span><strong>{gb(snapshot.usedMemoryBytes).toFixed(1)} / {gb(snapshot.maxMemoryBytes).toFixed(1)} GB</strong></div><div><span>CPU</span><strong>{snapshot.cpuLoadPercent.toFixed(0)}%</strong></div></div>{:else if snapshot.maxMemoryBytes > 0}<div class="live-facts"><div><span>Memory limit</span><strong>{gb(snapshot.maxMemoryBytes).toFixed(1)} GB</strong></div></div>{/if}
  </section>

  {#if activeRuntimes().length > 0}
    <section class="fleet-card" aria-label="Running servers">
      <header><div><strong>Running servers</strong><span>{activeRuntimes().length} / {MAX_CONCURRENT_SERVERS} active</span></div><span>{gb(managedMemoryBytes()).toFixed(1)} GB managed RAM</span></header>
      <div class="fleet-list">
        {#each activeRuntimes() as runtime}
          <div class="fleet-row"><div class="fleet-name"><span class="status-dot {stateTone(runtime.state)}"></span><div><strong>{runtime.workspaceName}</strong><span>{stateLabel(runtime.state)}</span></div></div><div class="fleet-meta">{#if runtime.paperPort}<span>:{runtime.paperPort}</span>{/if}<span>{gb(runtime.usedMemoryBytes).toFixed(1)} GB</span></div></div>
        {/each}
      </div>
    </section>
  {/if}

  {#if error}<section class="notice danger" role="alert"><strong>{category(error)} problem</strong><p>{error}</p></section>{/if}
  {#if notice}<section class="notice success" aria-live="polite"><strong>Server control</strong><p>{notice}</p></section>{/if}
  {#if preflight.issues.length > 0 && ['Offline','Crashed','Detached'].includes(snapshot.state)}<details class="attention" open={!preflight.ready}><summary><span><strong>Needs attention</strong><small>{preflight.issues.length} item{preflight.issues.length === 1 ? '' : 's'} blocking start</small></span><span>Details</span></summary><div class="issue-list">{#each preflight.issues as issue}<div class="issue-row"><strong>{category(issue)}</strong><span>{issue}</span></div>{/each}</div></details>{/if}
  {#if snapshot.state === 'Detached'}<section class="notice warning"><strong>Server is running externally</strong><p>Stop the external server first, then start it here so LazyBuilder can manage it normally.</p></section>{/if}

  <HealthPanel onRepaired={refreshAll} />
  <BackupPanel onRestored={refreshAll} />
</section>

<ServerConsole open={consoleOpen} onClose={() => (consoleOpen = false)} />

{#if logOpen}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && (logOpen = false)}><div class="log-dialog" role="dialog" aria-modal="true" aria-labelledby="server-log-title"><header><div><h2 id="server-log-title">Server log</h2><p>{logTail.path ? logTail.path.split(/[\\/]/).pop() : 'latest.log'}{logTail.truncated ? ' · showing recent lines' : ''}</p></div><button class="icon-button" aria-label="Close server log" onclick={() => (logOpen = false)}>×</button></header><pre>{logTail.content || 'No server log output is available yet.'}</pre><footer><button class="secondary" disabled={logBusy} onclick={loadLog}>{logBusy ? 'Refreshing…' : 'Refresh'}</button><button class="primary" onclick={() => (logOpen = false)}>Done</button></footer></div></div>{/if}

<style>
  .overview{width:min(920px,100%)}.page-head{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;margin-bottom:16px}.page-head h2{margin:0;font-size:18px}.page-head p{margin:4px 0 0;color:var(--muted);font-size:12px}.primary-actions{display:flex;align-items:center;gap:7px}.primary,.secondary,.stop{min-height:var(--control-height);border-radius:8px;padding:8px 13px;font-weight:700;cursor:pointer}.primary{border:1px solid var(--accent);background:var(--accent);color:var(--accent-ink)}.secondary,.stop{border:1px solid var(--border);background:var(--surface-2);color:var(--text)}button:disabled{opacity:.5;cursor:default}.more-menu{position:relative}.more-menu summary{width:38px;height:38px;display:grid;place-items:center;list-style:none;border-radius:8px;color:var(--muted);cursor:pointer}.menu-popover{position:absolute;z-index:10;right:0;top:42px;width:170px;padding:6px;border:1px solid var(--border);border-radius:10px;background:var(--surface-2);box-shadow:var(--shadow-popover)}.menu-popover button{width:100%;padding:8px 9px;border-radius:7px;background:transparent;color:var(--text-soft);text-align:left;cursor:pointer;font-size:10px}
  .status-card{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:22px;min-height:100px;padding:16px;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}.status-card.running{border-color:var(--accent-border)}.status-card.warning{border-color:#5f5125}.status-card.danger{border-color:#62343a}.status-copy{display:flex;align-items:flex-start;gap:11px}.status-copy strong{font-size:16px}.status-copy p{margin:3px 0 0;color:var(--muted);font-size:11px}.status-dot{width:8px;height:8px;margin-top:7px;border-radius:50%;background:#697078}.status-dot.running{background:var(--accent);box-shadow:0 0 0 4px var(--accent-soft)}.status-dot.transition{background:var(--info)}.status-dot.warning{background:var(--warning)}.status-dot.danger{background:var(--danger)}.live-facts{display:flex;border:1px solid var(--border-soft);border-radius:8px;background:var(--bg-elevated)}.live-facts div{min-width:96px;display:grid;gap:2px;padding:9px 11px;border-left:1px solid var(--border-soft)}.live-facts div:first-child{border-left:0}.live-facts span{color:var(--muted-2);font-size:8px;text-transform:uppercase}.live-facts strong{font-size:11px}
  .fleet-card{margin-top:12px;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}.fleet-card>header{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:11px 12px;border-bottom:1px solid var(--border-soft)}.fleet-card>header>div{display:grid;gap:2px}.fleet-card>header strong{font-size:11px}.fleet-card>header span{color:var(--muted);font-size:9px}.fleet-list{display:grid}.fleet-row{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:9px 12px;border-top:1px solid var(--border-soft)}.fleet-row:first-child{border-top:0}.fleet-name{display:flex;align-items:flex-start;gap:10px}.fleet-name>div{display:grid;gap:1px}.fleet-name strong{font-size:10px}.fleet-name span{color:var(--muted);font-size:9px}.fleet-meta{display:flex;align-items:center;gap:12px;color:var(--muted);font:9px ui-monospace,SFMono-Regular,Consolas,monospace}
  .notice,.attention{margin-top:12px;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}.notice{padding:11px 12px}.notice strong{font-size:11px}.notice p{margin:3px 0 0;color:var(--muted);font-size:10px}.notice.success{border-color:var(--accent-border);background:var(--accent-soft)}.notice.danger{border-color:#62343a;background:var(--danger-bg)}.notice.warning{border-color:#5f5125;background:var(--warning-bg)}.attention summary{display:flex;align-items:center;justify-content:space-between;padding:11px 12px;cursor:pointer}.attention summary>span:first-child{display:grid;gap:2px}.attention small{color:var(--muted);font-size:9px}.issue-list{padding:0 12px 9px;border-top:1px solid var(--border-soft)}.issue-row{display:grid;grid-template-columns:90px 1fr;gap:12px;padding:8px 0;border-bottom:1px solid var(--border-soft);font-size:10px}.issue-row span{color:var(--muted)}
  .modal-backdrop{position:fixed;z-index:100;inset:0;display:grid;place-items:center;padding:24px;background:rgba(4,6,8,.72)}.log-dialog{width:min(780px,100%);max-height:min(680px,calc(100vh - 48px));display:grid;grid-template-rows:auto minmax(0,1fr) auto;gap:12px;padding:18px;border:1px solid var(--border);border-radius:14px;background:var(--surface)}.log-dialog header,.log-dialog footer{display:flex;align-items:center;justify-content:space-between}.log-dialog h2{margin:0;font-size:18px}.log-dialog header p{margin:3px 0 0;color:var(--muted);font-size:10px}.log-dialog pre{min-height:260px;margin:0;padding:12px;overflow:auto;border:1px solid var(--border-soft);border-radius:9px;background:#0b0d0f;color:#c9d1d6;font:11px/1.55 ui-monospace,SFMono-Regular,Consolas,monospace;white-space:pre-wrap}.icon-button{width:32px;height:32px;display:grid;place-items:center;border-radius:8px;background:transparent;color:var(--muted);font-size:20px;cursor:pointer}@media(max-width:760px){.page-head,.status-card{align-items:flex-start;grid-template-columns:1fr;flex-direction:column}.live-facts{width:100%}.issue-row{grid-template-columns:1fr}.fleet-card>header,.fleet-row{align-items:flex-start}.fleet-row{flex-direction:column}.fleet-meta{padding-left:18px}}
</style>