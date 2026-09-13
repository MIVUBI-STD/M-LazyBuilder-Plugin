<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { PluginInstallResult, PluginSummary } from '../app/bridge/runtimeApi';

  let plugins: PluginSummary[] = [];
  let search = '';
  let error = '';
  let message = '';
  let busy = false;
  let serverState = 'Offline';
  let duplicateSelection: Record<string, string> = {};

  const isCore = (plugin: PluginSummary) => {
    const key = `${plugin.id} ${plugin.displayName}`.toLowerCase();
    return key.includes('world-manager') || key.includes('world manager') || key.includes('utilities-manager') || key.includes('utilities manager');
  };

  const isInvalid = (plugin: PluginSummary) => plugin.id.startsWith('invalid-');
  const canMutate = () => serverState === 'Offline' || serverState === 'Crashed';
  const hasDuplicates = (plugin: PluginSummary) => !isInvalid(plugin) && Boolean(plugin.candidateFiles && plugin.candidateFiles.length > 1);

  function serverPlugins() {
    const query = search.trim().toLowerCase();
    return plugins
      .filter((plugin) => !isCore(plugin))
      .filter((plugin) => !query || `${plugin.displayName} ${plugin.category} ${plugin.version}`.toLowerCase().includes(query));
  }

  function friendlyError(value: unknown) {
    return String(value).replace(/^Error:\s*/i, '').trim() || 'Plugin operation failed. Try again.';
  }

  async function refresh() {
    try {
      const [nextPlugins, snapshot] = await Promise.all([
        runtimeProduct.plugins.list(),
        runtimeProduct.server.snapshot()
      ]);
      plugins = nextPlugins;
      serverState = snapshot.state;
      error = '';
      duplicateSelection = Object.fromEntries(
        plugins
          .filter((plugin) => !isInvalid(plugin) && plugin.candidateFiles?.length)
          .map((plugin) => [plugin.id, plugin.candidateFiles?.[0] ?? ''])
      );
    } catch (e) {
      plugins = [];
      error = friendlyError(e);
    }
  }

  async function run(action: () => Promise<void>) {
    if (busy) return;
    if (!canMutate()) {
      error = serverState === 'Detached'
        ? 'The server is running externally. Stop it before changing plugin files.'
        : `Stop the server before changing plugins. Current state: ${serverState}.`;
      return;
    }
    busy = true;
    error = '';
    message = '';
    try {
      await action();
      await refresh();
    } catch (e) {
      error = friendlyError(e);
    } finally {
      busy = false;
    }
  }

  function applyResult(result: PluginInstallResult) {
    if (!result.success) throw new Error(result.message || 'Plugin operation failed.');
    message = result.message || (result.restartRequired ? 'Plugin changed. Restart the server to apply it.' : 'Plugin changed.');
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
      message = `Plugin ${plugin.state === 'Enabled' ? 'disabled' : 'enabled'}. Restart the server to apply it.`;
    });
  }

  async function removePlugin(plugin: PluginSummary) {
    if (!window.confirm(`Remove ${plugin.displayName}? Its plugin data will be kept.`)) return;
    await run(async () => {
      await runtimeProduct.plugins.remove(plugin.id);
      message = 'Plugin removed. Its data folder was kept.';
    });
  }

  async function removeProblemPlugin(plugin: PluginSummary) {
    const jar = plugin.candidateFiles?.[0];
    if (!jar) {
      error = 'LazyBuilder could not identify the broken JAR safely.';
      return;
    }
    if (!window.confirm(`Remove broken plugin file ${jar}? A rollback copy will be kept.`)) return;
    await run(async () => {
      await runtimeProduct.plugins.removeProblem(plugin.id, jar);
      message = 'Broken plugin JAR removed. A rollback copy was kept.';
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
      <h2>Plugins</h2>
      <p>Add and manage Paper plugins without touching the server folder manually.</p>
    </div>
    <button class="primary" disabled={busy || !canMutate()} onclick={addPlugin}>+ Add plugin</button>
  </header>

  {#if !canMutate()}
    <div class="notice warning">
      {serverState === 'Detached'
        ? 'Plugin changes are locked because the server is running externally. Stop it first.'
        : `Plugin changes are locked while the server is ${serverState.toLowerCase()}. Stop the server first.`}
    </div>
  {/if}
  {#if message}<div class="notice success">{message}</div>{/if}
  {#if error}<div class="notice error" role="alert">{error}</div>{/if}

  {#if plugins.some(isCore)}
    <section class="section-block">
      <div class="section-heading">
        <div><h3>LazyBuilder components</h3><p>Required components are maintained automatically.</p></div>
      </div>
      <div class="plugin-list core-list">
        {#each plugins.filter(isCore) as plugin}
          <div class="plugin-row">
            <div class="plugin-icon core">L</div>
            <div class="plugin-main">
              <strong>{plugin.displayName}</strong>
              <span>{plugin.version} · Required</span>
              {#if plugin.problemDetail}<small class="problem">{plugin.problemDetail}</small>{/if}
            </div>
            <span class="state-pill" class:problem={plugin.state === 'Problem'}>{plugin.state === 'Enabled' ? 'Ready' : plugin.state}</span>
          </div>
        {/each}
      </div>
    </section>
  {/if}

  <section class="section-block">
    <div class="section-heading plugin-section-heading">
      <div><h3>Server plugins</h3><p>{plugins.filter((plugin) => !isCore(plugin)).length} installed</p></div>
      {#if plugins.filter((plugin) => !isCore(plugin)).length > 0}
        <label class="search-field" aria-label="Search plugins">
          <span aria-hidden="true">⌕</span>
          <input bind:value={search} placeholder="Search plugins" />
        </label>
      {/if}
    </div>

    {#if serverPlugins().length > 0}
      <div class="plugin-list">
        {#each serverPlugins() as plugin}
          <div class="plugin-row expanded" class:has-problem={plugin.state === 'Problem'}>
            <div class="plugin-icon">{plugin.displayName.slice(0, 1).toUpperCase()}</div>
            <div class="plugin-main">
              <strong>{plugin.displayName}</strong>
              <span>{plugin.version} · {plugin.category}</span>
              {#if plugin.problemDetail}<small class="problem">{plugin.problemDetail}</small>{/if}

              {#if hasDuplicates(plugin)}
                <div class="duplicate-box">
                  <strong>Multiple JARs detected</strong>
                  <span>Choose the JAR you want LazyBuilder to keep.</span>
                  <div>
                    <select
                      disabled={busy || !canMutate()}
                      value={duplicateSelection[plugin.id] ?? plugin.candidateFiles?.[0]}
                      onchange={(event) => duplicateSelection = { ...duplicateSelection, [plugin.id]: (event.currentTarget as HTMLSelectElement).value }}
                    >
                      {#each plugin.candidateFiles ?? [] as candidate}<option value={candidate}>{candidate}</option>{/each}
                    </select>
                    <button disabled={busy || !canMutate()} onclick={() => resolveDuplicates(plugin)}>Keep selected</button>
                  </div>
                </div>
              {:else if isInvalid(plugin) && plugin.candidateFiles?.[0]}
                <div class="problem-file">Broken JAR: {plugin.candidateFiles[0]}</div>
              {/if}
            </div>

            <div class="row-tools">
              <span class="state-pill" class:disabled={!isInvalid(plugin) && plugin.state !== 'Enabled'} class:problem={isInvalid(plugin) || plugin.state === 'Problem'}>
                {isInvalid(plugin) ? 'Broken JAR' : plugin.state}
              </span>
              <details class="row-menu">
                <summary aria-label={`Actions for ${plugin.displayName}`} title="Plugin actions">•••</summary>
                <div class="menu-popover">
                  {#if !isInvalid(plugin)}
                    {#if plugin.state !== 'Problem'}
                      <button disabled={busy || !canMutate()} onclick={() => togglePlugin(plugin)}>{plugin.state === 'Enabled' ? 'Disable plugin' : 'Enable plugin'}</button>
                      <button disabled={busy || !canMutate()} onclick={() => updatePlugin(plugin)}>Replace JAR…</button>
                    {/if}
                    <button class="danger" disabled={busy || !canMutate() || hasDuplicates(plugin)} onclick={() => removePlugin(plugin)}>Remove plugin</button>
                  {:else}
                    <button class="danger" disabled={busy || !canMutate() || !plugin.candidateFiles?.[0]} onclick={() => removeProblemPlugin(plugin)}>Remove broken JAR</button>
                  {/if}
                </div>
              </details>
            </div>
          </div>
        {/each}
      </div>
    {:else if plugins.filter((plugin) => !isCore(plugin)).length > 0 && search.trim()}
      <div class="search-empty"><strong>No plugins found</strong><span>Try a different name.</span></div>
    {:else if !error}
      <div class="empty-state">
        <div class="empty-icon">+</div>
        <strong>No server plugins installed</strong>
        <p>Add a Paper plugin JAR when your build server needs one.</p>
        <button class="primary" disabled={busy || !canMutate()} onclick={addPlugin}>Add plugin</button>
      </div>
    {/if}
  </section>
</section>

<style>
  .plugins-page { width: min(980px, 100%); }
  .page-header { display: flex; justify-content: space-between; align-items: flex-end; gap: 20px; margin-bottom: 18px; }
  .page-header h2 { margin: 0; font-size: 18px; }
  .page-header p, .section-heading p { margin: 5px 0 0; color: var(--muted); font-size: 12px; }
  .primary { min-height: 40px; border: 1px solid var(--accent); border-radius: var(--radius-sm); padding: 8px 13px; background: var(--accent); color: var(--accent-ink); font-weight: 700; cursor: pointer; }
  .primary:hover:not(:disabled) { background: var(--accent-hover); border-color: var(--accent-hover); }

  .notice { margin-bottom: 12px; padding: 10px 12px; border-radius: var(--radius-sm); font-size: 12px; }
  .notice.success { border: 1px solid var(--accent-border); background: var(--accent-soft); color: #b9e5c7; }
  .notice.warning { border: 1px solid #655626; background: var(--warning-bg); color: #e3cf8d; }
  .notice.error { border: 1px solid #70343a; background: var(--danger-bg); color: #ffd9dc; }

  .section-block { margin-top: 20px; }
  .section-heading { display: flex; justify-content: space-between; align-items: end; gap: 18px; margin-bottom: 9px; }
  .section-heading h3 { margin: 0; font-size: 13px; }
  .plugin-section-heading { align-items: center; }

  .search-field { width: min(320px, 48%); min-height: 36px; display: flex; align-items: center; gap: 7px; padding: 0 10px; border-radius: var(--radius-sm); background: var(--surface-2); color: var(--muted); box-shadow: var(--shadow-inset); }
  .search-field input { min-width: 0; flex: 1; border: 0; outline: 0; background: transparent; color: var(--text); padding: 0; }

  .plugin-list { overflow: visible; border: 1px solid var(--border-soft); border-radius: var(--radius); background: var(--surface); box-shadow: var(--shadow-card); }
  .plugin-row { position: relative; display: flex; align-items: center; gap: 12px; min-height: 66px; padding: 12px 14px; border-bottom: 1px solid var(--border-soft); }
  .plugin-row:last-child { border-bottom: 0; }
  .plugin-row.has-problem { background: #211719; }
  .plugin-icon { width: 38px; height: 38px; flex: 0 0 38px; display: grid; place-items: center; border: 1px solid #3a4147; border-radius: 9px; background: #24292e; color: var(--text-soft); font-weight: 800; }
  .plugin-icon.core { border-color: var(--accent-border); background: #16301f; color: var(--accent); }
  .plugin-main { min-width: 0; display: grid; flex: 1; gap: 2px; }
  .plugin-main > strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
  .plugin-main > span { color: var(--muted); font-size: 11px; }
  .problem { margin-top: 4px; color: #ff9ba3; font-size: 11px; }
  .problem-file { margin-top: 6px; color: var(--muted); font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 10px; }

  .state-pill { flex: 0 0 auto; padding: 4px 7px; border-radius: 999px; background: #173321; color: #9fdaae; font-size: 10px; font-weight: 700; }
  .state-pill.disabled { background: #292d31; color: #9ba1a7; }
  .state-pill.problem { background: #3a2024; color: #ffafb5; }

  .row-tools { display: flex; align-items: center; gap: 7px; }
  .row-menu { position: relative; }
  .row-menu summary { width: 34px; height: 34px; display: grid; place-items: center; border-radius: var(--radius-sm); color: var(--muted); cursor: pointer; list-style: none; font-weight: 800; letter-spacing: .08em; }
  .row-menu summary::-webkit-details-marker { display: none; }
  .row-menu summary:hover { background: var(--surface-2); color: var(--text); }
  .menu-popover { position: absolute; z-index: 8; right: 0; top: 40px; width: 180px; display: grid; gap: 3px; padding: 6px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--surface-2); box-shadow: var(--shadow-popover); }
  .menu-popover button { width: 100%; min-height: 34px; padding: 7px 9px; border-radius: 7px; background: transparent; color: var(--text-soft); text-align: left; cursor: pointer; font-size: 11px; }
  .menu-popover button:hover:not(:disabled) { background: var(--surface-3); color: var(--text); }
  .menu-popover button.danger { color: #f1a5aa; }

  .duplicate-box { display: grid; gap: 6px; margin-top: 8px; padding: 9px; border: 1px solid #5f5125; border-radius: var(--radius-sm); background: #252116; }
  .duplicate-box > strong { color: #e5d59d; font-size: 10px; }
  .duplicate-box > span { color: #bdae7b; font-size: 10px; }
  .duplicate-box > div { display: flex; gap: 7px; }
  .duplicate-box select { min-width: 0; flex: 1; border: 1px solid var(--border); border-radius: 7px; padding: 7px 8px; background: #111315; color: var(--text); font-size: 11px; }
  .duplicate-box button { border: 1px solid var(--border); border-radius: 7px; padding: 7px 9px; background: var(--surface-2); color: var(--text-soft); cursor: pointer; font-size: 11px; }

  .empty-state, .search-empty { min-height: 240px; display: grid; place-content: center; justify-items: center; text-align: center; border: 1px dashed var(--border); border-radius: var(--radius); }
  .search-empty { min-height: 160px; gap: 3px; color: var(--muted); font-size: 11px; }
  .search-empty strong { color: var(--text-soft); font-size: 13px; }
  .empty-icon { width: 42px; height: 42px; display: grid; place-items: center; margin-bottom: 10px; border-radius: 11px; background: var(--surface-2); color: var(--muted); font-size: 20px; }
  .empty-state strong { font-size: 14px; }
  .empty-state p { margin: 5px 0 14px; color: var(--muted); font-size: 11px; }

  button:disabled, select:disabled { opacity: .5; cursor: not-allowed; }

  @media (max-width: 820px) {
    .page-header, .plugin-section-heading { align-items: stretch; flex-direction: column; }
    .search-field { width: 100%; }
    .plugin-row.expanded { align-items: flex-start; flex-wrap: wrap; }
    .row-tools { margin-left: auto; }
  }
</style>
