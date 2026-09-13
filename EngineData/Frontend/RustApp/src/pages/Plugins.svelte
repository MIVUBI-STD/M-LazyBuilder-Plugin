<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { PluginInstallResult, PluginSummary } from '../app/bridge/runtimeApi';

  let plugins: PluginSummary[] = [];
  let error = '';
  let message = '';
  let busy = false;
  let duplicateSelection: Record<string, string> = {};

  async function refresh() {
    try {
      plugins = await runtimeProduct.plugins.list();
      error = '';
      duplicateSelection = Object.fromEntries(
        plugins
          .filter((plugin) => plugin.candidateFiles?.length)
          .map((plugin) => [plugin.id, plugin.candidateFiles?.[0] ?? ''])
      );
    } catch (e) {
      plugins = [];
      error = String(e);
    }
  }

  async function run(action: () => Promise<void>) {
    if (busy) return;
    busy = true;
    error = '';
    message = '';
    try {
      await action();
      await refresh();
    } catch (e) {
      error = String(e);
    } finally {
      busy = false;
    }
  }

  function applyResult(result: PluginInstallResult) {
    if (!result.success) throw new Error(result.message || 'Plugin operation failed.');
    message = result.message || (result.restartRequired ? 'Plugin changed. Restart required.' : 'Plugin changed.');
  }

  async function addPlugin() {
    await run(async () => {
      const jar = await runtimeProduct.plugins.pickJar();
      if (!jar) return;
      applyResult(await runtimeProduct.plugins.install(jar));
    });
  }

  async function updatePlugin(plugin: PluginSummary) {
    await run(async () => {
      const jar = await runtimeProduct.plugins.pickJar();
      if (!jar) return;
      applyResult(await runtimeProduct.plugins.update(plugin.id, jar));
    });
  }

  async function togglePlugin(plugin: PluginSummary) {
    await run(async () => {
      await runtimeProduct.plugins.setEnabled(plugin.id, plugin.state !== 'Enabled');
      message = `Plugin ${plugin.state === 'Enabled' ? 'disabled' : 'enabled'}. Restart required.`;
    });
  }

  async function removePlugin(plugin: PluginSummary) {
    if (!window.confirm(`Remove ${plugin.displayName}? Plugin data will be preserved.`)) return;
    await run(async () => {
      await runtimeProduct.plugins.remove(plugin.id);
      message = 'Plugin JAR removed. Plugin data was preserved.';
    });
  }

  async function resolveDuplicates(plugin: PluginSummary) {
    const keep = duplicateSelection[plugin.id];
    if (!keep) return;
    await run(async () => applyResult(await runtimeProduct.plugins.resolveDuplicates(plugin.id, keep)));
  }

  onMount(() => void refresh());
</script>

<h1>Plugins</h1>
<p class="subtle">Install and maintain Paper plugins without hot-reload or duplicate ownership.</p>
<div class="actions">
  <button disabled={busy} onclick={addPlugin}>Add Plugin</button>
  <button disabled={busy} onclick={refresh}>Refresh</button>
</div>

{#if message}<p>{message}</p>{/if}
{#if error}<p style="color: var(--danger)">{error}</p>{/if}

{#each plugins as plugin}
  <div class="card" style="margin-top: 12px">
    <div style="display:flex; justify-content:space-between; gap:16px; align-items:flex-start">
      <div>
        <strong>{plugin.displayName}</strong>
        <div class="subtle">{plugin.version} · {plugin.state}</div>
        {#if plugin.problemDetail}<div style="color: var(--danger); margin-top:8px">{plugin.problemDetail}</div>{/if}
      </div>
      <span class="subtle">{plugin.category}</span>
    </div>

    {#if plugin.candidateFiles?.length}
      <div class="actions">
        <select
          disabled={busy}
          value={duplicateSelection[plugin.id] ?? plugin.candidateFiles[0]}
          onchange={(event) => duplicateSelection = { ...duplicateSelection, [plugin.id]: (event.currentTarget as HTMLSelectElement).value }}
        >
          {#each plugin.candidateFiles as candidate}
            <option value={candidate}>{candidate}</option>
          {/each}
        </select>
        <button disabled={busy} onclick={() => resolveDuplicates(plugin)}>Keep Selected JAR</button>
      </div>
    {/if}

    {#if !plugin.id.startsWith('invalid-')}
      <div class="actions">
        <button disabled={busy || plugin.state === 'Problem'} onclick={() => togglePlugin(plugin)}>
          {plugin.state === 'Enabled' ? 'Disable' : 'Enable'}
        </button>
        <button disabled={busy || plugin.state === 'Problem'} onclick={() => updatePlugin(plugin)}>Update</button>
        <button disabled={busy || plugin.state === 'Problem'} onclick={() => removePlugin(plugin)}>Remove</button>
      </div>
    {/if}
  </div>
{/each}

{#if plugins.length === 0 && !error}
  <p class="subtle" style="margin-top:20px">No Paper plugins detected.</p>
{/if}
