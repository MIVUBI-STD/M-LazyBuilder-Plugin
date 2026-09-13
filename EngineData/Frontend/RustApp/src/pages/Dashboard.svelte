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

  function friendlyError(value: unknown) {
    return String(value).replace(/^Error:\s*/i, '').trim() || 'Something went wrong. Try again.';
  }

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
    if (state === 'Online') return 'Ready for builders to join.';
    if (state === 'Starting') return 'Starting the server. This usually takes a few seconds.';
    if (state === 'Stopping') return 'Stopping safely. Wait until the server is offline.';
    if (state === 'Restarting') return 'Restarting the server.';
    if (state === 'Detached') return 'The server is running outside this launcher session.';
    if (state === 'Crashed') return 'The previous server process stopped unexpectedly.';
    return 'Start the server when your team is ready to build.';
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
      error = friendlyError(e);
    }
  }

  async function pollRuntime() {
    try {
      await refreshRuntime();
    } catch (e) {
      if (!error) error = friendlyError(e);
    }
  }

  async function action(run: () => Promise<void>) {
    if (busy) return;
    busy = true;
    notice = '';
    error = '';
    try {
      await run();
      await refreshAll();
    } catch (e) {
      const operationError = friendlyError(e);
      try {
        await Promise.all([refreshRuntime(), refreshPreflight()]);
      } catch {
        // Keep the original action error.
      }
      error = operationError;
    } finally {
      busy = false;
    }
  }

  async function stopDetachedProcess() {
    if (busy || snapshot.state !== 'Detached') return;
    if (!window.confirm('Stop the externally running Paper server so LazyBuilder can manage it again?')) return;
    busy = true;
    error = '';
    notice = '';
    try {
      const result = await runtimeProduct.server.recoverDetached();
      notice = result.message;
      await refreshAll();
    } catch (e) {
      error = friendlyError(e);
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

<section class="overview" aria-label={`${serverName} overview`}>
  <div class="section-heading">
    <div>
      <h2>Server status</h2>
      <p>Start, stop and check the server at a glance.</p>
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
      {:else if snapshot.state === 'Restarting'}
        <button class="secondary-action" disabled>Restarting…</button>
      {:else if snapshot.state === 'Detached'}
        <button class="stop-button" disabled={busy} onclick={stopDetachedProcess}>Stop external server</button>
      {/if}
    </div>
  </div>

  <section class="status-card {stateTone(snapshot.state)}">
    <div class="status-summary">
      <span class="status-dot {stateTone(snapshot.state)}"></span>
      <div>
        <strong>{stateLabel(snapshot.state)}</strong>
        <p>{stateDescription(snapshot.state)}</p>
      </div>
    </div>

    <div class="runtime-facts">
      {#if snapshot.state === 'Online'}
        <div><span>CPU</span><strong>{snapshot.cpuLoadPercent.toFixed(1)}%</strong></div>
        <div><span>Memory</span><strong>{gb(snapshot.usedMemoryBytes).toFixed(1)} / {gb(snapshot.maxMemoryBytes).toFixed(1)} GB</strong></div>
      {:else if snapshot.maxMemoryBytes > 0}
        <div><span>Memory limit</span><strong>{gb(snapshot.maxMemoryBytes).toFixed(0)} GB</strong></div>
      {/if}
      {#if preflight.javaVersion}
        <div><span>Java</span><strong>{preflight.javaVersion}</strong></div>
      {/if}
    </div>
  </section>

  {#if error}
    <section class="message-card danger-card" role="alert">
      <div>
        <strong>{category(error)} problem</strong>
        <p>{error}</p>
      </div>
    </section>
  {/if}

  {#if notice}
    <section class="message-card success-card" aria-live="polite">
      <div><strong>Server control</strong><p>{notice}</p></div>
    </section>
  {/if}

  {#if preflight.issues.length > 0 && ['Offline', 'Crashed', 'Detached'].includes(snapshot.state)}
    <section class="attention-card">
      <div class="attention-heading">
        <div>
          <strong>Needs attention before starting</strong>
          <p>Only items that block normal server use are shown here.</p>
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
          <strong>LazyBuilder cannot control the current console</strong>
          <p>Stop the verified external server first. After that, start it here and LazyBuilder will manage it normally.</p>
        </div>
      </div>
      <button class="stop-button" disabled={busy} onclick={stopDetachedProcess}>Stop external server</button>
    </section>
  {/if}
</section>

<style>
  .overview { width: min(920px, 100%); }
  .section-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin-bottom: 14px; }
  .section-heading h2 { margin: 0; color: var(--text); font-size: 18px; }
  .section-heading p { margin: 4px 0 0; color: var(--muted); font-size: 12px; }

  .header-actions { display: flex; align-items: center; gap: 8px; }
  .start-button, .secondary-action, .stop-button { min-height: var(--control-height); border-radius: var(--radius-sm); padding: 8px 14px; font-weight: 700; cursor: pointer; transition: background 120ms ease, border-color 120ms ease, transform 120ms ease; }
  .start-button { border: 1px solid var(--accent); background: var(--accent); color: var(--accent-ink); }
  .start-button:hover:not(:disabled) { background: var(--accent-hover); border-color: var(--accent-hover); }
  .start-button:active:not(:disabled), .secondary-action:active:not(:disabled), .stop-button:active:not(:disabled) { transform: scale(.98); }
  .secondary-action { border: 1px solid var(--border); background: var(--surface-2); color: var(--text); }
  .secondary-action:hover:not(:disabled) { background: var(--surface-3); border-color: var(--border-strong); }
  .stop-button { border: 1px solid #61343a; background: #2b1b1e; color: #ffb7bd; }
  .stop-button:hover:not(:disabled) { background: #382025; }

  .status-card { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 24px; min-height: 108px; padding: 18px; border: 1px solid var(--border-soft); border-radius: var(--radius); background: var(--surface); box-shadow: var(--shadow-card); }
  .status-card.running { border-color: var(--accent-border); background: linear-gradient(105deg, #16221a 0%, var(--surface) 48%); }
  .status-card.warning { border-color: #5f5125; background: linear-gradient(105deg, #252116 0%, var(--surface) 48%); }
  .status-card.danger { border-color: #62343a; background: linear-gradient(105deg, #29191c 0%, var(--surface) 48%); }
  .status-summary { min-width: 0; display: flex; align-items: flex-start; gap: 11px; }
  .status-summary strong { font-size: 17px; }
  .status-summary p { max-width: 480px; margin: 4px 0 0; color: var(--muted); font-size: 12px; }
  .status-dot { width: 9px; height: 9px; flex: 0 0 9px; margin-top: 7px; border-radius: 50%; background: #697078; }
  .status-dot.running { background: var(--accent); box-shadow: 0 0 0 4px var(--accent-soft); }
  .status-dot.transition { background: var(--info); }
  .status-dot.warning { background: var(--warning); }
  .status-dot.danger { background: var(--danger); }

  .runtime-facts { display: flex; align-items: stretch; border: 1px solid var(--border-soft); border-radius: var(--radius-sm); background: var(--bg-elevated); overflow: hidden; }
  .runtime-facts > div { min-width: 100px; display: grid; gap: 3px; padding: 10px 13px; border-left: 1px solid var(--border-soft); }
  .runtime-facts > div:first-child { border-left: 0; }
  .runtime-facts span { color: var(--muted-2); font-size: 10px; text-transform: uppercase; letter-spacing: .05em; }
  .runtime-facts strong { font-size: 13px; white-space: nowrap; }

  .message-card, .attention-card { margin-top: 12px; padding: 13px 14px; border: 1px solid var(--border-soft); border-radius: var(--radius); background: var(--surface); box-shadow: var(--shadow-card); }
  .message-card strong, .attention-card strong { font-size: 13px; }
  .message-card p, .attention-card p { margin: 4px 0 0; color: var(--muted); font-size: 12px; line-height: 1.5; }
  .success-card { border-color: var(--accent-border); background: var(--accent-soft); }
  .success-card p { color: #b9e5c7; }
  .danger-card { border-color: #62343a; background: var(--danger-bg); }
  .danger-card p { color: #e9b6ba; }
  .attention-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; }
  .attention-heading > span { min-width: 24px; height: 24px; display: grid; place-items: center; border-radius: 999px; background: var(--surface-2); color: var(--muted); font-size: 11px; }
  .issue-list { display: grid; margin-top: 12px; border-top: 1px solid var(--border-soft); }
  .issue-row { display: grid; grid-template-columns: 100px 1fr; gap: 14px; padding: 9px 0; border-bottom: 1px solid var(--border-soft); font-size: 11px; }
  .issue-row:last-child { border-bottom: 0; padding-bottom: 0; }
  .issue-row strong { color: var(--text-soft); font-size: 11px; }
  .issue-row span { color: var(--muted); overflow-wrap: anywhere; }
  .attention-card > button { margin-top: 14px; }

  @media (max-width: 820px) {
    .section-heading, .status-card { align-items: flex-start; grid-template-columns: 1fr; }
    .section-heading { flex-direction: column; }
    .runtime-facts { width: 100%; flex-wrap: wrap; }
    .runtime-facts > div { flex: 1; border-left: 0; border-top: 1px solid var(--border-soft); }
    .runtime-facts > div:first-child { border-top: 0; }
    .issue-row { grid-template-columns: 1fr; }
  }
</style>
