<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { ServerPreflight, ServerSnapshot } from '../app/bridge/runtimeApi';

  export let serverName = 'Server';

  let snapshot: ServerSnapshot = {
    state: 'Offline',
    health: 'Offline',
    cpuLoadPercent: 0,
    usedMemoryBytes: 0,
    maxMemoryBytes: 0,
    pid: null,
    logPath: ''
  };
  let preflight: ServerPreflight = {
    ready: false,
    workspace: '',
    serverDirectory: '',
    paperJar: '',
    worldsDirectory: '',
    javaPath: '',
    javaVersion: '',
    logDirectory: '',
    issues: []
  };
  let error = '';
  let notice = '';
  let busy = false;

  function category(message: string) {
    const value = message.toLowerCase();
    if (value.includes('java')) return 'Java';
    if (value.includes('paper') || value.includes('jar')) return 'Paper';
    if (value.includes('workspace') || value.includes('directory') || value.includes('path')) return 'Workspace';
    if (value.includes('pid') || value.includes('process') || value.includes('detached')) return 'Process';
    if (value.includes('port') || value.includes('address') || value.includes('bind')) return 'Network';
    if (value.includes('permission') || value.includes('denied') || value.includes('filesystem')) return 'Files';
    return 'Server';
  }

  function stateLabel(state: string) {
    if (state === 'Online') return 'Running';
    if (state === 'Detached') return 'Running externally';
    return state;
  }

  function stateTone(state: string) {
    if (state === 'Online') return 'running';
    if (['Starting', 'Restarting', 'Stopping'].includes(state)) return 'transition';
    if (state === 'Detached') return 'warning';
    if (state === 'Crashed') return 'danger';
    return 'offline';
  }

  function stateDescription(state: string) {
    if (state === 'Online') return 'Your Paper server is online and ready to use.';
    if (state === 'Starting') return 'Paper is starting. This usually takes a few seconds.';
    if (state === 'Stopping') return 'Waiting for Paper to shut down safely.';
    if (state === 'Restarting') return 'The server is restarting.';
    if (state === 'Detached') return 'Paper is running outside the current LazyBuilder session.';
    if (state === 'Crashed') return 'The previous server process stopped unexpectedly.';
    return 'Start the server when you are ready to build.';
  }

  async function refreshRuntime() {
    snapshot = await runtimeProduct.server.snapshot();
  }

  async function refreshPreflight() {
    preflight = await runtimeProduct.server.preflight();
  }

  async function refreshAll() {
    try {
      await Promise.all([refreshRuntime(), refreshPreflight()]);
      error = '';
    } catch (e) {
      error = String(e);
    }
  }

  async function pollRuntime() {
    try {
      await refreshRuntime();
      error = '';
    } catch (e) {
      error = String(e);
    }
  }

  async function action(run: () => Promise<void>) {
    if (busy) return;
    busy = true;
    notice = '';
    try {
      await run();
      await refreshAll();
      error = '';
    } catch (e) {
      error = String(e);
      await refreshAll();
    } finally {
      busy = false;
    }
  }

  async function recoverDetached() {
    if (busy || snapshot.state !== 'Detached') return;
    busy = true;
    error = '';
    notice = '';
    try {
      const result = await runtimeProduct.server.recoverDetached();
      notice = result.message;
      await refreshAll();
    } catch (e) {
      error = String(e);
    } finally {
      busy = false;
    }
  }

  onMount(() => {
    void refreshAll();
    const timer = window.setInterval(() => void pollRuntime(), 5000);
    return () => window.clearInterval(timer);
  });

  const gb = (bytes: number) => bytes / 1024 / 1024 / 1024;
</script>

