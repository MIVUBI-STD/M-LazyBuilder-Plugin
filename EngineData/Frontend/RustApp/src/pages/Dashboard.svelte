<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { ServerSnapshot } from '../app/bridge/runtimeApi';

  let snapshot: ServerSnapshot = { state: 'Offline', health: 'Offline', cpuLoadPercent: 0, usedMemoryBytes: 0, maxMemoryBytes: 0 };
  let error = '';

  async function refresh() {
    try { snapshot = await runtimeProduct.server.snapshot(); error = ''; }
    catch (e) { error = String(e); }
  }

  async function action(run: () => Promise<void>) {
    try { await run(); await refresh(); error = ''; }
    catch (e) { error = String(e); }
  }

  onMount(refresh);
  const gb = (bytes: number) => bytes / 1024 / 1024 / 1024;
</script>

<h1>Dashboard</h1>
<p class="subtle">Server condition at a glance</p>
<div class="cards">
  <div class="card"><div class="label">Server</div><div class="value">{snapshot.state}</div></div>
  <div class="card"><div class="label">Health</div><div class="value">{snapshot.health}</div></div>
  <div class="card"><div class="label">CPU Load</div><div class="value">{snapshot.cpuLoadPercent.toFixed(1)}%</div></div>
  <div class="card"><div class="label">RAM Usage</div><div class="value">{gb(snapshot.usedMemoryBytes).toFixed(1)} / {gb(snapshot.maxMemoryBytes).toFixed(1)} GB</div></div>
</div>
{#if error}<p style="color: var(--danger)">{error}</p>{/if}
<div class="actions">
  <button onclick={() => action(runtimeProduct.server.start)}>Start Server</button>
  <button onclick={() => action(runtimeProduct.server.stop)}>Stop</button>
  <button onclick={() => action(runtimeProduct.server.restart)}>Restart</button>
</div>
