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
      connectionStatus = `${worlds.length} managed world${worlds.length === 1 ? '' : 's'}`;
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
      operationTask = await runtimeProduct.worlds.clone({
        worldId: cloneSource.id,
        destinationFolder,
        displayName
      });
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
    if (!window.confirm(`Permanently delete ${deleteSource.displayName}? This cannot be undone.`)) return;

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

  function statusLabel(world: ManagedWorldSummary) {
    if (world.lifecycle === 'ARCHIVED') return 'Archived';
    return world.runtimeState === 'LOADED' ? 'Loaded' : 'Unloaded';
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

<section class="page-head">
  <div>
    <h1>Worlds</h1>
    <p>{serverOnline ? connectionStatus : 'Create, import and manage your server worlds.'}</p>
  </div>
  {#if serverOnline}
    <div class="head-actions">
      <button class="secondary compact" disabled={busy || importBusy || operationBusyWorldId !== null} onclick={refresh}>Refresh</button>
      <button class="secondary" disabled={busy || importBusy || operationBusyWorldId !== null} onclick={openImport}>Import</button>
      <button class="primary" disabled={busy || importBusy || operationBusyWorldId !== null} onclick={openCreate}>+ Create world</button>
    </div>
  {/if}
</section>

{#if error}
  <div class="notice error-notice">
    <strong>Couldn’t complete that action</strong>
    <span>{error}</span>
  </div>
{/if}

{#if !serverOnline}
  <section class="offline-state">
    <div class="offline-icon">◫</div>
    <h2>{connectionStatus === 'Server is starting…' ? 'Server is starting' : 'Server is offline'}</h2>
    <p>World management becomes available when the server is running.</p>
    <button class="primary" disabled={startingServer || connectionStatus === 'Server is starting…'} onclick={startServer}>
      {startingServer || connectionStatus === 'Server is starting…' ? 'Starting…' : 'Start server'}
    </button>
  </section>
{:else}
  {#if operationTask && ['QUEUED', 'RUNNING'].includes(operationTask.state)}
    <section class="task-strip">
      <div>
        <strong>{operationTask.taskType}</strong>
        <span>{operationTask.message || 'Working…'}</span>
      </div>
      <div class="progress-track"><span style={`width:${Math.max(2, operationTask.progressPercent)}%`}></span></div>
      <small>{operationTask.progressPercent}%</small>
    </section>
  {/if}

  {#if worlds.length === 0 && !busy && !error}
    <section class="empty-state">
      <div class="empty-icon">◇</div>
      <h2>No managed worlds yet</h2>
      <p>Create a new build world or import an existing Java world.</p>
      <div class="empty-actions">
        <button class="primary" onclick={openCreate}>Create world</button>
        <button class="secondary" onclick={openImport}>Import world</button>
      </div>
    </section>
  {:else}
    <div class="world-list">
      {#each worlds as world}
        <article class="world-item" class:archived={world.lifecycle === 'ARCHIVED'}>
          <div class="world-icon">{world.kind === 'VOID' ? '□' : '◆'}</div>
          <div class="world-info">
            <div class="world-name-row">
              <strong>{world.displayName}</strong>
              <span class="status-pill" class:loaded={world.runtimeState === 'LOADED'}>{statusLabel(world)}</span>
            </div>
            <div class="world-meta">
              <span>{world.kind === 'VOID' ? 'Void' : 'Flat'}</span>
              <span>{world.defaultGameMode.charAt(0) + world.defaultGameMode.slice(1).toLowerCase()}</span>
              {#if world.autoLoad}<span>Auto-load</span>{/if}
            </div>
          </div>

          <div class="world-actions">
            {#if world.lifecycle === 'ACTIVE'}
              <button class="secondary compact" disabled={busy || operationBusyWorldId !== null} onclick={() => toggleRuntime(world)}>
                {world.runtimeState === 'LOADED' ? 'Unload' : 'Load'}
              </button>
              <details class="menu">
                <summary aria-label={`Actions for ${world.displayName}`}>•••</summary>
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
                <summary aria-label={`Actions for ${world.displayName}`}>•••</summary>
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

{#if createOpen}
  <div class="modal-backdrop" role="presentation">
    <section class="modal" role="dialog" aria-modal="true" aria-labelledby="create-title">
      <div class="modal-head">
        <div><h2 id="create-title">Create world</h2><p>Set up a new world for building.</p></div>
        <button class="icon-button" onclick={() => (createOpen = false)}>×</button>
      </div>
      <label>World name<input bind:value={createName} placeholder="New Build World" autofocus /></label>
      <label>World type
        <select bind:value={createType}>
          <option value="FLAT">Flat world</option>
          <option value="VOID">Void world</option>
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
  <div class="modal-backdrop" role="presentation">
    <section class="modal" role="dialog" aria-modal="true" aria-labelledby="import-title">
      <div class="modal-head">
        <div><h2 id="import-title">Import world</h2><p>Import a Java world from a ZIP or MCWORLD file.</p></div>
        <button class="icon-button" onclick={() => (importOpen = false)}>×</button>
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
      <div class="modal-head"><div><h2>Clone world</h2><p>Create a copy of {cloneSource.displayName}.</p></div><button class="icon-button" onclick={closeOperationPanels}>×</button></div>
      <label>New world name<input bind:value={cloneName} disabled={operationBusyWorldId !== null} /></label>
      <div class="modal-actions"><button class="secondary" onclick={closeOperationPanels}>Cancel</button><button class="primary" disabled={operationBusyWorldId !== null || !cloneName.trim()} onclick={runClone}>Clone world</button></div>
    </section>
  </div>
{/if}

{#if exportSource}
  <div class="modal-backdrop" role="presentation">
    <section class="modal" role="dialog" aria-modal="true">
      <div class="modal-head"><div><h2>Export world</h2><p>Create a Java 1.21.4 ZIP of {exportSource.displayName}.</p></div><button class="icon-button" onclick={closeOperationPanels}>×</button></div>
      <label>Export name<input bind:value={exportName} disabled={operationBusyWorldId !== null} /></label>
      <div class="modal-actions"><button class="secondary" onclick={closeOperationPanels}>Cancel</button><button class="primary" disabled={operationBusyWorldId !== null || !exportName.trim()} onclick={runExport}>Export world</button></div>
    </section>
  </div>
{/if}

{#if deleteSource}
  <div class="modal-backdrop" role="presentation">
    <section class="modal danger-modal" role="dialog" aria-modal="true">
      <div class="modal-head"><div><h2>Delete {deleteSource.displayName}?</h2><p>This permanently removes the managed world.</p></div><button class="icon-button" onclick={closeOperationPanels}>×</button></div>
      <div class="danger-callout">This action cannot be undone. Type <strong>{deleteSource.displayName}</strong> to confirm.</div>
      <label>World name<input bind:value={deleteConfirmation} disabled={operationBusyWorldId !== null} /></label>
      <div class="modal-actions"><button class="secondary" onclick={closeOperationPanels}>Cancel</button><button class="danger-button" disabled={operationBusyWorldId !== null || deleteConfirmation.trim() !== deleteSource.displayName} onclick={runDelete}>Delete permanently</button></div>
    </section>
  </div>
{/if}

{#if settings}
  <div class="modal-backdrop" role="presentation">
    <section class="modal settings-modal" role="dialog" aria-modal="true">
      <div class="modal-head"><div><h2>{settings.displayName}</h2><p>World settings</p></div><button class="icon-button" disabled={settingsBusy} onclick={() => (settings = null)}>×</button></div>
      <div class="settings-grid-local">
        <label>Default game mode<select bind:value={settings.defaultGameMode} disabled={settingsBusy}><option value="CREATIVE">Creative</option><option value="SURVIVAL">Survival</option><option value="ADVENTURE">Adventure</option><option value="SPECTATOR">Spectator</option></select></label>
        <label>Weather<select bind:value={settings.weather} disabled={settingsBusy}><option value="CLEAR">Clear</option><option value="RAIN">Rain</option><option value="THUNDER">Thunder</option></select></label>
        <label>Time<input type="number" min="0" max="23999" bind:value={settings.timeOfDayTicks} disabled={settingsBusy} /></label>
      </div>
      <div class="toggle-list">
        <label><span><strong>Auto-load</strong><small>Load this world when the server starts.</small></span><input type="checkbox" bind:checked={settings.autoLoad} disabled={settingsBusy} /></label>
        <label><span><strong>Mob spawning</strong><small>Allow natural mob spawning.</small></span><input type="checkbox" bind:checked={settings.naturalSpawning} disabled={settingsBusy} /></label>
        <label><span><strong>Daylight cycle</strong><small>Allow time to progress normally.</small></span><input type="checkbox" bind:checked={settings.daylightCycle} disabled={settingsBusy} /></label>
        <label><span><strong>Weather cycle</strong><small>Allow weather to change naturally.</small></span><input type="checkbox" bind:checked={settings.weatherCycle} disabled={settingsBusy} /></label>
      </div>
      <div class="modal-actions"><button class="secondary" disabled={settingsBusy} onclick={() => (settings = null)}>Cancel</button><button class="primary" disabled={settingsBusy} onclick={saveSettings}>{settingsBusy ? 'Saving…' : 'Save changes'}</button></div>
    </section>
  </div>
{/if}

<style>
  .page-head { display:flex; justify-content:space-between; align-items:flex-start; gap:24px; margin-bottom:22px; }
  .page-head h1 { margin:0; font-size:26px; letter-spacing:-.02em; }
  .page-head p { margin:6px 0 0; color:var(--muted); font-size:13px; }
  .head-actions, .empty-actions { display:flex; align-items:center; gap:8px; }
  button { font:inherit; cursor:pointer; }
  button:disabled { cursor:default; opacity:.48; }
  .primary, .secondary, .danger-button { border-radius:9px; padding:9px 13px; font-weight:650; }
  .primary { border:1px solid var(--accent); background:var(--accent); color:#07120b; }
  .secondary { border:1px solid var(--border); background:var(--surface-2); color:var(--text); }
  .secondary:hover:not(:disabled) { border-color:#4a535c; background:#293036; }
  .compact { padding:7px 10px; font-size:12px; }
  .notice { display:grid; gap:3px; padding:12px 14px; border-radius:10px; margin-bottom:16px; font-size:13px; }
  .error-notice { border:1px solid #713940; background:#321b1f; color:#ffdadd; }
  .error-notice span { color:#efb9be; }
  .offline-state, .empty-state { min-height:360px; display:flex; flex-direction:column; align-items:center; justify-content:center; text-align:center; border:1px dashed var(--border); border-radius:14px; background:rgba(255,255,255,.012); padding:40px; }
  .offline-state h2, .empty-state h2 { margin:14px 0 6px; font-size:20px; }
  .offline-state p, .empty-state p { margin:0 0 18px; color:var(--muted); font-size:13px; max-width:390px; }
  .offline-icon, .empty-icon { width:48px; height:48px; display:grid; place-items:center; border-radius:12px; background:var(--surface-2); border:1px solid var(--border); font-size:22px; color:var(--muted); }
  .world-list { border:1px solid var(--border); border-radius:13px; overflow:visible; background:var(--surface); }
  .world-item { display:grid; grid-template-columns:auto minmax(0,1fr) auto; gap:14px; align-items:center; min-height:76px; padding:13px 14px; border-bottom:1px solid var(--border); position:relative; }
  .world-item:last-child { border-bottom:0; }
  .world-item:hover { background:rgba(255,255,255,.018); }
  .world-item.archived { opacity:.75; }
  .world-icon { width:42px; height:42px; display:grid; place-items:center; border-radius:10px; background:var(--surface-2); color:var(--muted); font-size:18px; }
  .world-info { min-width:0; }
  .world-name-row { display:flex; align-items:center; gap:8px; min-width:0; }
  .world-name-row strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
  .world-meta { display:flex; gap:0; margin-top:7px; color:var(--muted); font-size:12px; }
  .world-meta span + span::before { content:'•'; margin:0 7px; color:#626c76; }
  .status-pill { padding:3px 7px; border-radius:999px; background:#2a2f34; color:#aab3bc; font-size:10px; font-weight:700; text-transform:uppercase; letter-spacing:.04em; }
  .status-pill.loaded { background:#173523; color:#8fe1a8; }
  .world-actions { display:flex; align-items:center; gap:7px; }
  .menu { position:relative; }
  .menu summary { list-style:none; width:34px; height:32px; display:grid; place-items:center; border:1px solid transparent; border-radius:8px; color:var(--muted); cursor:pointer; user-select:none; }
  .menu summary::-webkit-details-marker { display:none; }
  .menu summary:hover, .menu[open] summary { background:var(--surface-2); border-color:var(--border); color:var(--text); }
  .menu-popover { position:absolute; z-index:20; right:0; top:38px; min-width:185px; padding:6px; border:1px solid var(--border); border-radius:10px; background:#202429; box-shadow:0 14px 40px rgba(0,0,0,.38); }
  .menu-popover button { display:block; width:100%; border:0; border-radius:7px; padding:8px 9px; text-align:left; background:transparent; color:var(--text); font-size:12px; }
  .menu-popover button:hover:not(:disabled) { background:#2b3137; }
  .menu-separator { height:1px; background:var(--border); margin:5px 3px; }
  .danger-text { color:#ff9a9a !important; }
  .task-strip { display:grid; grid-template-columns:minmax(160px,1fr) minmax(180px,340px) auto; align-items:center; gap:14px; padding:11px 13px; margin-bottom:14px; border:1px solid var(--border); border-radius:10px; background:var(--surface); }
  .task-strip > div:first-child { display:grid; gap:2px; }
  .task-strip span, .task-strip small { color:var(--muted); font-size:11px; }
  .progress-track { height:5px; background:#2a3035; border-radius:999px; overflow:hidden; }
  .progress-track span { display:block; height:100%; background:var(--accent); border-radius:999px; transition:width .2s ease; }
  .modal-backdrop { position:fixed; z-index:100; inset:0; display:grid; place-items:center; padding:28px; background:rgba(0,0,0,.62); backdrop-filter:blur(3px); }
  .modal { width:min(480px,100%); display:grid; gap:16px; padding:20px; border:1px solid #3b434b; border-radius:14px; background:#1b1f23; box-shadow:0 24px 80px rgba(0,0,0,.52); }
  .settings-modal { width:min(620px,100%); }
  .modal-head { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; }
  .modal-head h2 { margin:0; font-size:19px; }
  .modal-head p { margin:5px 0 0; color:var(--muted); font-size:12px; }
  .icon-button { border:0; background:transparent; color:var(--muted); padding:0 4px; font-size:24px; line-height:1; }
  .modal label { display:grid; gap:7px; color:#cbd1d7; font-size:12px; }
  .modal input, .modal select { width:100%; border:1px solid var(--border); background:#111417; color:var(--text); border-radius:9px; padding:10px 11px; outline:none; }
  .modal input:focus, .modal select:focus { border-color:#65717c; }
  .modal-actions { display:flex; justify-content:flex-end; gap:8px; padding-top:3px; }
  .file-picker { display:grid; grid-template-columns:minmax(0,1fr) auto; align-items:center; gap:9px; padding:8px 8px 8px 11px; border:1px solid var(--border); border-radius:9px; background:#111417; }
  .file-picker span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:var(--muted); }
  .danger-callout { padding:11px 12px; border:1px solid #6c363d; border-radius:9px; background:#321b1f; color:#f1c5c9; font-size:12px; line-height:1.45; }
  .danger-button { border:1px solid #a74650; background:#8d3039; color:#fff; }
  .settings-grid-local { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:10px; }
  .toggle-list { display:grid; border:1px solid var(--border); border-radius:10px; overflow:hidden; }
  .toggle-list label { display:flex; flex-direction:row; align-items:center; justify-content:space-between; gap:18px; padding:11px 12px; border-bottom:1px solid var(--border); }
  .toggle-list label:last-child { border-bottom:0; }
  .toggle-list label > span { display:grid; gap:2px; }
  .toggle-list small { color:var(--muted); font-size:11px; }
  .toggle-list input { width:auto; }
  @media (max-width:760px) {
    .page-head { flex-direction:column; }
    .head-actions { width:100%; flex-wrap:wrap; }
    .world-item { grid-template-columns:auto minmax(0,1fr); }
    .world-actions { grid-column:2; justify-content:flex-start; }
    .settings-grid-local { grid-template-columns:1fr; }
    .task-strip { grid-template-columns:1fr; }
  }
</style>