<section class="overview">
  <header class="page-header">
    <div>
      <p class="eyebrow">Overview</p>
      <h1>{serverName}</h1>
      <div class="status-line">
        <span class="status-dot {stateTone(snapshot.state)}"></span>
        <span>{stateLabel(snapshot.state)}</span>
        {#if preflight.javaVersion}<span class="separator">•</span><span class="muted">Java {preflight.javaVersion}</span>{/if}
      </div>
    </div>

    <div class="header-actions">
      {#if ['Offline', 'Crashed'].includes(snapshot.state)}
        <button class="start-button" disabled={busy || !preflight.ready} onclick={() => action(runtimeProduct.server.start)}>
          {busy ? 'Starting…' : '▶ Start server'}
        </button>
      {:else if snapshot.state === 'Online'}
        <button class="secondary-action" disabled={busy} onclick={() => action(runtimeProduct.server.restart)}>Restart</button>
        <button class="stop-button" disabled={busy} onclick={() => action(runtimeProduct.server.stop)}>Stop</button>
      {:else if snapshot.state === 'Starting'}
        <button class="secondary-action" disabled>Starting…</button>
      {:else if snapshot.state === 'Stopping'}
        <button class="secondary-action" disabled>Stopping…</button>
      {:else if snapshot.state === 'Detached'}
        <button class="secondary-action" disabled={busy} onclick={recoverDetached}>Recover control</button>
      {/if}
    </div>
  </header>

  <section class="hero-card {stateTone(snapshot.state)}">
    <div class="hero-copy">
      <div class="hero-status">
        <span class="large-dot {stateTone(snapshot.state)}"></span>
        <strong>{stateLabel(snapshot.state)}</strong>
      </div>
      <p>{stateDescription(snapshot.state)}</p>
    </div>

    {#if snapshot.state === 'Online'}
      <div class="live-metrics">
        <div>
          <span>CPU</span>
          <strong>{snapshot.cpuLoadPercent.toFixed(1)}%</strong>
        </div>
        <div>
          <span>Memory</span>
          <strong>{gb(snapshot.usedMemoryBytes).toFixed(1)} / {gb(snapshot.maxMemoryBytes).toFixed(1)} GB</strong>
        </div>
        {#if snapshot.pid}
          <div>
            <span>Process</span>
            <strong>PID {snapshot.pid}</strong>
          </div>
        {/if}
      </div>
    {:else if snapshot.maxMemoryBytes > 0}
      <div class="offline-meta">
        <span>Memory limit</span>
        <strong>{gb(snapshot.maxMemoryBytes).toFixed(0)} GB</strong>
      </div>
    {/if}
  </section>

  {#if error}
    <section class="message-card danger-card">
      <div>
        <strong>{category(error)} problem</strong>
        <p>{error}</p>
      </div>
    </section>
  {/if}

  {#if notice}
    <section class="message-card">
      <div><strong>Server control</strong><p>{notice}</p></div>
    </section>
  {/if}

  {#if preflight.issues.length > 0 && ['Offline', 'Crashed', 'Detached'].includes(snapshot.state)}
    <section class="attention-card">
      <div class="attention-heading">
        <div>
          <strong>Needs attention</strong>
          <p>Resolve these items before starting the server.</p>
        </div>
        <span>{preflight.issues.length}</span>
      </div>
      <div class="issue-list">
        {#each preflight.issues as issue}
          <div class="issue-row"><strong>{category(issue)}</strong><span>{issue}</span></div>
        {/each}
      </div>
    </section>
  {/if}

  {#if snapshot.state === 'Detached'}
    <section class="attention-card">
      <div class="attention-heading">
        <div>
          <strong>Server is running externally</strong>
          <p>LazyBuilder will not start another Paper process while this one is active.</p>
        </div>
      </div>
      <button class="secondary-action" disabled={busy} onclick={recoverDetached}>Recover server process</button>
    </section>
  {/if}
</section>

<style>
  .overview { width: min(980px, 100%); }
  .page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin-bottom: 22px; }
  .eyebrow { margin: 0 0 4px; color: var(--muted-2); text-transform: uppercase; letter-spacing: .12em; font-size: 10px; font-weight: 750; }
  h1 { margin: 0; font-size: 30px; line-height: 1.1; letter-spacing: -.035em; }
  .status-line { display: flex; align-items: center; gap: 7px; margin-top: 8px; color: var(--text-soft); font-size: 12px; }
  .muted { color: var(--muted); }
  .separator { color: var(--muted-2); }
  .status-dot, .large-dot { border-radius: 50%; background: #697078; }
  .status-dot { width: 7px; height: 7px; }
  .large-dot { width: 10px; height: 10px; }
  .status-dot.running, .large-dot.running { background: var(--accent); box-shadow: 0 0 0 4px rgba(27, 217, 106, .10); }
  .status-dot.transition, .large-dot.transition { background: #67a9ff; }
  .status-dot.warning, .large-dot.warning { background: var(--warning); }
  .status-dot.danger, .large-dot.danger { background: var(--danger); }

  .header-actions { display: flex; align-items: center; gap: 8px; }
  .start-button, .secondary-action, .stop-button { border-radius: 8px; padding: 9px 14px; font-weight: 700; cursor: pointer; }
  .start-button { border: 1px solid var(--accent); background: var(--accent); color: var(--accent-ink); }
  .start-button:hover:not(:disabled) { background: var(--accent-hover); border-color: var(--accent-hover); }
  .secondary-action { border: 1px solid var(--border); background: var(--surface-2); color: var(--text); }
  .secondary-action:hover:not(:disabled) { background: var(--surface-3); }
  .stop-button { border: 1px solid #61343a; background: #2b1b1e; color: #ffb7bd; }
  .stop-button:hover:not(:disabled) { background: #382025; }

  .hero-card { display: flex; justify-content: space-between; align-items: center; gap: 28px; min-height: 142px; padding: 22px; border: 1px solid var(--border); border-radius: 14px; background: var(--surface); }
  .hero-card.running { border-color: #28533a; background: linear-gradient(110deg, #17241b 0%, var(--surface) 48%); }
  .hero-card.warning { border-color: #5f5125; background: linear-gradient(110deg, #252116 0%, var(--surface) 48%); }
  .hero-card.danger { border-color: #62343a; background: linear-gradient(110deg, #29191c 0%, var(--surface) 48%); }
  .hero-copy { min-width: 0; }
  .hero-status { display: flex; align-items: center; gap: 10px; }
  .hero-status strong { font-size: 19px; letter-spacing: -.02em; }
  .hero-copy p { max-width: 500px; margin: 8px 0 0; color: var(--muted); }

  .live-metrics { display: flex; align-items: stretch; flex-wrap: wrap; border: 1px solid var(--border-soft); border-radius: 10px; background: rgba(10, 12, 14, .35); }
  .live-metrics > div { min-width: 116px; display: grid; gap: 4px; padding: 12px 15px; border-left: 1px solid var(--border-soft); }
  .live-metrics > div:first-child { border-left: 0; }
  .live-metrics span, .offline-meta span { color: var(--muted-2); font-size: 10px; text-transform: uppercase; letter-spacing: .06em; }
  .live-metrics strong { font-size: 14px; white-space: nowrap; }
  .offline-meta { display: grid; gap: 4px; min-width: 120px; padding: 12px 14px; border: 1px solid var(--border-soft); border-radius: 10px; background: rgba(10, 12, 14, .30); }
  .offline-meta strong { font-size: 18px; }

  .message-card, .attention-card { margin-top: 14px; padding: 16px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); }
  .message-card strong, .attention-card strong { font-size: 13px; }
  .message-card p, .attention-card p { margin: 4px 0 0; color: var(--muted); font-size: 12px; line-height: 1.5; }
  .danger-card { border-color: #62343a; background: #241719; }
  .danger-card p { color: #e9b6ba; }
  .attention-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; }
  .attention-heading > span { min-width: 24px; height: 24px; display: grid; place-items: center; border-radius: 999px; background: var(--surface-2); color: var(--muted); font-size: 11px; }
  .issue-list { display: grid; margin-top: 13px; border-top: 1px solid var(--border-soft); }
  .issue-row { display: grid; grid-template-columns: 110px 1fr; gap: 14px; padding: 10px 0; border-bottom: 1px solid var(--border-soft); font-size: 11px; }
  .issue-row:last-child { border-bottom: 0; padding-bottom: 0; }
  .issue-row strong { color: var(--text-soft); font-size: 11px; }
  .issue-row span { color: var(--muted); overflow-wrap: anywhere; }
  .attention-card > button { margin-top: 14px; }

  @media (max-width: 820px) {
    .page-header, .hero-card { align-items: flex-start; flex-direction: column; }
    .live-metrics { width: 100%; }
    .issue-row { grid-template-columns: 1fr; gap: 3px; }
  }
</style>
