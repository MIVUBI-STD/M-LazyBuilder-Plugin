<script lang="ts">
  import { onMount } from 'svelte';
  import RuntimeErrorNotice from '../components/RuntimeErrorNotice.svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import { detailsMenu } from '../app/detailsMenu';
  import { dialogFocus } from '../app/dialogFocus';
  import { presentRuntimeError } from '../app/runtimeErrorPresentation';
  import type { RuntimeErrorPresentation } from '../app/runtimeErrorPresentation';
  import type { RecoveryAction } from '../app/bridge/errors';
  import type { PluginInstallResult, PluginSummary, ServerState } from '../app/bridge/runtimeApi';

  let plugins: PluginSummary[] = [];
  let search = '';
  let error: RuntimeErrorPresentation | null = null;
  let message = '';
  let busy = false;
  let serverState: ServerState = 'Offline';
  let duplicateSelection: Record<string, string> = {};
  let extraPluginList: PluginSummary[] = [];
  let managedPluginList: PluginSummary[] = [];
  let visiblePluginList: PluginSummary[] = [];
  let canMutatePlugins = true;
  let removeCandidate: PluginSummary | null = null;
  let brokenFileCandidate: PluginSummary | null = null;

  const isInvalid = (plugin: PluginSummary) => plugin.id.startsWith('invalid-');
  const hasDuplicates = (plugin: PluginSummary) => plugin.mutable && !isInvalid(plugin) && Boolean(plugin.candidateFiles && plugin.candidateFiles.length > 1);

  $: canMutatePlugins = serverState === 'Offline' || serverState === 'Crashed';
  $: extraPluginList = plugins.filter((plugin) => plugin.mutable);
  $: managedPluginList = plugins.filter((plugin) => plugin.managedByLazyBuilder);
  $: {
    const query = search.trim().toLowerCase();
    visiblePluginList = extraPluginList.filter((plugin) => !query || `${plugin.displayName} ${plugin.version}`.toLowerCase().includes(query));
  }

  function localError(code: string, message: string, action: RecoveryAction | null): RuntimeErrorPresentation {
    return { code, message, details: '', recoverable: action !== null, action, correlationId: '' };
  }

  function closeConfirmation() {
    if (busy) return;
    removeCandidate = null;
    brokenFileCandidate = null;
  }

  async function refresh() {
    try {
      const [nextPlugins, snapshot] = await Promise.all([runtimeProduct.plugins.list(), runtimeProduct.server.snapshot()]);
      plugins = nextPlugins;
      serverState = snapshot.state;
      error = null;
      duplicateSelection = Object.fromEntries(
        nextPlugins.filter((plugin) => plugin.mutable && !isInvalid(plugin) && plugin.candidateFiles?.length).map((plugin) => [plugin.id, plugin.candidateFiles?.[0] ?? ''])
      );
    } catch (value) {
      plugins = [];
      error = presentRuntimeError(value, 'Could not inspect installed plugins.');
    }
  }

  async function run(action: () => Promise<void>) {
    if (busy) return;
    if (!canMutatePlugins) {
      error = localError(
        'SERVER_BUSY',
        serverState === 'Detached'
          ? 'The server is running externally. Stop it before changing plugins.'
          : 'Stop the server before changing plugins.',
        'STOP_SERVER'
      );
      return;
    }
    busy = true;
    error = null;
    message = '';
    try {
      await action();
      await refresh();
    } catch (value) {
      error = presentRuntimeError(value, 'Plugin operation failed. Try again.');
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

  async function removePlugin() {
    const plugin = removeCandidate;
    if (!plugin?.mutable) return;
    await run(async () => {
      await runtimeProduct.plugins.remove(plugin.id);
      removeCandidate = null;
      message = 'Plugin removed. Its data folder was kept.';
    });
  }

  async function removeProblemPlugin() {
    const plugin = brokenFileCandidate;
    if (!plugin?.mutable) return;
    const jar = plugin.candidateFiles?.[0];
    if (!jar) {
      brokenFileCandidate = null;
      error = localError(
        'PLUGIN_FILE_AMBIGUOUS',
        'LazyBuilder could not identify the broken plugin file safely.',
        'REVIEW_PLUGINS'
      );
      return;
    }
    await run(async () => {
      await runtimeProduct.plugins.removeProblem(plugin.id, jar);
      brokenFileCandidate = null;
      message = 'Broken plugin file removed safely.';
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
    <button class="primary" disabled={busy || !canMutatePlugins} onclick={addPlugin}>+ Add plugin</button>
  </header>

  {#if !canMutatePlugins}
    <div class="notice warning"><strong>Stop the server to edit plugins</strong><span>{serverState === 'Detached' ? 'The server is currently running outside LazyBuilder.' : 'Installed plugins stay visible while the server is running.'}</span></div>
  {/if}
  {#if message}<div class="notice success">{message}</div>{/if}
  <RuntimeErrorNotice {error} />

  {#if extraPluginList.length > 4}
    <label class="search-field" aria-label="Search plugins"><span aria-hidden="true">⌕</span><input bind:value={search} placeholder="Search plugins" /></label>
  {/if}

  {#if visiblePluginList.length > 0}
    <div class="plugin-list">
      {#each visiblePluginList as plugin}
        <article class="plugin-row" class:has-problem={plugin.state === 'Problem' || isInvalid(plugin)}>
          <div class="plugin-icon">{plugin.displayName.slice(0, 1).toUpperCase()}</div>
          <div class="plugin-main">
            <strong>{plugin.displayName}</strong>
            <span>{plugin.version || 'Unknown version'}</span>

            {#if hasDuplicates(plugin)}
              <div class="problem-card">
                <strong>Multiple plugin files found</strong><span>Choose which plugin file LazyBuilder should keep.</span>
                <div class="problem-actions">
                  <select disabled={busy || !canMutatePlugins} value={duplicateSelection[plugin.id] ?? plugin.candidateFiles?.[0]} onchange={(event) => duplicateSelection = { ...duplicateSelection, [plugin.id]: (event.currentTarget as HTMLSelectElement).value }}>
                    {#each plugin.candidateFiles ?? [] as candidate}<option value={candidate}>{candidate}</option>{/each}
                  </select>
                  <button disabled={busy || !canMutatePlugins} onclick={() => resolveDuplicates(plugin)}>Keep selected</button>
                </div>
              </div>
            {:else if isInvalid(plugin)}
              <div class="problem-card danger-card"><strong>Broken plugin file</strong><span>{plugin.candidateFiles?.[0] || 'LazyBuilder could not read this plugin file.'}</span></div>
            {:else if plugin.problemDetail}
              <small class="problem-text">{plugin.problemDetail}</small>
            {/if}
          </div>

          <div class="row-tools">
            <span class="state-pill" class:disabled={!isInvalid(plugin) && plugin.state !== 'Enabled'} class:problem={isInvalid(plugin) || plugin.state === 'Problem'}>{isInvalid(plugin) ? 'Needs attention' : plugin.state}</span>
            <details use:detailsMenu class="row-menu">
              <summary aria-label={`Manage ${plugin.displayName}`} title="Manage plugin">•••</summary>
              <div class="menu-popover">
                {#if !isInvalid(plugin)}
                  {#if plugin.state !== 'Problem'}
                    <button disabled={busy || !canMutatePlugins} onclick={() => togglePlugin(plugin)}>{plugin.state === 'Enabled' ? 'Disable' : 'Enable'}</button>
                    <button disabled={busy || !canMutatePlugins} onclick={() => updatePlugin(plugin)}>Replace plugin file…</button>
                  {/if}
                  <button class="danger" disabled={busy || !canMutatePlugins || hasDuplicates(plugin)} onclick={() => { removeCandidate = plugin; brokenFileCandidate = null; }}>Remove plugin…</button>
                {:else}
                  <button class="danger" disabled={busy || !canMutatePlugins || !plugin.candidateFiles?.[0]} onclick={() => { brokenFileCandidate = plugin; removeCandidate = null; }}>Remove broken file…</button>
                {/if}
              </div>
            </details>
          </div>
        </article>
      {/each}
    </div>
  {:else if extraPluginList.length > 0 && search.trim()}
    <section class="search-empty"><strong>No matching plugins</strong><span>Try another name.</span><button onclick={() => (search = '')}>Clear search</button></section>
  {:else if !error}
    <section class="empty-state">
      <div class="empty-icon">+</div><h3>No extra plugins</h3><p>This server can stay simple. Add a plugin only when your builders need one.</p>
      <button class="primary" disabled={busy || !canMutatePlugins} onclick={addPlugin}>Add plugin</button>
    </section>
  {/if}

  {#if managedPluginList.length > 0}
    <details class="system-components">
      <summary>LazyBuilder components</summary><p>Required components are maintained automatically.</p>
      <div class="system-list">
        {#each managedPluginList as plugin}
          <div><div class="plugin-icon core">L</div><span><strong>{plugin.displayName}</strong><small>{plugin.version} · Required</small></span><span class="state-pill" class:problem={plugin.state === 'Problem'}>{plugin.state === 'Enabled' ? 'Ready' : plugin.state}</span></div>
        {/each}
      </div>
    </details>
  {/if}
</section>

{#if removeCandidate}
  <div class="confirm-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && closeConfirmation()}>
    <section
      use:dialogFocus={{ onEscape: closeConfirmation, initialFocusSelector: '.secondary-confirm', escapeDisabled: busy }}
      class="confirm-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="remove-plugin-title"
    >
      <header><div><h3 id="remove-plugin-title">Remove {removeCandidate.displayName}?</h3><p>The plugin JAR will be removed from this server.</p></div><button class="close-button" disabled={busy} aria-label="Close remove plugin confirmation" onclick={closeConfirmation}>×</button></header>
      <div class="safe-note"><strong>Plugin data will be kept.</strong><span>Its data folder stays on disk so configuration and saved plugin data are not destroyed.</span></div>
      <div class="confirm-actions"><button class="secondary-confirm" disabled={busy} onclick={closeConfirmation}>Cancel</button><button class="danger-confirm" disabled={busy} onclick={removePlugin}>{busy ? 'Removing…' : 'Remove plugin'}</button></div>
    </section>
  </div>
{/if}

{#if brokenFileCandidate}
  <div class="confirm-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && closeConfirmation()}>
    <section
      use:dialogFocus={{ onEscape: closeConfirmation, initialFocusSelector: '.secondary-confirm', escapeDisabled: busy }}
      class="confirm-dialog danger-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="remove-broken-plugin-title"
    >
      <header><div><h3 id="remove-broken-plugin-title">Remove broken plugin file?</h3><p>{brokenFileCandidate.candidateFiles?.[0] || brokenFileCandidate.displayName}</p></div><button class="close-button" disabled={busy} aria-label="Close broken plugin file confirmation" onclick={closeConfirmation}>×</button></header>
      <div class="danger-note"><strong>Only the selected broken JAR is removed.</strong><span>LazyBuilder will not delete unrelated plugin files or plugin data.</span></div>
      <div class="confirm-actions"><button class="secondary-confirm" disabled={busy} onclick={closeConfirmation}>Cancel</button><button class="danger-confirm" disabled={busy} onclick={removeProblemPlugin}>{busy ? 'Removing…' : 'Remove broken file'}</button></div>
    </section>
  </div>
{/if}

<style>
  .plugins-page{width:min(920px,100%)}
  .page-header{display:flex;justify-content:space-between;align-items:flex-end;gap:20px;margin-bottom:18px}.page-header h2{margin:0;font-size:18px}.page-header p{margin:4px 0 0;color:var(--muted);font-size:12px}
  .primary{min-height:var(--control-height);border:1px solid var(--accent);border-radius:8px;padding:8px 13px;background:var(--accent);color:var(--accent-ink);font-weight:700;cursor:pointer}.primary:hover:not(:disabled){background:var(--accent-hover)}
  .notice{display:grid;gap:2px;margin-bottom:12px;padding:10px 12px;border-radius:8px;font-size:11px}.notice strong{font-size:11px}.notice.success{border:1px solid var(--accent-border);background:var(--accent-soft);color:#b9e5c7}.notice.warning{border:1px solid #655626;background:var(--warning-bg);color:#e3cf8d}
  .search-field{display:flex;align-items:center;gap:8px;margin-bottom:10px;min-height:var(--control-height);padding:0 11px;border:1px solid var(--border-soft);border-radius:8px;background:var(--surface);color:var(--muted)}.search-field:focus-within{border-color:var(--accent-border);box-shadow:0 0 0 3px var(--accent-soft)}.search-field input{min-width:0;flex:1;border:0;outline:0;background:transparent;color:var(--text);padding:0}
  .plugin-list{border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}.plugin-row{position:relative;display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:12px;min-height:62px;padding:10px 12px;border-bottom:1px solid var(--border-soft);transition:background 120ms ease}.plugin-row:last-child{border-bottom:0}.plugin-row:hover{background:var(--surface-2)}.plugin-row.has-problem{background:color-mix(in srgb,var(--danger-bg) 48%,var(--surface))}.plugin-icon{width:36px;height:36px;display:grid;place-items:center;border:1px solid var(--border);border-radius:9px;background:var(--surface-2);color:var(--text-soft);font-size:11px;font-weight:800}.plugin-icon.core{border-color:var(--accent-border);background:var(--accent-soft);color:var(--accent)}.plugin-main{min-width:0;display:grid;gap:2px}.plugin-main>strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.plugin-main>span{color:var(--muted);font-size:10px}.problem-text{margin-top:4px;color:#ff9ba3;font-size:10px}
  .state-pill{padding:3px 6px;border-radius:999px;background:#173321;color:#9fdaae;font-size:8px;font-weight:750;white-space:nowrap;text-transform:uppercase;letter-spacing:.03em}.state-pill.disabled{background:var(--surface-3);color:var(--muted)}.state-pill.problem{background:#3a2024;color:#ffafb5}.row-tools{display:flex;align-items:center;gap:5px}.row-menu{position:relative}.row-menu summary{width:34px;height:32px;display:grid;place-items:center;border-radius:8px;color:var(--muted);cursor:pointer;list-style:none;font-weight:800}.row-menu summary::-webkit-details-marker{display:none}.row-menu summary:hover,.row-menu[open] summary{background:var(--surface-2);color:var(--text)}.menu-popover{position:absolute;z-index:20;right:0;top:38px;width:190px;display:grid;gap:2px;padding:6px;border:1px solid var(--border);border-radius:10px;background:var(--surface-2);box-shadow:var(--shadow-popover)}.menu-popover button{width:100%;min-height:32px;padding:7px 9px;border-radius:7px;background:transparent;color:var(--text-soft);text-align:left;cursor:pointer;font-size:10px}.menu-popover button:hover:not(:disabled){background:var(--surface-3);color:var(--text)}.menu-popover button.danger{color:#f1a5aa}
  .problem-card{display:grid;gap:3px;margin-top:7px;padding:8px;border:1px solid #5f5125;border-radius:8px;background:var(--warning-bg)}.problem-card>strong{color:#e5d59d;font-size:10px}.problem-card>span{color:#bdae7b;font-size:9px;overflow-wrap:anywhere}.problem-card.danger-card{border-color:#6c363d;background:var(--danger-bg)}.problem-actions{display:flex;gap:7px;margin-top:3px}.problem-actions select{min-width:0;flex:1;padding:6px 8px}.problem-actions button{border:1px solid var(--border);border-radius:7px;padding:6px 9px;background:var(--surface-2);color:var(--text-soft);cursor:pointer;font-size:10px}
  .empty-state,.search-empty{min-height:220px;display:grid;place-content:center;justify-items:center;text-align:center;border:1px dashed var(--border);border-radius:10px;background:var(--bg-elevated)}.search-empty{min-height:150px;gap:3px;color:var(--muted);font-size:11px}.search-empty strong{color:var(--text);font-size:12px}.search-empty button{margin-top:6px;background:transparent;color:var(--accent);cursor:pointer}.empty-icon{width:40px;height:40px;display:grid;place-items:center;margin-bottom:9px;border-radius:10px;background:var(--surface-2);color:var(--muted);font-size:18px}.empty-state h3{margin:0;font-size:13px}.empty-state p{max-width:390px;margin:5px 0 14px;color:var(--muted);font-size:10px}
  .system-components{margin-top:16px;color:var(--muted)}.system-components>summary{width:max-content;color:var(--muted);font-size:10px;cursor:pointer}.system-components>p{margin:5px 0 8px;color:var(--muted-2);font-size:9px}.system-list{border:1px solid var(--border-soft);border-radius:8px;background:var(--bg-elevated)}.system-list>div{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:9px;padding:8px 9px;border-bottom:1px solid var(--border-soft)}.system-list>div:last-child{border-bottom:0}.system-list>div>span:nth-child(2){min-width:0;display:grid;gap:1px}.system-list strong{color:var(--text-soft);font-size:10px}.system-list small{color:var(--muted-2);font-size:9px}.system-list .plugin-icon{width:28px;height:28px;font-size:9px}
  .confirm-backdrop{position:fixed;z-index:120;inset:0;display:grid;place-items:center;padding:24px;background:rgba(4,6,8,.74);backdrop-filter:blur(4px)}.confirm-dialog{width:min(450px,100%);display:grid;gap:15px;padding:19px;border:1px solid var(--border);border-radius:13px;background:var(--surface);box-shadow:var(--shadow-popover)}.confirm-dialog.danger-dialog{border-color:#6c363d}.confirm-dialog header{display:flex;align-items:flex-start;justify-content:space-between;gap:14px}.confirm-dialog h3{margin:0;font-size:16px}.confirm-dialog header p{margin:4px 0 0;color:var(--muted);font-size:10px;overflow-wrap:anywhere}.close-button{width:30px;height:30px;border:0;border-radius:7px;background:transparent;color:var(--muted);font-size:19px;cursor:pointer}.safe-note,.danger-note{display:grid;gap:4px;padding:11px 12px;border:1px solid var(--border-soft);border-radius:8px;background:var(--bg-elevated)}.safe-note strong,.danger-note strong{font-size:10px}.safe-note span,.danger-note span{color:var(--muted);font-size:10px;line-height:1.45}.danger-note{border-color:#6c363d;background:var(--danger-bg)}.confirm-actions{display:flex;justify-content:flex-end;gap:8px}.secondary-confirm,.danger-confirm{min-height:34px;padding:7px 11px;border-radius:8px;font-weight:700;cursor:pointer}.secondary-confirm{border:1px solid var(--border);background:var(--surface-2);color:var(--text)}.danger-confirm{border:1px solid #a74650;background:#8d3039;color:#fff}
  button:disabled,select:disabled{opacity:.5;cursor:not-allowed}
  @media(max-width:760px){.page-header{align-items:stretch;flex-direction:column}.plugin-row{grid-template-columns:auto minmax(0,1fr)}.row-tools{grid-column:2;justify-content:flex-start}.problem-actions{flex-direction:column}.confirm-backdrop{padding:14px}.confirm-actions{align-items:stretch;flex-direction:column-reverse}}
</style>