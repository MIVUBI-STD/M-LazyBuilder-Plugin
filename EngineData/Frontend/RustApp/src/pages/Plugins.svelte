<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { PluginSummary } from '../app/bridge/runtimeApi';
  let plugins: PluginSummary[] = [];
  let error = '';
  async function refresh() {
    try { plugins = await runtimeProduct.plugins.list(); error = ''; }
    catch (e) { plugins = []; error = String(e); }
  }
  onMount(refresh);
</script>
<h1>Plugins</h1>
<p class="subtle">Inventory is owned by the Rust Plugin-Manager runtime.</p>
<div class="actions"><button onclick={refresh}>Refresh</button></div>
{#if error}<p style="color: var(--danger)">{error}</p>{/if}
{#each plugins as plugin}
  <div class="card" style="margin-top: 12px">
    <strong>{plugin.displayName}</strong>
    <div class="subtle">{plugin.version} · {plugin.category} · {plugin.state}</div>
  </div>
{/each}
