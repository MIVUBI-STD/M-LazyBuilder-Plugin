<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { ManagedWorldSummary } from '../app/bridge/runtimeApi';
  let worlds: ManagedWorldSummary[] = [];
  let error = '';
  async function refresh() {
    try { worlds = await runtimeProduct.worlds.list(); error = ''; }
    catch (e) { worlds = []; error = String(e); }
  }
  onMount(refresh);
</script>
<h1>Worlds</h1>
<p class="subtle">World state comes from World-Manager. Desktop does not own world files.</p>
<div class="actions"><button onclick={refresh}>Refresh</button></div>
{#if error}<p style="color: var(--danger)">{error}</p>{/if}
{#each worlds as world}
  <div class="card" style="margin-top: 12px">
    <strong>{world.displayName}</strong>
    <div class="subtle">{world.kind} · {world.runtimeState} · {world.lifecycle}</div>
  </div>
{/each}
