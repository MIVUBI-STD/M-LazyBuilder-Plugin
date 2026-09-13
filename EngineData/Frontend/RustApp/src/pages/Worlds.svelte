<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { ManagedWorldSummary, UpdateWorldSettingsRequest, WorldSettingsSnapshot, WorldTaskSnapshot } from '../app/bridge/runtimeApi';

  let worlds: ManagedWorldSummary[] = [];
  let search = '';
  let error = '';
  let busy = false;
  let serverOnline = false;
  let startingServer = false;
  let operationBusyWorldId: string | null = null;
  let operationTask: WorldTaskSnapshot | null = null;

  let createOpen = false;
  let createName = '';
  let createType: 'FLAT' | 'VOID' = 'FLAT';
  let importOpen = false;
  let importPath = '';
  let importName = '';
  let importBusy = false;
  let settings: WorldSettingsSnapshot | null = null;
  let settingsBusy = false;
  let cloneSource: ManagedWorldSummary | null = null;
  let cloneName = '';
  let exportSource: ManagedWorldSummary | null = null;
  let exportName = '';
  let deleteSource: ManagedWorldSummary | null = null;
  let deleteConfirmation = '';

  function friendlyError(value: unknown) {
    return String(value).replace(/^Error:\s*/i, '').trim() || 'Something went wrong. Try again.';
  }

  function slugify(value: string) {
    return value.toLowerCase().replace(/[^a-z0-9_-]+/g, '-').replace(/^-+|-+$/g, '');
  }

  function kindLabel(world: ManagedWorldSummary) {
    if (world.kind === 'VOID') return 'Void';
    if (world.kind === 'IMPORTED') return 'Imported';
    return 'Flat';
  }

  function visibleWorlds() {
    const query = search.trim().toLowerCase();
    if (!query) return worlds;
    return worlds.filter((world) => `${world.displayName} ${world.kind} ${world.defaultGameMode} ${world.lifecycle} ${world.runtimeState}`.toLowerCase().includes(query));
  }

  function activeWorlds() {
    return visibleWorlds().filter((world) => world.lifecycle === 'ACTIVE');
  }

  function archivedWorlds() {
    return visibleWorlds().filter((world) => world.lifecycle === 'ARCHIVED');
  }

  function closePanels() {
    cloneSource = null;
    exportSource = null;
    deleteSource = null;
    deleteConfirmation = '';
  }

  async function refresh() {
    if (busy) return;
    busy = true;
    try {
      const snapshot = await runtimeProduct.server.snapshot();
      serverOnline = snapshot.state === 'Online';
      if (!serverOnline) {
        worlds = [];
        settings = null;
        closePanels();
        return;
      }
      const next = await runtimeProduct.worlds.list();
      worlds = [...next].sort((a, b) => a.lifecycle.localeCompare(b.lifecycle) || a.displayName.localeCompare(b.displayName));
      error = '';
    } catch (e) {
      worlds = [];
      error = friendlyError(e);
    } finally {
      busy = false;
    }
  }

  async function startServer() {
    if (startingServer || serverOnline) return;
    startingServer = true;
    error = '';
    try {
      const preflight = await runtimeProduct.server.preflight();
      if (!preflight.ready) {
        error = preflight.issues[0] || 'This server needs attention before it can start.';
        return;
      }
      await runtimeProduct.server.start();
      for (let i = 0; i < 20; i += 1) {
        await new Promise((resolve) => window.setTimeout(resolve, 750));
        const snapshot = await runtimeProduct.server.snapshot();
        if (snapshot.state === 'Online') break;
        if (snapshot.state === 'Offline' || snapshot.state === 'Crashed') break;
      }
      await refresh();
    } catch (e) {
      error = friendlyError(e);
    } finally {
      startingServer = false;
    }
  }

  async function createWorld() {
    const displayName = createName.trim();
    if (!displayName || !serverOnline) return;
    busy = true;
    error = '';
    try {
      await runtimeProduct.worlds.create({ folderName: slugify(displayName) || 'world', displayName, kind: createType });
      createOpen = false;
      createName = '';
    } catch (e) {
      error = friendlyError(e);
    } finally {
      busy = false;
      await refresh();
    }
  }

  async function pickImport() {
    if (importBusy) return;
    try {
      const selected = await runtimeProduct.worlds.pickImport();
      if (!selected) return;
      importPath = selected;
      const fileName = selected.split(/[\\/]/).pop() || 'Imported World';
      importName = fileName.replace(/\.(zip|mcworld)$/i, '').replace(/[-_]+/g, ' ').trim() || 'Imported World';
    } catch (e) {
      error = friendlyError(e);
    }
  }

  async function runImport() {
    const displayName = importName.trim();
    if (!serverOnline || importBusy || !importPath || !displayName) return;
    importBusy = true;
    error = '';
    try {
      const artifactName = await runtimeProduct.worlds.uploadImport(importPath);
      operationTask = await runtimeProduct.worlds.import({ artifactName, destinationFolder: slugify(displayName), displayName });
      importOpen = false;
      await pollTask(operationTask.taskId);
      importPath = '';
      importName = '';
    } catch (e) {
      error = friendlyError(e);
    } finally {
      importBusy = false;
      await refresh();
    }
  }

  async function pollTask(taskId: string) {
    while (true) {
      const task = await runtimeProduct.worlds.task(taskId);
      operationTask = task;
      if (task.state === 'SUCCEEDED') return;
      if (task.state === 'FAILED') throw new Error(task.error || task.message || 'World task failed.');
      await new Promise((resolve) => window.setTimeout(resolve, 750));
    }
  }

  async function runWorldTask(world: ManagedWorldSummary, action: 'backup' | 'archive' | 'restore') {
    if (operationBusyWorldId) return;
    operationBusyWorldId = world.id;
    error = '';
    try {
      operationTask = action === 'backup'
        ? await runtimeProduct.worlds.backup(world.id)
        : action === 'archive'
          ? await runtimeProduct.worlds.archive(world.id)
          : await runtimeProduct.worlds.restore(world.id);
      await pollTask(operationTask.taskId);
    } catch (e) {
      error = friendlyError(e);
    } finally {
      operationBusyWorldId = null;
      await refresh();
    }
  }

  async function toggleLoaded(world: ManagedWorldSummary) {
    if (operationBusyWorldId) return;
    operationBusyWorldId = world.id;
    try {
      if (world.runtimeState === 'LOADED') await runtimeProduct.worlds.unload(world.id);
      else await runtimeProduct.worlds.load(world.id);
    } catch (e) {
      error = friendlyError(e);
    } finally {
      operationBusyWorldId = null;
      await refresh();
    }
  }

  async function openSettings(world: ManagedWorldSummary) {
    settingsBusy = true;
    error = '';
    try {
      settings = await runtimeProduct.worlds.settings(world.id);
    } catch (e) {
      error = friendlyError(e);
    } finally {
      settingsBusy = false;
    }
  }

  async function saveSettings() {
    if (!settings) return;
    settingsBusy = true;
    try {
      const request: UpdateWorldSettingsRequest = {
        autoLoad: settings.autoLoad,
        defaultGameMode: settings.defaultGameMode,
        timeOfDayTicks: Number(settings.timeOfDayTicks),
        weather: settings.weather,
        naturalSpawning: settings.naturalSpawning,
        daylightCycle: settings.daylightCycle,
        weatherCycle: settings.weatherCycle
      };
      await runtimeProduct.worlds.updateSettings(settings.id, request);
      settings = null;
      await refresh();
    } catch (e) {
      error = friendlyError(e);
    } finally {
      settingsBusy = false;
    }
  }

  async function runClone() {
    if (!cloneSource || operationBusyWorldId || !cloneName.trim()) return;
    operationBusyWorldId = cloneSource.id;
    try {
      operationTask = await runtimeProduct.worlds.clone({ worldId: cloneSource.id, destinationFolder: slugify(cloneName), displayName: cloneName.trim() });
      closePanels();
      await pollTask(operationTask.taskId);
    } catch (e) {
      error = friendlyError(e);
    } finally {
      operationBusyWorldId = null;
      await refresh();
    }
  }

  async function runExport() {
    if (!exportSource || operationBusyWorldId || !exportName.trim()) return;
    operationBusyWorldId = exportSource.id;
    try {
      operationTask = await runtimeProduct.worlds.export({ worldId: exportSource.id, targetFormat: 'JAVA_1_21_4', artifactName: exportName.trim() });
      closePanels();
      await pollTask(operationTask.taskId);
    } catch (e) {
      error = friendlyError(e);
    } finally {
      operationBusyWorldId = null;
      await refresh();
    }
  }

  async function runDelete() {
    if (!deleteSource || deleteConfirmation.trim() !== deleteSource.displayName || operationBusyWorldId) return;
    operationBusyWorldId = deleteSource.id;
    try {
      operationTask = await runtimeProduct.worlds.delete({ worldId: deleteSource.id, typedFolderName: deleteConfirmation.trim() });
      closePanels();
      await pollTask(operationTask.taskId);
    } catch (e) {
      error = friendlyError(e);
    } finally {
      operationBusyWorldId = null;
      await refresh();
    }
  }

  onMount(() => void refresh());
