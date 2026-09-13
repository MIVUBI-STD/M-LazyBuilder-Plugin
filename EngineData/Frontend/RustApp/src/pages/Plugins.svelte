<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { PluginInstallResult, PluginSummary } from '../app/bridge/runtimeApi';

  let plugins: PluginSummary[] = [];
  let error = '';
  let message = '';
  let busy = false;
  let duplicateSelection: Record<string, string> = {};

  const isCore = (plugin: PluginSummary) => {
    const key = `${plugin.id} ${plugin.displayName}`.toLowerCase();
    return key.includes('world-manager') || key.includes('world manager') || key.includes('utilities-manager') || key.includes('utilities manager');
  };

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
      message = 'Plugin removed. Its data folder was preserved.';
    });
  }

  async function resolveDuplicates(plugin: PluginSummary) {
    const keep = duplicateSelection[plugin.id];
    if (!keep) return;
    await run(async () => applyResult(await runtimeProduct.plugins.resolveDuplicates(plugin.id, keep)));
  }

  onMount(() => void refresh());
</script>

<section class="plugins-page">
  <header class="page-header">
    <div>
      <p class="eyebrow">Server</p>
      <h1>Plugins</h1>
      <p>Install and manage Paper plugins for this server.</p>
    </div>
    <button class="primary" disabled={busy} onclick={addPlugin}>+ Add plugin</button>
  </header>

  {#if message}<div class="notice success">{message}</div>{/if}
  {#if error}<div class="notice error">{error}</div>{/if}

  {#if plugins.some(isCore)}
    <section class="section-block">
      <div class="section-heading">
        <div><h2>LazyBuilder components</h2><p>Required components are maintained automatically.</p></div>
      </div>
      <div class="plugin-list core-list">
        {#each plugins.filter(isCore) as plugin}
          <div class="plugin-row">
            <div class="plugin-icon core">L</div>
            <div class="plugin-main">
              <strong>{plugin.displayName}</strong>
              <span>{plugin.version} · {plugin.category}</span>
              {#if plugin.problemDetail}<small class="problem">{plugin.problemDetail}</small>{/if}
            </div>
            <span class="state-pill" class:problem={plugin.state === 'Problem'}>{plugin.state === 'Enabled' ? 'Ready' : plugin.state}</span>
          </div>
        {/each}
      </div>
    </section>
  {/if}

  <section class="section-block">
    <div class="section-heading">
      <div><h2>Server plugins</h2><p>{plugins.filter((plugin) => !isCore(plugin)).length} installed</p></div>
      <button class="refresh" disabled={busy} onclick={refresh}>Refresh</button>
    </div>

    {#if plugins.filter((plugin) => !isCore(plugin)).length > 0}
      <div class="plugin-list">
        {#each plugins.filter((plugin) => !isCore(plugin)) as plugin}
          <div class="plugin-row expanded" class:has-problem={plugin.state === 'Problem'}>
            <div class="plugin-icon">{plugin.displayName.slice(0, 1).toUpperCase()}</div>
            <div class="plugin-main">
              <strong>{plugin.displayName}</strong>
              <span>{plugin.version} · {plugin.category}</span>
              {#if plugin.problemDetail}<small class="problem">{plugin.problemDetail}</small>{/if}

              {#if plugin.candidateFiles?.length}
                <div class="duplicate-box">
                  <span>Multiple JARs detected</span>
                  <div>
                    <select
                      disabled={busy}
                      value={duplicateSelection[plugin.id] ?? plugin.candidateFiles[0]}
                      onchange={(event) => duplicateSelection = { ...duplicateSelection, [plugin.id]: (event.currentTarget as HTMLSelectElement).value }}
                    >
                      {#each plugin.candidateFiles as candidate}<option value={candidate}>{candidate}</option>{/each}
                    </select>
                    <button disabled={busy} onclick={() => resolveDuplicates(plugin)}>Keep selected</button>
                  </div>
                </div>
              {/if}
            </div>

            {#if !plugin.id.startsWith('invalid-')}
              <div class="row-actions">
                <span class="state-pill" class:disabled={plugin.state !== 'Enabled'} class:problem={plugin.state === 'Problem'}>{plugin.state}</span>
                <button disabled={busy || plugin.state === 'Problem'} onclick={() => togglePlugin(plugin)}>{plugin.state === 'Enabled' ? 'Disable' : 'Enable'}</button>
                <button disabled={busy || plugin.state === 'Problem'} onclick={() => updatePlugin(plugin)}>Update</button>
                <button class="danger" disabled={busy || plugin.state === 'Problem'} onclick={() => removePlugin(plugin)}>Remove</button>
              </div>
            {:else}
              <span class="state-pill problem">Problem</span>
            {/if}
          </div>
        {/each}
      </div>
    {:else if !error}
      <div class="empty-state">
        <div class="empty-icon">+</div>
        <strong>No server plugins installed</strong>
        <p>Add a Paper plugin JAR to get started.</p>
        <button class="primary" disabled={busy} onclick={addPlugin}>Add plugin</button>
      </div>
    {/if}
  </section>
</section>

<style>
  .plugins-page { width: min(980px, 100%); }
  .page-header { display: flex; justify-content: space-between; align-items: flex-end; gap: 20px; margin-bottom: 22px; }
  .eyebrow { margin: 0 0 4px; color: var(--muted-2); text-transform: uppercase; letter-spacing: .12em; font-size: 10px; font-weight: 750; }
  h1 { margin: 0; font-size: 30px; line-height: 1.1; letter-spacing: -.035em; }
  .page-header p:last-child, .section-heading p { margin: 6px 0 0; color: var(--muted); font-size: 12px; }
  .primary { border: 1px solid var(--accent); border-radius: 8px; padding: 9px 13px; background: var(--accent); color: var(--accent-ink); font-weight: 700; cursor: pointer; }
  .primary:hover:not(:disabled) { background: var(--accent-hover); border-color: var(--accent-hover); }

  .notice { margin-bottom: 12px; padding: 10px 12px; border-radius: 9px; font-size: 12px; }
  .notice.success { border: 1px solid #28583a; background: #14241a; color: #b9e5c7; }
  .notice.error { border: 1px solid #70343a; background: var(--danger-bg); color: #ffd9dc; }
  .section-block { margin-top: 22px; }
  .section-heading { display: flex; justify-content: space-between; align-items: end; gap: 18px; margin-bottom: 9px; }
  .section-heading h2 { margin: 0; font-size: 14px; }
  .refresh { border: 0; background: transparent; color: var(--muted); padding: 6px 8px; cursor: pointer; font-size: 11px; }
  .refresh:hover { color: var(--text); }

  .plugin-list { overflow: hidden; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); }
  .plugin-row { display: flex; align-items: center; gap: 12px; min-height: 66px; padding: 12px 14px; border-bottom: 1px solid var(--border-soft); }
  .plugin-row:last-child { border-bottom: 0; }
  .plugin-row.has-problem { background: #211719; }
  .plugin-icon { width: 38px; height: 38px; flex: 0 0 38px; display: grid; place-items: center; border: 1px solid #3a4147; border-radius: 9px; background: #24292e; color: var(--text-soft); font-weight: 800; }
  .plugin-icon.core { border-color: #28583a; background: #16301f; color: var(--accent); }
  .plugin-main { min-width: 0; display: grid; flex: 1; gap: 2px; }
  .plugin-main strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
  .plugin-main > span { color: var(--muted); font-size: 11px; }
  .problem { margin-top: 4px; color: #ff9ba3; font-size: 11px; }
  .state-pill { flex: 0 0 auto; padding: 4px 7px; border-radius: 999px; background: #173321; color: #9fdaae; font-size: 10px; font-weight: 700; }
  .state-pill.disabled { background: #292d31; color: #9ba1a7; }
  .state-pill.problem { background: #3a2024; color: #ffafb5; }

  .row-actions { display: flex; align-items: center; gap: 6px; }
  .row-actions button, .duplicate-box button { border: 1px solid var(--border); border-radius: 7px; padding: 7px 9px; background: var(--surface-2); color: var(--text-soft); cursor: pointer; font-size: 11px; }
  .row-actions button:hover:not(:disabled), .duplicate-box button:hover:not(:disabled) { background: var(--surface-3); color: var(--text); }
  .row-actions button.danger { color: #f1a5aa; }

  .duplicate-box { display: grid; gap: 6px; margin-top: 8px; padding: 9px; border: 1px solid #5f5125; border-radius: 8px; background: #252116; }
  .duplicate-box > span { color: #ddca8b; font-size: 10px; font-weight: 700; }
  .duplicate-box > div { display: flex; gap: 7px; }
  .duplicate-box select { min-width: 0; flex: 1; border: 1px solid var(--border); border-radius: 7px; padding: 7px 8px; background: #111315; color: var(--text); font-size: 11px; }

  .empty-state { min-height: 260px; display: grid; place-content: center; justify-items: center; text-align: center; border: 1px dashed var(--border); border-radius: 12px; }
  .empty-icon { width: 42px; height: 42px; display: grid; place-items: center; margin-bottom: 10px; border-radius: 11px; background: var(--surface-2); color: var(--muted); font-size: 20px; }
  .empty-state strong { font-size: 14px; }
  .empty-state p { margin: 5px 0 14px; color: var(--muted); font-size: 11px; }

  @media (max-width: 820px) {
    .plugin-row.expanded { align-items: flex-start; flex-wrap: wrap; }
    .row-actions { width: 100%; padding-left: 50px; flex-wrap: wrap; }
  }
</style>
