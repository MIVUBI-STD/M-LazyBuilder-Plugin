<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type {
    ManagedWorldSummary,
    WorldSettingsSnapshot,
    UpdateWorldSettingsRequest,
    WorldTaskSnapshot
  } from '../app/bridge/runtimeApi';

  let worlds: ManagedWorldSummary[] = [];
  let error = '';
  let connectionStatus = 'Checking server…';
  let busy = false;
  let serverOnline = false;
  let startingServer = false;
  let search = '';

  let createOpen = false;
  let createName = '';
  let createType: 'FLAT' | 'VOID' = 'FLAT';

  let importOpen = false;
  let importPath = '';
  let importName = '';
  let importBusy = false;

  let settings: WorldSettingsSnapshot | null = null;
  let settingsBusy = false;

  let operationBusyWorldId: string | null = null;
  let operationTask: WorldTaskSnapshot | null = null;
  let cloneSource: ManagedWorldSummary | null = null;
  let cloneName = '';
  let exportSource: ManagedWorldSummary | null = null;
  let exportName = '';
  let deleteSource: ManagedWorldSummary | null = null;
  let deleteConfirmation = '';

  async function refresh() {
    if (busy) return;
    busy = true;
    try {
      const server = await runtimeProduct.server.snapshot();
      serverOnline = server.state === 'Online';
      if (!serverOnline) {
        worlds = [];
        settings = null;
        operationTask = null;
        operationBusyWorldId = null;
        closeOperationPanels();
        connectionStatus = server.state === 'Starting' ? 'Server is starting…' : 'Server is offline';
        error = '';
        return;
      }

      worlds = await runtimeProduct.worlds.list();
      worlds = [...worlds].sort((left, right) =>
        left.lifecycle.localeCompare(right.lifecycle) || left.displayName.localeCompare(right.displayName)
      );
      connectionStatus = `${worlds.length} world${worlds.length === 1 ? '' : 's'}`;
      error = '';
    } catch (e) {
      worlds = [];
      settings = null;
      connectionStatus = 'Worlds unavailable';
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
      connectionStatus = 'Server is starting…';
      for (let attempt = 0; attempt < 20; attempt += 1) {
        await new Promise((resolve) => window.setTimeout(resolve, 750));
        const snapshot = await runtimeProduct.server.snapshot();
        if (snapshot.state === 'Online') {
          await refresh();
          return;
        }
        if (snapshot.state === 'Crashed' || snapshot.state === 'Offline') break;
      }
      await refresh();
    } catch (e) {
      error = friendlyError(e);
    } finally {
      startingServer = false;
    }
  }

  function openCreate() {
    if (!serverOnline) return;
    createName = '';
    createType = 'FLAT';
    createOpen = true;
  }

  async function createWorld() {
    const displayName = createName.trim();
    if (!displayName || !serverOnline) return;
    busy = true;
    error = '';
    try {
      const folderName = slugify(displayName) || 'world';
      await runtimeProduct.worlds.create({ folderName, displayName, kind: createType });
      createOpen = false;
      createName = '';
    } catch (e) {
      error = friendlyError(e);
    } finally {
      busy = false;
      await refresh();
    }
  }

  async function openImport() {
    if (!serverOnline || importBusy) return;
    importOpen = true;
    if (!importPath) await pickImport();
  }

  async function pickImport() {
    if (!serverOnline || importBusy) return;
    try {
      const selected = await runtimeProduct.worlds.pickImport();
      if (!selected) return;
      importPath = selected;
      const fileName = selected.split(/[\\/]/).pop() || 'Imported World';
      importName = fileName.replace(/\.(zip|mcworld)$/i, '').replace(/[-_]+/g, ' ').trim() || 'Imported World';
      error = '';
    } catch (e) {
      error = friendlyError(e);
    }
  }

  async function runImport() {
    const displayName = importName.trim();
    if (!serverOnline || importBusy || !importPath || !displayName) return;
    const destinationFolder = slugify(displayName);
    if (!destinationFolder) return;

    importBusy = true;
    operationTask = null;
    error = '';
    try {
      const artifactName = await runtimeProduct.worlds.uploadImport(importPath);
      operationTask = await runtimeProduct.worlds.import({ artifactName, destinationFolder, displayName });
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

  async function toggleRuntime(world: ManagedWorldSummary) {
    if (busy) return;
    busy = true;
    error = '';
    try {
      if (world.runtimeState === 'LOADED') await runtimeProduct.worlds.unload(world.id);
      else await runtimeProduct.worlds.load(world.id);
    } catch (e) {
      error = friendlyError(e);
    } finally {
      busy = false;
      await refresh();
    }
  }

  async function runLifecycleTask(world: ManagedWorldSummary, operation: 'archive' | 'restore') {
    if (operationBusyWorldId || importBusy) return;
    if (operation === 'archive' && !window.confirm(`Archive ${world.displayName}?`)) return;

    operationBusyWorldId = world.id;
    operationTask = null;
    error = '';
    try {
      operationTask = operation === 'archive'
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

  async function runBackup(world: ManagedWorldSummary) {
    if (operationBusyWorldId || importBusy) return;
    operationBusyWorldId = world.id;
    operationTask = null;
    error = '';
    try {
      operationTask = await runtimeProduct.worlds.backup(world.id);
      await pollTask(operationTask.taskId);
    } catch (e) {
      error = friendlyError(e);
    } finally {
      operationBusyWorldId = null;
      await refresh();
    }
  }

  function closeOperationPanels() {
    cloneSource = null;
    exportSource = null;
    deleteSource = null;
    deleteConfirmation = '';
  }

  function openClone(world: ManagedWorldSummary) {
    closeOperationPanels();
    cloneSource = world;
    cloneName = `${world.displayName} Copy`;
  }

  async function runClone() {
    if (!cloneSource || operationBusyWorldId || importBusy) return;
    const displayName = cloneName.trim();
    if (!displayName) return;
    const destinationFolder = slugify(displayName);
    if (!destinationFolder) return;

    operationBusyWorldId = cloneSource.id;
    operationTask = null;
    error = '';
    try {
      operationTask = await runtimeProduct.worlds.clone({ worldId: cloneSource.id, destinationFolder, displayName });
      closeOperationPanels();
      cloneName = '';
      await pollTask(operationTask.taskId);
    } catch (e) {
      error = friendlyError(e);
    } finally {
      operationBusyWorldId = null;
      await refresh();
    }
  }

  function openExport(world: ManagedWorldSummary) {
    closeOperationPanels();
    exportSource = world;
    exportName = `${slugify(world.displayName) || 'world'}-export`;
  }

  async function runExport() {
    if (!exportSource || operationBusyWorldId || importBusy) return;
    const artifactName = exportName.trim();
    if (!artifactName) return;

    operationBusyWorldId = exportSource.id;
    operationTask = null;
    error = '';
    try {
      operationTask = await runtimeProduct.worlds.export({
        worldId: exportSource.id,
        targetFormat: 'JAVA_1_21_4',
        artifactName
      });
      closeOperationPanels();
      exportName = '';
      await pollTask(operationTask.taskId);
    } catch (e) {
      error = friendlyError(e);
    } finally {
      operationBusyWorldId = null;
      await refresh();
    }
  }

  function openDelete(world: ManagedWorldSummary) {
    closeOperationPanels();
    deleteSource = world;
    deleteConfirmation = '';
  }

  async function runDelete() {
    if (!deleteSource || operationBusyWorldId || importBusy) return;
    const confirmation = deleteConfirmation.trim();
    if (confirmation !== deleteSource.displayName) return;

    operationBusyWorldId = deleteSource.id;
    operationTask = null;
    error = '';
    try {
      operationTask = await runtimeProduct.worlds.delete({
        worldId: deleteSource.id,
        typedFolderName: confirmation
      });
      closeOperationPanels();
      await pollTask(operationTask.taskId);
    } catch (e) {
      error = friendlyError(e);
    } finally {
      operationBusyWorldId = null;
      await refresh();
    }
  }

  async function pollTask(taskId: string) {
    while (true) {
      const task = await runtimeProduct.worlds.task(taskId);
      operationTask = task;
      if (task.state === 'SUCCEEDED') {
        error = '';
        return;
      }
      if (task.state === 'FAILED') throw new Error(task.error || task.message || 'World task failed.');
      await new Promise((resolve) => window.setTimeout(resolve, 750));
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
    error = '';
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
      settings = await runtimeProduct.worlds.updateSettings(settings.id, request);
      await refresh();
      settings = null;
    } catch (e) {
      error = friendlyError(e);
    } finally {
      settingsBusy = false;
    }
  }

  function visibleWorlds() {
    const query = search.trim().toLowerCase();
    if (!query) return worlds;
    return worlds.filter((world) =>
      `${world.displayName} ${world.kind} ${world.defaultGameMode} ${world.lifecycle} ${world.runtimeState}`.toLowerCase().includes(query)
    );
  }

  function statusLabel(world: ManagedWorldSummary) {
    if (world.lifecycle === 'ARCHIVED') return 'Archived';
    return world.runtimeState === 'LOADED' ? 'Loaded' : 'Unloaded';
  }

  function kindLabel(world: ManagedWorldSummary) {
    if (world.kind === 'VOID') return 'Void';
    if (world.kind === 'IMPORTED') return 'Imported';
    return 'Flat';
  }

  function friendlyError(value: unknown) {
    const message = String(value).replace(/^Error:\s*/i, '').trim();
    return message || 'Something went wrong. Try again.';
  }

  function slugify(value: string) {
    return value.toLowerCase().replace(/[^a-z0-9_-]+/g, '-').replace(/^-+|-+$/g, '');
  }

  onMount(() => void refresh());
</script>

<section class="worlds-page">
  <header class="page-head">
    <div>
      <h1>Worlds</h1>
      <p>{serverOnline ? `${connectionStatus} ready to manage.` : 'Manage the places your builders work in.'}</p>
    </div>
    {#if serverOnline}
      <div class="head-actions">
        <button class="secondary" disabled={busy || importBusy || operationBusyWorldId !== null} onclick={openImport}>Import</button>
        <button class="primary" disabled={busy || importBusy || operationBusyWorldId !== null} onclick={openCreate}>+ Create world</button>
      </div>
    {/if}
  </header>

  {#if error}
    <div class="notice error-notice">
      <strong>Couldn’t complete that action</strong>
      <span>{error}</span>
    </div>
  {/if}

  {#if !serverOnline}
    <section class="offline-state">
      <div class="state-icon">◇</div>
      <h2>{connectionStatus === 'Server is starting…' ? 'Starting the server' : 'Start the server to manage worlds'}</h2>
      <p>World changes need the Paper server running. You can start it here and stay on this page.</p>
      <button class="primary" disabled={startingServer || connectionStatus === 'Server is starting…'} onclick={startServer}>
        {startingServer || connectionStatus === 'Server is starting…' ? 'Starting…' : 'Start server'}
      </button>
    </section>
  {:else}
    {#if operationTask && ['QUEUED', 'RUNNING'].includes(operationTask.state)}
      <section class="task-strip" aria-live="polite">
        <div>
          <strong>{operationTask.taskType}</strong>
          <span>{operationTask.message || 'Working…'}</span>
        </div>
        <div class="progress-track"><span style={`width:${Math.max(2, operationTask.progressPercent)}%`}></span></div>
        <small>{operationTask.progressPercent}%</small>
      </section>
    {/if}

    {#if worlds.length > 0}
      <div class="toolbar">
        <label class="search-field">
          <span aria-hidden="true">⌕</span>
          <input bind:value={search} placeholder="Search worlds" aria-label="Search worlds" />
        </label>
        <span class="world-count">{visibleWorlds().length} shown</span>
      </div>
    {/if}

    {#if worlds.length === 0 && !busy && !error}
      <section class="empty-state">
        <div class="state-icon">◇</div>
        <h2>No worlds yet</h2>
        <p>Create a clean build world or import an existing Java world.</p>
        <div class="empty-actions">
          <button class="primary" onclick={openCreate}>Create world</button>
          <button class="secondary" onclick={openImport}>Import world</button>
        </div>
      </section>
    {:else if visibleWorlds().length === 0}
      <section class="search-empty">
        <strong>No matching worlds</strong>
        <span>Try a different name or clear the search.</span>
        <button onclick={() => (search = '')}>Clear search</button>
      </section>
    {:else}
      <div class="world-list">
        {#each visibleWorlds() as world}
          <article class="world-item" class:archived={world.lifecycle === 'ARCHIVED'}>
            <div class="world-icon">{world.kind === 'VOID' ? '□' : '◆'}</div>
            <div class="world-info">
              <div class="world-name-row">
                <strong>{world.displayName}</strong>
                <span class="status-pill" class:loaded={world.runtimeState === 'LOADED'}>{statusLabel(world)}</span>
              </div>
              <div class="world-meta">
                <span>{kindLabel(world)}</span>
                <span>{world.defaultGameMode.charAt(0) + world.defaultGameMode.slice(1).toLowerCase()}</span>
                {#if world.autoLoad}<span>Loads with server</span>{/if}
              </div>
            </div>

            <div class="world-actions">
              {#if world.lifecycle === 'ACTIVE'}
                <button class="secondary compact" disabled={busy || operationBusyWorldId !== null} onclick={() => toggleRuntime(world)}>
                  {world.runtimeState === 'LOADED' ? 'Unload' : 'Load'}
                </button>
                <details class="menu">
                  <summary aria-label={`More actions for ${world.displayName}`}>•••</summary>
                  <div class="menu-popover">
                    <button disabled={settingsBusy || operationBusyWorldId !== null} onclick={() => openSettings(world)}>World settings</button>
                    <button disabled={operationBusyWorldId !== null} onclick={() => runBackup(world)}>Create backup</button>
                    <button disabled={operationBusyWorldId !== null} onclick={() => openClone(world)}>Clone</button>
                    <button disabled={operationBusyWorldId !== null} onclick={() => openExport(world)}>Export</button>
                    <div class="menu-separator"></div>
                    <button disabled={operationBusyWorldId !== null} onclick={() => runLifecycleTask(world, 'archive')}>Archive</button>
                    <button class="danger-text" disabled={operationBusyWorldId !== null} onclick={() => openDelete(world)}>Delete permanently</button>
                  </div>
                </details>
              {:else}
                <button class="secondary compact" disabled={operationBusyWorldId !== null} onclick={() => runLifecycleTask(world, 'restore')}>Restore</button>
                <details class="menu">
                  <summary aria-label={`More actions for ${world.displayName}`}>•••</summary>
                  <div class="menu-popover">
                    <button class="danger-text" disabled={operationBusyWorldId !== null} onclick={() => openDelete(world)}>Delete permanently</button>
                  </div>
                </details>
              {/if}
            </div>
          </article>
        {/each}
      </div>
    {/if}
  {/if}
</section>

{#if createOpen}
  <div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && (createOpen = false)}>
    <section class="modal" role="dialog" aria-modal="true" aria-labelledby="create-title">
      <div class="modal-head">
        <div><h2 id="create-title">Create world</h2><p>Start with a simple world made for building.</p></div>
        <button class="icon-button" aria-label="Close" onclick={() => (createOpen = false)}>×</button>
      </div>
      <label>World name<input bind:value={createName} placeholder="Build World" autofocus /></label>
      <label>World type
        <select bind:value={createType}>
          <option value="FLAT">Flat — normal building surface</option>
          <option value="VOID">Void — empty building space</option>
        </select>
      </label>
      <div class="modal-actions">
        <button class="secondary" disabled={busy} onclick={() => (createOpen = false)}>Cancel</button>
        <button class="primary" disabled={busy || !createName.trim()} onclick={createWorld}>{busy ? 'Creating…' : 'Create world'}</button>
      </div>
    </section>
  </div>
{/if}

{#if importOpen}
  <div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && (importOpen = false)}>
    <section class="modal" role="dialog" aria-modal="true" aria-labelledby="import-title">
      <div class="modal-head">
        <div><h2 id="import-title">Import world</h2><p>Add an existing Java world to this server.</p></div>
        <button class="icon-button" aria-label="Close" onclick={() => (importOpen = false)}>×</button>
      </div>
      <label>World file
        <div class="file-picker">
          <span title={importPath}>{importPath ? importPath.split(/[\\/]/).pop() : 'No file selected'}</span>
          <button class="secondary compact" disabled={importBusy} onclick={pickImport}>Choose file</button>
        </div>
      </label>
      <label>World name<input bind:value={importName} disabled={!importPath || importBusy} placeholder="Imported World" /></label>
      <div class="modal-actions">
        <button class="secondary" disabled={importBusy} onclick={() => (importOpen = false)}>Cancel</button>
        <button class="primary" disabled={importBusy || !importPath || !importName.trim()} onclick={runImport}>{importBusy ? 'Importing…' : 'Import world'}</button>
      </div>
    </section>
  </div>
{/if}

{#if cloneSource}
  <div class="modal-backdrop" role="presentation">
    <section class="modal" role="dialog" aria-modal="true">
      <div class="modal-head"><div><h2>Clone world</h2><p>Create a separate copy of {cloneSource.displayName}.</p></div><button class="icon-button" aria-label="Close" onclick={closeOperationPanels}>×</button></div>
      <label>New world name<input bind:value={cloneName} disabled={operationBusyWorldId !== null} /></label>
      <div class="modal-actions"><button class="secondary" onclick={closeOperationPanels}>Cancel</button><button class="primary" disabled={operationBusyWorldId !== null || !cloneName.trim()} onclick={runClone}>Clone world</button></div>
    </section>
  </div>
{/if}

{#if exportSource}
  <div class="modal-backdrop" role="presentation">
    <section class="modal" role="dialog" aria-modal="true">
      <div class="modal-head"><div><h2>Export world</h2><p>Save {exportSource.displayName} as a Java 1.21.4 ZIP.</p></div><button class="icon-button" aria-label="Close" onclick={closeOperationPanels}>×</button></div>
      <label>Export name<input bind:value={exportName} disabled={operationBusyWorldId !== null} /></label>
      <div class="modal-actions"><button class="secondary" onclick={closeOperationPanels}>Cancel</button><button class="primary" disabled={operationBusyWorldId !== null || !exportName.trim()} onclick={runExport}>Export world</button></div>
    </section>
  </div>
{/if}

{#if deleteSource}
  <div class="modal-backdrop" role="presentation">
    <section class="modal danger-modal" role="dialog" aria-modal="true">
      <div class="modal-head"><div><h2>Delete {deleteSource.displayName}?</h2><p>This permanently removes the world from this server.</p></div><button class="icon-button" aria-label="Close" onclick={closeOperationPanels}>×</button></div>
      <div class="danger-callout">This cannot be undone. Type <strong>{deleteSource.displayName}</strong> to confirm.</div>
      <label>World name<input bind:value={deleteConfirmation} disabled={operationBusyWorldId !== null} /></label>
      <div class="modal-actions"><button class="secondary" onclick={closeOperationPanels}>Cancel</button><button class="danger-button" disabled={operationBusyWorldId !== null || deleteConfirmation.trim() !== deleteSource.displayName} onclick={runDelete}>Delete permanently</button></div>
    </section>
  </div>
{/if}

{#if settings}
  <div class="modal-backdrop" role="presentation">
    <section class="modal settings-modal" role="dialog" aria-modal="true">
      <div class="modal-head"><div><h2>{settings.displayName}</h2><p>Settings for this world.</p></div><button class="icon-button" aria-label="Close" disabled={settingsBusy} onclick={() => (settings = null)}>×</button></div>
      <div class="settings-grid-local">
        <label>Game mode<select bind:value={settings.defaultGameMode} disabled={settingsBusy}><option value="CREATIVE">Creative</option><option value="SURVIVAL">Survival</option><option value="ADVENTURE">Adventure</option><option value="SPECTATOR">Spectator</option></select></label>
        <label>Weather<select bind:value={settings.weather} disabled={settingsBusy}><option value="CLEAR">Clear</option><option value="RAIN">Rain</option><option value="THUNDER">Thunder</option></select></label>
        <label>Time<input type="number" min="0" max="23999" bind:value={settings.timeOfDayTicks} disabled={settingsBusy} /></label>
      </div>
      <div class="toggle-list">
        <label><span><strong>Load with server</strong><small>Keep this world ready when Paper starts.</small></span><input type="checkbox" bind:checked={settings.autoLoad} disabled={settingsBusy} /></label>
        <label><span><strong>Mob spawning</strong><small>Allow natural mobs to spawn.</small></span><input type="checkbox" bind:checked={settings.naturalSpawning} disabled={settingsBusy} /></label>
        <label><span><strong>Daylight cycle</strong><small>Let time move normally.</small></span><input type="checkbox" bind:checked={settings.daylightCycle} disabled={settingsBusy} /></label>
        <label><span><strong>Weather cycle</strong><small>Let weather change naturally.</small></span><input type="checkbox" bind:checked={settings.weatherCycle} disabled={settingsBusy} /></label>
      </div>
      <div class="modal-actions"><button class="secondary" disabled={settingsBusy} onclick={() => (settings = null)}>Cancel</button><button class="primary" disabled={settingsBusy} onclick={saveSettings}>{settingsBusy ? 'Saving…' : 'Save changes'}</button></div>
    </section>
  </div>
{/if}

<style>
  .worlds-page { width:min(1040px,100%); }
  .page-head { display:flex; justify-content:space-between; align-items:flex-end; gap:24px; margin-bottom:20px; }
  .page-head h1 { margin:0; font-size:24px; letter-spacing:-.025em; }
  .page-head p { margin:5px 0 0; color:var(--muted); font-size:12px; }
  .head-actions,.empty-actions { display:flex; align-items:center; gap:8px; }
  button { font:inherit; cursor:pointer; }
  button:disabled { cursor:default; opacity:.48; }
  .primary,.secondary,.danger-button { min-height:36px; border-radius:var(--radius-sm); padding:8px 12px; font-weight:650; }
  .primary { border:1px solid var(--accent); background:var(--accent); color:var(--accent-ink); }
  .primary:hover:not(:disabled) { background:var(--accent-hover); border-color:var(--accent-hover); }
  .secondary { border:1px solid var(--border); background:var(--surface-2); color:var(--text); }
  .secondary:hover:not(:disabled) { background:var(--surface-3); }
  .compact { min-height:32px; padding:6px 9px; font-size:11px; }
  .notice { display:grid; gap:3px; padding:11px 13px; border-radius:var(--radius-sm); margin-bottom:14px; font-size:12px; }
  .error-notice { border:1px solid #713940; background:var(--danger-bg); color:#ffdadd; }
  .error-notice span { color:#efb9be; }
  .offline-state,.empty-state { min-height:300px; display:flex; flex-direction:column; align-items:center; justify-content:center; text-align:center; border:1px dashed var(--border); border-radius:var(--radius); background:var(--bg-elevated); padding:36px; }
  .offline-state h2,.empty-state h2 { margin:13px 0 5px; font-size:18px; }
  .offline-state p,.empty-state p { margin:0 0 17px; color:var(--muted); font-size:12px; max-width:420px; }
  .state-icon { width:46px; height:46px; display:grid; place-items:center; border-radius:11px; background:var(--surface-2); border:1px solid var(--border); font-size:20px; color:var(--muted); }
  .toolbar { display:flex; align-items:center; gap:10px; margin-bottom:10px; }
  .search-field { min-width:220px; flex:1; display:flex; align-items:center; gap:8px; min-height:38px; padding:0 11px; border:1px solid var(--border); border-radius:var(--radius-sm); background:var(--surface); color:var(--muted); }
  .search-field:focus-within { border-color:color-mix(in srgb,var(--accent) 45%,var(--border)); box-shadow:0 0 0 3px color-mix(in srgb,var(--accent) 10%,transparent); }
  .search-field input { width:100%; border:0; outline:0; background:transparent; color:var(--text); }
  .world-count { color:var(--muted-2); font-size:11px; }
  .search-empty { min-height:180px; display:grid; place-content:center; justify-items:center; gap:5px; color:var(--muted); }
  .search-empty strong { color:var(--text); }
  .search-empty button { margin-top:7px; border:0; background:transparent; color:var(--accent); }
  .world-list { border:1px solid var(--border); border-radius:var(--radius); background:var(--surface); }
  .world-item { display:grid; grid-template-columns:auto minmax(0,1fr) auto; gap:13px; align-items:center; min-height:70px; padding:11px 13px; border-bottom:1px solid var(--border-soft); position:relative; transition:background-color 120ms ease; }
  .world-item:last-child { border-bottom:0; }
  .world-item:hover { background:rgba(255,255,255,.018); }
  .world-item.archived { opacity:.72; }
  .world-icon { width:40px; height:40px; display:grid; place-items:center; border-radius:9px; background:var(--surface-2); color:var(--muted); font-size:17px; }
  .world-info { min-width:0; }
  .world-name-row { display:flex; align-items:center; gap:8px; min-width:0; }
  .world-name-row strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:13px; }
  .world-meta { display:flex; gap:0; margin-top:5px; color:var(--muted); font-size:11px; }
  .world-meta span + span::before { content:'•'; margin:0 7px; color:var(--muted-2); }
  .status-pill { padding:3px 6px; border-radius:999px; background:var(--surface-3); color:var(--muted); font-size:9px; font-weight:750; text-transform:uppercase; letter-spacing:.04em; }
  .status-pill.loaded { background:#173523; color:#8fe1a8; }
  .world-actions { display:flex; align-items:center; gap:6px; }
  .menu { position:relative; }
  .menu summary { list-style:none; width:34px; height:32px; display:grid; place-items:center; border:1px solid transparent; border-radius:var(--radius-sm); color:var(--muted); cursor:pointer; user-select:none; }
  .menu summary::-webkit-details-marker { display:none; }
  .menu summary:hover,.menu[open] summary { background:var(--surface-2); border-color:var(--border); color:var(--text); }
  .menu-popover { position:absolute; z-index:20; right:0; top:38px; min-width:190px; padding:6px; border:1px solid var(--border); border-radius:10px; background:var(--surface-2); box-shadow:var(--shadow-popover); }
  .menu-popover button { display:block; width:100%; border:0; border-radius:7px; padding:8px 9px; text-align:left; background:transparent; color:var(--text); font-size:11px; }
  .menu-popover button:hover:not(:disabled) { background:var(--surface-3); }
  .menu-separator { height:1px; background:var(--border); margin:5px 3px; }
  .danger-text { color:#ff9a9a !important; }
  .task-strip { display:grid; grid-template-columns:minmax(160px,1fr) minmax(180px,340px) auto; align-items:center; gap:13px; padding:10px 12px; margin-bottom:12px; border:1px solid var(--border); border-radius:var(--radius-sm); background:var(--surface); }
  .task-strip > div:first-child { display:grid; gap:2px; }
  .task-strip span,.task-strip small { color:var(--muted); font-size:10px; }
  .progress-track { height:5px; background:var(--surface-3); border-radius:999px; overflow:hidden; }
  .progress-track span { display:block; height:100%; background:var(--accent); border-radius:999px; transition:width .2s ease; }
  .modal-backdrop { position:fixed; z-index:100; inset:0; display:grid; place-items:center; padding:24px; background:rgba(0,0,0,.68); backdrop-filter:blur(4px); }
  .modal { width:min(480px,100%); display:grid; gap:15px; padding:20px; border:1px solid var(--border); border-radius:var(--radius); background:var(--surface); box-shadow:var(--shadow-popover); }
  .settings-modal { width:min(600px,100%); }
  .modal-head { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; }
  .modal-head h2 { margin:0; font-size:18px; }
  .modal-head p { margin:4px 0 0; color:var(--muted); font-size:11px; }
  .icon-button { width:32px; height:32px; border:0; border-radius:var(--radius-sm); background:transparent; color:var(--muted); font-size:21px; line-height:1; }
  .icon-button:hover:not(:disabled) { background:var(--surface-2); color:var(--text); }
  .modal label { display:grid; gap:6px; color:var(--text-soft); font-size:11px; font-weight:600; }
  .modal input,.modal select { width:100%; min-height:38px; border:1px solid var(--border); background:var(--bg); color:var(--text); border-radius:var(--radius-sm); padding:8px 10px; outline:none; }
  .modal input:focus,.modal select:focus { border-color:color-mix(in srgb,var(--accent) 45%,var(--border)); }
  .modal-actions { display:flex; justify-content:flex-end; gap:8px; padding-top:3px; }
  .file-picker { display:grid; grid-template-columns:minmax(0,1fr) auto; align-items:center; gap:9px; padding:7px 7px 7px 10px; border:1px solid var(--border); border-radius:var(--radius-sm); background:var(--bg); }
  .file-picker span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:var(--muted); }
  .danger-callout { padding:10px 11px; border:1px solid #6c363d; border-radius:var(--radius-sm); background:var(--danger-bg); color:#f1c5c9; font-size:11px; line-height:1.45; }
  .danger-button { border:1px solid #a74650; background:#8d3039; color:#fff; }
  .settings-grid-local { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:9px; }
  .toggle-list { display:grid; border:1px solid var(--border); border-radius:var(--radius-sm); overflow:hidden; }
  .toggle-list label { display:flex; flex-direction:row; align-items:center; justify-content:space-between; gap:18px; padding:10px 11px; border-bottom:1px solid var(--border-soft); }
  .toggle-list label:last-child { border-bottom:0; }
  .toggle-list label > span { display:grid; gap:2px; }
  .toggle-list small { color:var(--muted); font-size:10px; }
  .toggle-list input { width:auto; }

  @media (max-width:760px) {
    .page-head { align-items:flex-start; flex-direction:column; }
    .head-actions { width:100%; flex-wrap:wrap; }
    .toolbar { align-items:stretch; flex-direction:column; }
    .world-count { display:none; }
    .world-item { grid-template-columns:auto minmax(0,1fr); }
    .world-actions { grid-column:2; justify-content:flex-start; }
    .settings-grid-local { grid-template-columns:1fr; }
    .task-strip { grid-template-columns:1fr; }
  }
</style>