</script>

<section class="worlds-page">
  <header class="page-head">
    <div>
      <h2>Worlds</h2>
      <p>Your build spaces, kept simple.</p>
    </div>
    {#if serverOnline}
      <div class="head-actions">
        <button class="secondary" onclick={() => { importOpen = true; if (!importPath) void pickImport(); }}>Import</button>
        <button class="primary" onclick={() => { createName = ''; createType = 'FLAT'; createOpen = true; }}>+ Create world</button>
      </div>
    {/if}
  </header>

  {#if error}<div class="notice error" role="alert">{error}</div>{/if}

  {#if !serverOnline}
    <section class="state-card">
      <div class="state-icon">◇</div>
      <h3>Start the server to manage worlds</h3>
      <p>World changes need Paper running. Start it here and stay on this page.</p>
      <button class="primary" disabled={startingServer} onclick={startServer}>{startingServer ? 'Starting…' : 'Start server'}</button>
    </section>
  {:else}
    {#if operationTask && ['QUEUED', 'RUNNING'].includes(operationTask.state)}
      <div class="task-strip" aria-live="polite">
        <span>{operationTask.message || 'Working…'}</span>
        <div class="progress"><i style={`width:${Math.max(2, operationTask.progressPercent)}%`}></i></div>
        <strong>{operationTask.progressPercent}%</strong>
      </div>
    {/if}

    {#if worlds.length > 4}
      <label class="search-field">
        <span>⌕</span><input bind:value={search} placeholder="Search worlds" aria-label="Search worlds" />
      </label>
    {/if}

    {#if worlds.length === 0 && !busy}
      <section class="state-card">
        <div class="state-icon">◇</div>
        <h3>No worlds yet</h3>
        <p>Create a clean world for building, or import one you already use.</p>
        <div class="state-actions"><button class="primary" onclick={() => (createOpen = true)}>Create world</button><button class="secondary" onclick={() => (importOpen = true)}>Import</button></div>
      </section>
    {:else if visibleWorlds().length === 0}
      <section class="search-empty"><strong>No matching worlds</strong><button onclick={() => (search = '')}>Clear search</button></section>
    {:else}
      {#if activeWorlds().length > 0}
        <section class="world-section">
          <div class="section-label">Worlds</div>
          <div class="world-list">
            {#each activeWorlds() as world}
              <article class="world-row">
                <div class="world-icon">{world.kind === 'VOID' ? '□' : '◆'}</div>
                <div class="world-copy">
                  <div class="world-title"><strong>{world.displayName}</strong>{#if world.runtimeState === 'LOADED'}<span class="loaded-dot"></span>{/if}</div>
                  <span>{kindLabel(world)} · {world.defaultGameMode.charAt(0) + world.defaultGameMode.slice(1).toLowerCase()}{world.autoLoad ? ' · Loads with server' : ''}</span>
                </div>
                <details class="menu">
                  <summary aria-label={`Actions for ${world.displayName}`}>•••</summary>
                  <div class="menu-popover">
                    <button onclick={() => openSettings(world)}>World settings</button>
                    <button onclick={() => toggleLoaded(world)}>{world.runtimeState === 'LOADED' ? 'Unload world' : 'Load world'}</button>
                    <div class="menu-separator"></div>
                    <button onclick={() => runWorldTask(world, 'backup')}>Create backup</button>
                    <button onclick={() => { closePanels(); cloneSource = world; cloneName = `${world.displayName} Copy`; }}>Duplicate world…</button>
                    <button onclick={() => { closePanels(); exportSource = world; exportName = `${slugify(world.displayName) || 'world'}-export`; }}>Export…</button>
                    <button onclick={() => runWorldTask(world, 'archive')}>Archive</button>
                    <div class="menu-separator"></div>
                    <button class="danger" onclick={() => { closePanels(); deleteSource = world; }}>Delete permanently…</button>
                  </div>
                </details>
              </article>
            {/each}
          </div>
        </section>
      {/if}

      {#if archivedWorlds().length > 0}
        <details class="archived-section">
          <summary>Archived worlds <span>{archivedWorlds().length}</span></summary>
          <div class="world-list archived-list">
            {#each archivedWorlds() as world}
              <article class="world-row archived">
                <div class="world-icon">◇</div>
                <div class="world-copy"><strong>{world.displayName}</strong><span>Archived</span></div>
                <div class="archived-actions"><button class="secondary compact" onclick={() => runWorldTask(world, 'restore')}>Restore</button><details class="menu"><summary>•••</summary><div class="menu-popover"><button class="danger" onclick={() => { closePanels(); deleteSource = world; }}>Delete permanently…</button></div></details></div>
              </article>
            {/each}
          </div>
        </details>
      {/if}
    {/if}
  {/if}
</section>

{#if createOpen}
  <div class="modal-backdrop" onclick={(event) => event.currentTarget === event.target && (createOpen = false)}>
    <section class="modal" role="dialog" aria-modal="true">
      <div class="modal-head"><div><h3>Create world</h3><p>Choose a simple build space.</p></div><button class="icon-button" onclick={() => (createOpen = false)}>×</button></div>
      <label>World name<input bind:value={createName} placeholder="Build World" autofocus /></label>
      <label>World type<select bind:value={createType}><option value="FLAT">Flat — normal building surface</option><option value="VOID">Void — empty building space</option></select></label>
      <div class="modal-actions"><button class="secondary" onclick={() => (createOpen = false)}>Cancel</button><button class="primary" disabled={busy || !createName.trim()} onclick={createWorld}>{busy ? 'Creating…' : 'Create world'}</button></div>
    </section>
  </div>
{/if}

{#if importOpen}
  <div class="modal-backdrop" onclick={(event) => event.currentTarget === event.target && (importOpen = false)}>
    <section class="modal" role="dialog" aria-modal="true">
      <div class="modal-head"><div><h3>Import world</h3><p>Add an existing Java world.</p></div><button class="icon-button" onclick={() => (importOpen = false)}>×</button></div>
      <label>World file<div class="file-picker"><span>{importPath ? importPath.split(/[\\/]/).pop() : 'No file selected'}</span><button class="secondary compact" onclick={pickImport}>Choose file</button></div></label>
      <label>World name<input bind:value={importName} disabled={!importPath || importBusy} placeholder="Imported World" /></label>
      <div class="modal-actions"><button class="secondary" onclick={() => (importOpen = false)}>Cancel</button><button class="primary" disabled={importBusy || !importPath || !importName.trim()} onclick={runImport}>{importBusy ? 'Importing…' : 'Import world'}</button></div>
    </section>
  </div>
{/if}

{#if settings}
  <div class="modal-backdrop">
    <section class="modal settings-modal" role="dialog" aria-modal="true">
      <div class="modal-head"><div><h3>{settings.displayName}</h3><p>World settings</p></div><button class="icon-button" onclick={() => (settings = null)}>×</button></div>
      <div class="settings-grid"><label>Game mode<select bind:value={settings.defaultGameMode}><option value="CREATIVE">Creative</option><option value="SURVIVAL">Survival</option><option value="ADVENTURE">Adventure</option><option value="SPECTATOR">Spectator</option></select></label><label>Weather<select bind:value={settings.weather}><option value="CLEAR">Clear</option><option value="RAIN">Rain</option><option value="THUNDER">Thunder</option></select></label><label>Time<input type="number" min="0" max="23999" bind:value={settings.timeOfDayTicks} /></label></div>
      <div class="toggle-list"><label><span><strong>Load with server</strong><small>Keep this world ready when Paper starts.</small></span><input type="checkbox" bind:checked={settings.autoLoad} /></label><label><span><strong>Mob spawning</strong><small>Allow natural mobs to spawn.</small></span><input type="checkbox" bind:checked={settings.naturalSpawning} /></label><label><span><strong>Daylight cycle</strong><small>Let time move normally.</small></span><input type="checkbox" bind:checked={settings.daylightCycle} /></label><label><span><strong>Weather cycle</strong><small>Let weather change naturally.</small></span><input type="checkbox" bind:checked={settings.weatherCycle} /></label></div>
      <div class="modal-actions"><button class="secondary" onclick={() => (settings = null)}>Cancel</button><button class="primary" disabled={settingsBusy} onclick={saveSettings}>{settingsBusy ? 'Saving…' : 'Save changes'}</button></div>
    </section>
  </div>
{/if}

{#if cloneSource}<div class="modal-backdrop"><section class="modal"><div class="modal-head"><div><h3>Duplicate world</h3><p>Create a separate copy of {cloneSource.displayName}.</p></div><button class="icon-button" onclick={closePanels}>×</button></div><label>New world name<input bind:value={cloneName} /></label><div class="modal-actions"><button class="secondary" onclick={closePanels}>Cancel</button><button class="primary" onclick={runClone}>Duplicate</button></div></section></div>{/if}
{#if exportSource}<div class="modal-backdrop"><section class="modal"><div class="modal-head"><div><h3>Export world</h3><p>Save {exportSource.displayName} as a Java ZIP.</p></div><button class="icon-button" onclick={closePanels}>×</button></div><label>Export name<input bind:value={exportName} /></label><div class="modal-actions"><button class="secondary" onclick={closePanels}>Cancel</button><button class="primary" onclick={runExport}>Export</button></div></section></div>{/if}
{#if deleteSource}<div class="modal-backdrop"><section class="modal"><div class="modal-head"><div><h3>Delete {deleteSource.displayName}?</h3><p>This permanently removes the world.</p></div><button class="icon-button" onclick={closePanels}>×</button></div><div class="danger-callout">Type <strong>{deleteSource.displayName}</strong> to confirm.</div><label>World name<input bind:value={deleteConfirmation} /></label><div class="modal-actions"><button class="secondary" onclick={closePanels}>Cancel</button><button class="danger-button" disabled={deleteConfirmation.trim() !== deleteSource.displayName} onclick={runDelete}>Delete permanently</button></div></section></div>{/if}

<style>
  .worlds-page{width:min(920px,100%)}.page-head{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;margin-bottom:18px}.page-head h2{margin:0;font-size:18px}.page-head p{margin:4px 0 0;color:var(--muted);font-size:12px}.head-actions,.state-actions,.modal-actions,.archived-actions{display:flex;gap:8px;align-items:center}.primary,.secondary,.danger-button{min-height:var(--control-height);border-radius:var(--radius-sm);padding:8px 12px;font-weight:650;cursor:pointer}.primary{border:1px solid var(--accent);background:var(--accent);color:var(--accent-ink)}.secondary{border:1px solid var(--border);background:var(--surface-2);color:var(--text)}.compact{min-height:32px;padding:6px 9px;font-size:11px}.danger-button{border:1px solid #a74650;background:#8d3039;color:#fff}.notice{margin-bottom:12px;padding:10px 12px;border:1px solid #70343a;border-radius:var(--radius-sm);background:var(--danger-bg);color:#ffd9dc;font-size:12px}.state-card{min-height:280px;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;border:1px dashed var(--border);border-radius:var(--radius);background:var(--bg-elevated);padding:34px}.state-icon{width:46px;height:46px;display:grid;place-items:center;border-radius:11px;background:var(--surface-2);color:var(--muted);font-size:20px}.state-card h3{margin:12px 0 5px}.state-card p{max-width:420px;margin:0 0 16px;color:var(--muted);font-size:12px}.search-field{display:flex;align-items:center;gap:8px;margin-bottom:12px;padding:0 11px;min-height:38px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--surface);color:var(--muted)}.search-field input{width:100%;border:0;outline:0;background:transparent;color:var(--text)}.task-strip{display:grid;grid-template-columns:minmax(0,1fr) minmax(160px,320px) auto;gap:12px;align-items:center;margin-bottom:12px;padding:10px 12px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--surface);font-size:11px}.progress{height:5px;overflow:hidden;border-radius:999px;background:var(--surface-3)}.progress i{display:block;height:100%;background:var(--accent)}.world-section{margin-top:4px}.section-label{margin:0 0 7px;color:var(--muted-2);font-size:10px;font-weight:750;text-transform:uppercase;letter-spacing:.08em}.world-list{overflow:visible;border:1px solid var(--border-soft);border-radius:var(--radius);background:var(--surface);box-shadow:var(--shadow-card)}.world-row{position:relative;display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:12px;min-height:66px;padding:11px 12px;border-bottom:1px solid var(--border-soft)}.world-row:last-child{border-bottom:0}.world-row:hover{background:var(--surface-2)}.world-row.archived{opacity:.75}.world-icon{width:38px;height:38px;display:grid;place-items:center;border-radius:9px;background:var(--surface-2);color:var(--muted)}.world-copy{min-width:0;display:grid;gap:3px}.world-copy strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px}.world-copy>span{color:var(--muted);font-size:11px}.world-title{display:flex;align-items:center;gap:8px}.loaded-dot{width:7px;height:7px;border-radius:50%;background:var(--accent);box-shadow:0 0 0 3px var(--accent-soft)}.menu{position:relative}.menu summary{list-style:none;width:34px;height:32px;display:grid;place-items:center;border-radius:var(--radius-sm);color:var(--muted);cursor:pointer}.menu summary::-webkit-details-marker{display:none}.menu summary:hover,.menu[open] summary{background:var(--surface-3);color:var(--text)}.menu-popover{position:absolute;z-index:20;right:0;top:38px;width:190px;padding:6px;border:1px solid var(--border);border-radius:var(--radius);background:var(--surface-2);box-shadow:var(--shadow-popover)}.menu-popover button{width:100%;min-height:32px;padding:7px 9px;border-radius:7px;background:transparent;color:var(--text-soft);text-align:left;font-size:11px;cursor:pointer}.menu-popover button:hover{background:var(--surface-3);color:var(--text)}.menu-popover .danger{color:#f1a5aa}.menu-separator{height:1px;margin:5px 3px;background:var(--border)}.archived-section{margin-top:14px}.archived-section>summary{display:flex;align-items:center;gap:7px;color:var(--muted);font-size:11px;cursor:pointer}.archived-section>summary span{padding:2px 6px;border-radius:999px;background:var(--surface-2);font-size:9px}.archived-list{margin-top:8px}.search-empty{min-height:160px;display:grid;place-content:center;justify-items:center;gap:6px;color:var(--muted);font-size:11px}.search-empty button{background:transparent;color:var(--accent);cursor:pointer}.modal-backdrop{position:fixed;z-index:100;inset:0;display:grid;place-items:center;padding:24px;background:rgba(0,0,0,.68);backdrop-filter:blur(4px)}.modal{width:min(480px,100%);display:grid;gap:15px;padding:20px;border:1px solid var(--border);border-radius:var(--radius);background:var(--surface);box-shadow:var(--shadow-popover)}.settings-modal{width:min(600px,100%)}.modal-head{display:flex;justify-content:space-between;gap:16px}.modal-head h3{margin:0;font-size:18px}.modal-head p{margin:4px 0 0;color:var(--muted);font-size:11px}.icon-button{width:32px;height:32px;border-radius:var(--radius-sm);background:transparent;color:var(--muted);font-size:20px;cursor:pointer}.modal label{display:grid;gap:6px;color:var(--text-soft);font-size:11px;font-weight:600}.modal input,.modal select{width:100%;min-height:38px;padding:8px 10px}.file-picker{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px;align-items:center;padding:7px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--bg)}.file-picker span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--muted)}.settings-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px}.toggle-list{display:grid;border:1px solid var(--border);border-radius:var(--radius-sm);overflow:hidden}.toggle-list label{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:10px 11px;border-bottom:1px solid var(--border-soft)}.toggle-list label:last-child{border-bottom:0}.toggle-list label>span{display:grid;gap:2px}.toggle-list small{color:var(--muted);font-size:10px}.toggle-list input{width:auto}.danger-callout{padding:10px 11px;border:1px solid #6c363d;border-radius:var(--radius-sm);background:var(--danger-bg);color:#f1c5c9;font-size:11px}
  @media(max-width:760px){.page-head{align-items:flex-start;flex-direction:column}.world-row{grid-template-columns:auto minmax(0,1fr)}.world-row>.menu,.archived-actions{grid-column:2;justify-self:start}.settings-grid,.task-strip{grid-template-columns:1fr}}
</style>