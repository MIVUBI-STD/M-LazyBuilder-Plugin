<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { ManagedWorldSummary } from '../app/bridge/runtimeApi';

  let worlds: ManagedWorldSummary[] = [];
  let error = '';
  let connectionStatus = 'Server offline';
  let busy = false;

  async function refresh() {
    if (busy) return;
    busy = true;
    try {
      const server = await runtimeProduct.server.snapshot();
      if (server.state !== 'Online') {
        worlds = [];
        error = '';
        connectionStatus = 'Start the server to manage worlds';
        return;
      }

      worlds = await runtimeProduct.worlds.list();
      worlds = [...worlds].sort((left, right) =>
        left.lifecycle.localeCompare(right.lifecycle) || left.displayName.localeCompare(right.displayName)
      );
      connectionStatus = `World-Manager ready · ${worlds.length} world${worlds.length === 1 ? '' : 's'}`;
      error = '';
    } catch (e) {
      worlds = [];
      connectionStatus = 'World-Manager unavailable';
      error = String(e);
    } finally {
      busy = false;
    }
  }

  onMount(() => {
    void refresh();
    const timer = window.setInterval(() => void refresh(), 3000);
    return () => window.clearInterval(timer);
  });
</script>

<h1>Worlds</h1>
<p class="subtle">World state comes from World-Manager. Desktop does not own world files.</p>
<p class="subtle">{connectionStatus}</p>
<div class="actions"><button disabled={busy} onclick={refresh}>Refresh</button></div>
{#if error}<p style="color: var(--danger)">{error}</p>{/if}
{#each worlds as world}
  <div class="card" style="margin-top: 12px">
    <strong>{world.displayName}</strong>
    <div class="subtle">{world.kind} · {world.runtimeState} · {world.lifecycle}</div>
    <div class="subtle">Default mode: {world.defaultGameMode} · Auto load: {world.autoLoad ? 'On' : 'Off'}</div>
  </div>
{/each}
{#if worlds.length === 0 && !error && connectionStatus.startsWith('World-Manager ready')}
  <p class="subtle" style="margin-top:20px">No managed worlds detected.</p>
{/if}
