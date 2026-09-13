<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { ServerPreflight, ServerSnapshot } from '../app/bridge/runtimeApi';

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
  let busy = false;

  async function refresh() {
    try {
      [snapshot, preflight] = await Promise.all([
        runtimeProduct.server.snapshot(),
        runtimeProduct.server.preflight()
      ]);
      error = '';
    } catch (e) {
      error = String(e);
    }
  }

  async function action(run: () => Promise<void>) {
    if (busy) return;
    busy = true;
    try {
      await run();
      await refresh();
      error = '';
    } catch (e) {
      error = String(e);
      await refresh();
    } finally {
      busy = false;
    }
  }

  onMount(() => {
    void refresh();
    const timer = window.setInterval(() => void refresh(), 2000);
    return () => window.clearInterval(timer);
  });

  const gb = (bytes: number) => bytes / 1024 / 1024 / 1024;
</script>

<h1>Dashboard</h1>
<p class="subtle">Server launcher, runtime health, and local readiness</p>

<div class="cards">
  <div class="card"><div class="label">Server</div><div class="value">{snapshot.state}</div></div>
  <div class="card"><div class="label">Health</div><div class="value">{snapshot.health}</div></div>
  <div class="card"><div class="label">CPU Load</div><div class="value">{snapshot.cpuLoadPercent.toFixed(1)}%</div></div>
  <div class="card"><div class="label">RAM Usage</div><div class="value">{gb(snapshot.usedMemoryBytes).toFixed(1)} / {gb(snapshot.maxMemoryBytes).toFixed(1)} GB</div></div>
  <div class="card"><div class="label">Launcher</div><div class="value">{preflight.ready ? 'Ready' : 'Needs Attention'}</div></div>
  <div class="card"><div class="label">PID</div><div class="value">{snapshot.pid ?? '—'}</div></div>
</div>

{#if error}<p style="color: var(--danger)">{error}</p>{/if}

{#if preflight.issues.length > 0 && ['Offline', 'Crashed', 'Detached'].includes(snapshot.state)}
  <div class="card">
    <div class="label">Preflight Issues</div>
    <ul>
      {#each preflight.issues as issue}
        <li>{issue}</li>
      {/each}
    </ul>
  </div>
{/if}

<div class="actions">
  <button disabled={busy || !preflight.ready || !['Offline', 'Crashed'].includes(snapshot.state)} onclick={() => action(runtimeProduct.server.start)}>Start Server</button>
  <button disabled={busy || !['Starting', 'Online'].includes(snapshot.state)} onclick={() => action(runtimeProduct.server.stop)}>Stop</button>
  <button disabled={busy || snapshot.state !== 'Online'} onclick={() => action(runtimeProduct.server.restart)}>Restart</button>
</div>

<div class="card" style="margin-top: 16px">
  <div class="label">Controller Diagnostics</div>
  <p><strong>Workspace:</strong> {preflight.workspace || 'Unavailable'}</p>
  <p><strong>Server:</strong> {preflight.serverDirectory || 'Unavailable'}</p>
  <p><strong>Paper:</strong> {preflight.paperJar || 'Unavailable'}</p>
  <p><strong>Worlds:</strong> {preflight.worldsDirectory || 'Unavailable'}</p>
  <p><strong>Java:</strong> {preflight.javaVersion || 'Unavailable'}</p>
  <p class="subtle">{preflight.javaPath}</p>
  <p><strong>Logs:</strong> {snapshot.logPath || preflight.logDirectory || 'Unavailable'}</p>
</div>
