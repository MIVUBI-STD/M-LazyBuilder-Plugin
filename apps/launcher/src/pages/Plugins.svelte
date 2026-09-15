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

  const isInvalid = (plugin: PluginSummary) => plugin.id.startsWith('invalid-');
  const canMutate = () => serverState === 'Offline' || serverState === 'Crashed';
  const hasDuplicates = (plugin: PluginSummary) => plugin.mutable && !isInvalid(plugin) && Boolean(plugin.candidateFiles && plugin.candidateFiles.length > 1);
  const extraPlugins = () => plugins.filter((plugin) => plugin.mutable);
  const managedPlugins = () => plugins.filter((plugin) => plugin.managedByLazyBuilder);

  function serverPlugins() {
    const query = search.trim().toLowerCase();
    return extraPlugins().filter((plugin) => !query || `${plugin.displayName} ${plugin.version}`.toLowerCase().includes(query));
  }

  function friendlyError(value: unknown) {
    return String(value).replace(/^Error:\s*/i, '').trim() || 'Plugin operation failed. Try again.';
  }

  async function refresh() {
    try {
      const [nextPlugins, snapshot] = await Promise.all([runtimeProduct.plugins.list(), runtimeProduct.server.snapshot()]);
      plugins = nextPlugins;
      serverState = snapshot.state;
      error = '';
      duplicateSelection = Object.fromEntries(
        plugins.filter((plugin) => plugin.mutable && !isInvalid(plugin) && plugin.candidateFiles?.length).map((plugin) => [plugin.id, plugin.candidateFiles?.[0] ?? ''])
      );
    } catch (e) {
      plugins = [];
      error = friendlyError(e);
    }
  }

  async function run(action: () => Promise<void>) {
    if (busy) return;
    if (!canMutate()) {
      error = serverState === 'Detached' ? 'The server is running externally. Stop it before changing plugins.' : 'Stop the server before changing plugins.';
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
    if (!plugin.mutable) return;
    await run(async () => {
      const jar = await runtimeProduct.plugins.pickJar();
      if (!jar) return;
      applyResult(await runtimeProduct.plugins.update(plugin.id, jar));
    });
  }

  async function togglePlugin(plugin: PluginSummary) {
    if (!plugin.mutable) return;
    await run(async () => {
      await runtimeProduct.plugins.setEnabled(plugin.id, plugin.state !== 'Enabled');
      message = `Plugin ${plugin.state === 'Enabled' ? 'disabled' : 'enabled'}. Restart the server to apply it.`;
    });
  }

  async function removePlugin(plugin: PluginSummary) {
    if (!plugin.mutable) return;
    if (!window.confirm(`Remove ${plugin.displayName}? Its plugin data will be kept.`)) return;
    await run(async () => {
      await runtimeProduct.plugins.remove(plugin.id);
      message = 'Plugin removed. Its data folder was kept.';
    });
  }

  async function removeProblemPlugin(plugin: PluginSummary) {
    if (!plugin.mutable) return;
    const jar = plugin.candidateFiles?.[0];
    if (!jar) {
      error = 'LazyBuilder could not identify the broken JAR safely.';
      return;
    }
    if (!window.confirm(`Remove broken plugin file ${jar}?`)) return;
    await run(async () => {
      await runtimeProduct.plugins.removeProblem(plugin.id, jar);
      message = 'Broken plugin JAR removed safely.';
    });
  }

  async function resolveDuplicates(plugin: PluginSummary) {
    if (!plugin.mutable) return;
    const keep = duplicateSelection[plugin.id];
    if (!keep) return;
    await run(async () => applyResult(await runtimeProduct.plugins.resolveDuplicates(plugin.id, keep)));
  }

  onMount(() => void refresh());
</script>

<section class="plugins-page">
  <header class="page-header">
    <div><h2>Plugins</h2><p>Add only what this build server needs.</p></div>
    <button class="primary" disabled={busy || !canMutate()} onclick={addPlugin}>+ Add plugin</button>
  </header>

  {#if !canMutate()}
    <div class="notice warning"><strong>Stop the server to edit plugins</strong><span>{serverState === 'Detached' ? 'The server is currently running outside LazyBuilder.' : 'Installed plugins stay visible while the server is running.'}</span></div>
  {/if}
  {#if message}<div class="notice success">{message}</div>{/if}
  {#if error}<div class="notice error" role="alert">{error}</div>{/if}

  {#if extraPlugins().length > 4}
    <label class="search-field" aria-label="Search plugins"><span aria-hidden="true">⌕</span><input bind:value={search} placeholder="Search plugins" /></label>
  {/if}

  {#if serverPlugins().length > 0}
    <div class="plugin-list">
      {#each serverPlugins() as plugin}
        <article class="plugin-row" class:has-problem={plugin.state === 'Problem' || isInvalid(plugin)}>
          <div class="plugin-icon">{plugin.displayName.slice(0, 1).toUpperCase()}</div>
          <div class="plugin-main">
            <strong>{plugin.displayName}</strong>
            <span>{plugin.version || 'Unknown version'}</span>

            {#if hasDuplicates(plugin)}
              <div class="problem-card">
                <strong>Multiple plugin files found</strong><span>Choose which JAR LazyBuilder should keep.</span>
                <div class="problem-actions">
                  <select disabled={busy || !canMutate()} value={duplicateSelection[plugin.id] ?? plugin.candidateFiles?.[0]} onchange={(event) => duplicateSelection = { ...duplicateSelection, [plugin.id]: (event.currentTarget as HTMLSelectElement).value }}>
                    {#each plugin.candidateFiles ?? [] as candidate}<option value={candidate}>{candidate}</option>{/each}
                  </select>
                  <button disabled={busy || !canMutate()} onclick={() => resolveDuplicates(plugin)}>Keep selected</button>
                </div>
              </div>
            {:else if isInvalid(plugin)}
              <div class="problem-card danger-card"><strong>Broken plugin file</strong><span>{plugin.candidateFiles?.[0] || 'LazyBuilder could not read this JAR.'}</span></div>
            {:else if plugin.problemDetail}
              <small class="problem-text">{plugin.problemDetail}</small>
            {/if}
          </div>

          <div class="row-tools">
            <span class="state-pill" class:disabled={!isInvalid(plugin) && plugin.state !== 'Enabled'} class:problem={isInvalid(plugin) || plugin.state === 'Problem'}>{isInvalid(plugin) ? 'Needs attention' : plugin.state}</span>
            <details class="row-menu">
              <summary aria-label={`Manage ${plugin.displayName}`} title="Manage plugin">•••</summary>
              <div class="menu-popover">
                {#if !isInvalid(plugin)}
                  {#if plugin.state !== 'Problem'}
                    <button disabled={busy || !canMutate()} onclick={() => togglePlugin(plugin)}>{plugin.state === 'Enabled' ? 'Disable' : 'Enable'}</button>
                    <button disabled={busy || !canMutate()} onclick={() => updatePlugin(plugin)}>Replace plugin file…</button>
                  {/if}
                  <button class="danger" disabled={busy || !canMutate() || hasDuplicates(plugin)} onclick={() => removePlugin(plugin)}>Remove plugin</button>
                {:else}
                  <button class="danger" disabled={busy || !canMutate() || !plugin.candidateFiles?.[0]} onclick={() => removeProblemPlugin(plugin)}>Remove broken file</button>
                {/if}
              </div>
            </details>
          </div>
        </article>
      {/each}
    </div>
  {:else if extraPlugins().length > 0 && search.trim()}
    <section class="search-empty"><strong>No matching plugins</strong><span>Try another name.</span><button onclick={() => (search = '')}>Clear search</button></section>
  {:else if !error}
    <section class="empty-state">
      <div class="empty-icon">+</div><h3>No extra plugins</h3><p>This server can stay simple. Add a plugin only when your builders need one.</p>
      <button class="primary" disabled={busy || !canMutate()} onclick={addPlugin}>Add plugin</button>
    </section>
  {/if}

  {#if managedPlugins().length > 0}
    <details class="system-components">
      <summary>LazyBuilder components</summary><p>Required components are maintained automatically.</p>
      <div class="system-list">
        {#each managedPlugins() as plugin}
          <div><div class="plugin-icon core">L</div><span><strong>{plugin.displayName}</strong><small>{plugin.version} · Required</small></span><span class="state-pill" class:problem={plugin.state === 'Problem'}>{plugin.state === 'Enabled' ? 'Ready' : plugin.state}</span></div>
        {/each}
      </div>
    </details>
  {/if}
</section>

<style>
  .plugins-page{width:min(920px,100%)}
  .page-header{display:flex;justify-content:space-between;align-items:flex-end;gap:20px;margin-bottom:18px}.page-header h2{margin:0;font-size:18px}.page-header p{margin:4px 0 0;color:var(--muted);font-size:12px}
  .primary{min-height:var(--control-height);border:1px solid var(--accent);border-radius:8px;padding:8px 13px;background:var(--accent);color:var(--accent-ink);font-weight:700;cursor:pointer}.primary:hover:not(:disabled){background:var(--accent-hover)}
  .notice{display:grid;gap:2px;margin-bottom:12px;padding:10px 12px;border-radius:8px;font-size:11px}.notice strong{font-size:11px}.notice.success{border:1px solid var(--accent-border);background:var(--accent-soft);color:#b9e5c7}.notice.warning{border:1px solid #655626;background:var(--warning-bg);color:#e3cf8d}.notice.error{border:1px solid #70343a;background:var(--danger-bg);color:#ffd9dc}
  .search-field{display:flex;align-items:center;gap:8px;margin-bottom:10px;min-height:var(--control-height);padding:0 11px;border:1px solid var(--border-soft);border-radius:8px;background:var(--surface);color:var(--muted)}.search-field:focus-within{border-color:var(--accent-border);box-shadow:0 0 0 3px var(--accent-soft)}.search-field input{min-width:0;flex:1;border:0;outline:0;background:transparent;color:var(--text);padding:0}

  .plugin-list{border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}.plugin-row{position:relative;display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:12px;min-height:62px;padding:10px 12px;border-bottom:1px solid var(--border-soft);transition:background 120ms ease}.plugin-row:last-child{border-bottom:0}.plugin-row:hover{background:var(--surface-2)}.plugin-row.has-problem{background:color-mix(in srgb,var(--danger-bg) 48%,var(--surface))}.plugin-icon{width:36px;height:36px;display:grid;place-items:center;border:1px solid var(--border);border-radius:9px;background:var(--surface-2);color:var(--text-soft);font-size:11px;font-weight:800}.plugin-icon.core{border-color:var(--accent-border);background:var(--accent-soft);color:var(--accent)}.plugin-main{min-width:0;display:grid;gap:2px}.plugin-main>strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.plugin-main>span{color:var(--muted);font-size:10px}.problem-text{margin-top:4px;color:#ff9ba3;font-size:10px}
  .state-pill{padding:3px 6px;border-radius:999px;background:#173321;color:#9fdaae;font-size:8px;font-weight:750;white-space:nowrap;text-transform:uppercase;letter-spacing:.03em}.state-pill.disabled{background:var(--surface-3);color:var(--muted)}.state-pill.problem{background:#3a2024;color:#ffafb5}.row-tools{display:flex;align-items:center;gap:5px}.row-menu{position:relative}.row-menu summary{width:34px;height:32px;display:grid;place-items:center;border-radius:8px;color:var(--muted);cursor:pointer;list-style:none;font-weight:800}.row-menu summary::-webkit-details-marker{display:none}.row-menu summary:hover,.row-menu[open] summary{background:var(--surface-2);color:var(--text)}.menu-popover{position:absolute;z-index:20;right:0;top:38px;width:190px;display:grid;gap:2px;padding:6px;border:1px solid var(--border);border-radius:10px;background:var(--surface-2);box-shadow:var(--shadow-popover)}.menu-popover button{width:100%;min-height:32px;padding:7px 9px;border-radius:7px;background:transparent;color:var(--text-soft);text-align:left;cursor:pointer;font-size:10px}.menu-popover button:hover:not(:disabled){background:var(--surface-3);color:var(--text)}.menu-popover button.danger{color:#f1a5aa}
  .problem-card{display:grid;gap:3px;margin-top:7px;padding:8px;border:1px solid #5f5125;border-radius:8px;background:var(--warning-bg)}.problem-card>strong{color:#e5d59d;font-size:10px}.problem-card>span{color:#bdae7b;font-size:9px;overflow-wrap:anywhere}.problem-card.danger-card{border-color:#6c363d;background:var(--danger-bg)}.problem-actions{display:flex;gap:7px;margin-top:3px}.problem-actions select{min-width:0;flex:1;padding:6px 8px}.problem-actions button{border:1px solid var(--border);border-radius:7px;padding:6px 9px;background:var(--surface-2);color:var(--text-soft);cursor:pointer;font-size:10px}

  .empty-state,.search-empty{min-height:220px;display:grid;place-content:center;justify-items:center;text-align:center;border:1px dashed var(--border);border-radius:10px;background:var(--bg-elevated)}.search-empty{min-height:150px;gap:3px;color:var(--muted);font-size:11px}.search-empty strong{color:var(--text);font-size:12px}.search-empty button{margin-top:6px;background:transparent;color:var(--accent);cursor:pointer}.empty-icon{width:40px;height:40px;display:grid;place-items:center;margin-bottom:9px;border-radius:10px;background:var(--surface-2);color:var(--muted);font-size:18px}.empty-state h3{margin:0;font-size:13px}.empty-state p{max-width:390px;margin:5px 0 14px;color:var(--muted);font-size:10px}
  .system-components{margin-top:16px;color:var(--muted)}.system-components>summary{width:max-content;color:var(--muted);font-size:10px;cursor:pointer}.system-components>p{margin:5px 0 8px;color:var(--muted-2);font-size:9px}.system-list{border:1px solid var(--border-soft);border-radius:8px;background:var(--bg-elevated)}.system-list>div{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:9px;padding:8px 9px;border-bottom:1px solid var(--border-soft)}.system-list>div:last-child{border-bottom:0}.system-list>div>span:nth-child(2){min-width:0;display:grid;gap:1px}.system-list strong{color:var(--text-soft);font-size:10px}.system-list small{color:var(--muted-2);font-size:9px}.system-list .plugin-icon{width:28px;height:28px;font-size:9px}
  button:disabled,select:disabled{opacity:.5;cursor:not-allowed}
  @media(max-width:760px){.page-header{align-items:stretch;flex-direction:column}.plugin-row{grid-template-columns:auto minmax(0,1fr)}.row-tools{grid-column:2;justify-content:flex-start}.problem-actions{flex-direction:column}}
</style>
