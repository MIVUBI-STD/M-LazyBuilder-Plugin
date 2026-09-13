<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { ServerPreflight, ServerProcessMetrics, ServerSnapshot } from '../app/bridge/runtimeApi';

  let snapshot: ServerSnapshot = {
    state: 'Offline',
    health: 'Offline',
    cpuLoadPercent: 0,
    usedMemoryBytes: 0,
    maxMemoryBytes: 0,
    pid: null,
    logPath: ''
  };
  let metrics: ServerProcessMetrics = {
    available: false,
    pid: null,
    cpuPercent: 0,
    processMemoryBytes: 0,
    diskReadBytes: 0,
    diskWriteBytes: 0
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
    if (value.includes('port') || value.includes('address') || value.includes('bind')) return 'Port';
    if (value.includes('permission') || value.includes('denied') || value.includes('filesystem')) return 'Filesystem';
    return 'Runtime';
  }

  const cpuUsage = () => metrics.available ? metrics.cpuPercent : snapshot.cpuLoadPercent;
  const ramUsage = () => metrics.available ? metrics.processMemoryBytes : snapshot.usedMemoryBytes;

  async function refreshRuntime() {
    const [server, processMetrics] = await Promise.all([
      runtimeProduct.server.snapshot(),
      runtimeProduct.server.metrics()
    ]);
    snapshot = server;
    metrics = processMetrics;
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
    const timer = window.setInterval(() => void pollRuntime(), 2000);
    return () => window.clearInterval(timer);
  });

  const gb = (bytes: number) => bytes / 1024 / 1024 / 1024;
</script>

<h1>Dashboard</h1>
<p class="subtle">Essential server health and controls</p>

<div class="cards">
  <div class="card"><div class="label">Server</div><div class="value">{snapshot.state}</div></div>
  <div class="card"><div class="label">Health</div><div class="value">{snapshot.health}</div></div>
  <div class="card"><div class="label">CPU</div><div class="value">{cpuUsage().toFixed(1)}%</div></div>
  <div class="card"><div class="label">RAM</div><div class="value">{gb(ramUsage()).toFixed(1)} / {gb(snapshot.maxMemoryBytes).toFixed(1)} GB</div></div>
</div>

<div class="actions" style="margin-top: 16px">
  <button disabled={busy || !preflight.ready || !['Offline', 'Crashed'].includes(snapshot.state)} onclick={() => action(runtimeProduct.server.start)}>Start Server</button>
  <button disabled={busy || !['Starting', 'Online'].includes(snapshot.state)} onclick={() => action(runtimeProduct.server.stop)}>Stop</button>
  <button disabled={busy || snapshot.state !== 'Online'} onclick={() => action(runtimeProduct.server.restart)}>Restart</button>
</div>

{#if error}
  <div class="card" style="margin-top: 16px">
    <div class="label">{category(error)} Error</div>
    <p style="color: var(--danger)">{error}</p>
  </div>
{/if}

{#if notice}
  <div class="card" style="margin-top: 16px">
    <div class="label">Controller</div>
    <p>{notice}</p>
  </div>
{/if}

{#if preflight.issues.length > 0 && ['Offline', 'Crashed', 'Detached'].includes(snapshot.state)}
  <div class="card" style="margin-top: 16px">
    <div class="label">Needs Attention</div>
    <ul>
      {#each preflight.issues as issue}
        <li><strong>{category(issue)}:</strong> {issue}</li>
      {/each}
    </ul>
  </div>
{/if}

{#if snapshot.state === 'Detached'}
  <div class="card" style="margin-top: 16px">
    <div class="label">Recovery Required</div>
    <p>Paper is still running, but this launcher no longer owns its console handle. Starting another instance is blocked to protect the server.</p>
    <button disabled={busy} onclick={recoverDetached}>Recover Server Process</button>
  </div>
{/if}
