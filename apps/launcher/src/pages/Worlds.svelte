<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import { RuntimeError } from '../app/bridge/runtimeApi';
  import type { ManagedWorldSummary, ServerState, UpdateWorldSettingsRequest, WorldSettingsSnapshot, WorldTaskSnapshot } from '../app/bridge/runtimeApi';

  const TASK_TIMEOUT_MS = 30 * 60 * 1000;
  const TASK_POLL_VISIBLE_MS = 1000;
  const TASK_POLL_HIDDEN_MS = 5000;

  let worlds: ManagedWorldSummary[] = [];
  let search = '';
  let error = '';
  let busy = false;
  let serverState: ServerState = 'Offline';
  let serverOnline = false;
  let operationBusyWorldId: string | null = null;
  let operationTask: WorldTaskSnapshot | null = null;
  let pageActive = false;

  let createOpen = false;
  let createName = '';
  let createType: 'FLAT' | 'VOID' = 'FLAT';
  let importOpen = false;
  let importPath = '';
  let importName = '';
  let importBusy = false;
  let settings: WorldSettingsSnapshot | null = null;
  let settingsBusy = false;
  let duplicateSource: ManagedWorldSummary | null = null;
  let duplicateName = '';
  let exportSource: ManagedWorldSummary | null = null;
  let exportName = '';
  let deleteSource: ManagedWorldSummary | null = null;
  let deleteConfirmation = '';

  function friendlyError(value: unknown) {
    if (value instanceof RuntimeError && value.code === 'WORLD_PROTOCOL_MISMATCH') {
      return 'World Manager is out of sync with this Launcher build. Update the LazyBuilder core components before continuing.';
    }
    if (value instanceof Error && value.message.trim()) return value.message.trim();
    return String(value).replace(/^Error:\s*/i, '').trim() || 'Something went wrong. Try again.';
  }

  function slugify(value: string) {
    return value.toLowerCase().replace(/[^a-z0-9_-]+/g, '-').replace(/^-+|-+$/g, '');
  }

  function folderName(value: string, prefix: string) {
    return slugify(value) || `${prefix}-${Date.now().toString(36)}`;
  }

  function kindLabel(world: ManagedWorldSummary) {
    if (world.kind === 'VOID') return 'Void';
    if (world.kind === 'IMPORTED') return 'Imported';
    return 'Flat';
  }

  function modeLabel(value: string) {
    if (!value) return '';
    return value.charAt(0) + value.slice(1).toLowerCase();
  }

  function visibleWorlds() {
    const query = search.trim().toLowerCase();
    if (!query) return worlds;
    return worlds.filter((world) => `${world.displayName} ${world.kind} ${world.defaultGameMode} ${world.lifecycle}`.toLowerCase().includes(query));
  }

  function activeWorlds() {
    return visibleWorlds().filter((world) => world.lifecycle === 'ACTIVE');
  }

  function archivedWorlds() {
    return visibleWorlds().filter((world) => world.lifecycle === 'ARCHIVED');
  }

  function closePanels() {
    duplicateSource = null;
    exportSource = null;
    deleteSource = null;
    deleteConfirmation = '';
  }

  async function refresh() {
    if (busy || !pageActive) return;
    busy = true;
    try {
      const snapshot = await runtimeProduct.server.snapshot();
      if (!pageActive) return;
      serverState = snapshot.state;
      serverOnline = snapshot.state === 'Online';
      if (!serverOnline) {
        worlds = [];
        settings = null;
        closePanels();
        return;
      }
      const next = await runtimeProduct.worlds.list();
      if (!pageActive) return;
      worlds = [...next].sort((a, b) => a.lifecycle.localeCompare(b.lifecycle) || a.displayName.localeCompare(b.displayName));
      error = '';
    } catch (e) {
      if (!pageActive) return;
      worlds = [];
      error = friendlyError(e);
    } finally {
      busy = false;
    }
  }

  async function createWorld() {
    const displayName = createName.trim();
    if (!displayName || !serverOnline || busy) return;
    busy = true;
    error = '';
    try {
      await runtimeProduct.worlds.create({
        folderName: folderName(displayName, 'world'),
        displayName,
        kind: createType
      });
      if (!pageActive) return;
      createOpen = false;
      createName = '';
    } catch (e) {
      if (pageActive) error = friendlyError(e);
    } finally {
      busy = false;
      if (pageActive) await refresh();
    }
  }

  async function pickImport() {
    if (importBusy) return;
    try {
      const selected = await runtimeProduct.worlds.pickImport();
      if (!pageActive || !selected) return;
      importPath = selected;
      const fileName = selected.split(/[\\/]/).pop() || 'Imported World';
      importName = fileName.replace(/\.(zip|mcworld)$/i, '').replace(/[-_]+/g, ' ').trim() || 'Imported World';
      error = '';
    } catch (e) {
      if (pageActive) error = friendlyError(e);
    }
  }

  async function runImport() {
    const displayName = importName.trim();
    if (!serverOnline || importBusy || !importPath || !displayName) return;
    importBusy = true;
    error = '';
    try {
      const artifactName = await runtimeProduct.worlds.uploadImport(importPath);
      if (!pageActive) return;
      operationTask = await runtimeProduct.worlds.import({
        artifactName,
        destinationFolder: folderName(displayName, 'imported-world'),
        displayName
      });
      importOpen = false;
      const completedHere = await pollTask(operationTask.taskId);
      if (!completedHere || !pageActive) return;
      importPath = '';
      importName = '';
    } catch (e) {
      if (pageActive) error = friendlyError(e);
    } finally {
      importBusy = false;
      if (pageActive) {
        operationTask = null;
        await refresh();
      }
    }
  }

  async function pollTask(taskId: string): Promise<boolean> {
    const startedAt = Date.now();
    while (pageActive && Date.now() - startedAt < TASK_TIMEOUT_MS) {
      const task = await runtimeProduct.worlds.task(taskId);
      if (!pageActive) return false;
      operationTask = task;
      operationBusyWorldId = task.worldId ?? operationBusyWorldId ?? '__world-task__';
      if (task.state === 'SUCCEEDED') return true;
      if (task.state === 'FAILED') throw new Error(task.error || task.message || 'World task failed.');
      await new Promise((resolve) => window.setTimeout(resolve, document.hidden ? TASK_POLL_HIDDEN_MS : TASK_POLL_VISIBLE_MS));
    }
    if (!pageActive) return false;
    throw new Error('This world operation is taking unusually long. Check the server status and logs before trying again.');
  }

  async function recoverActiveTask() {
    if (!pageActive || !serverOnline || operationTask) return;
    try {
      const tasks = await runtimeProduct.worlds.tasks();
      if (!pageActive) return;
      const active = tasks
        .filter((task) => task.state === 'QUEUED' || task.state === 'RUNNING')
        .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))[0];
      if (!active) return;
      operationTask = active;
      operationBusyWorldId = active.worldId ?? '__world-task__';
      const completedHere = await pollTask(active.taskId);
      if (!completedHere || !pageActive) return;
      operationTask = null;
      operationBusyWorldId = null;
      await refresh();
    } catch (e) {
      if (!pageActive) return;
      operationTask = null;
      operationBusyWorldId = null;
      error = friendlyError(e);
      await refresh();
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
      if (pageActive) error = friendlyError(e);
    } finally {
      if (pageActive) {
        operationBusyWorldId = null;
        operationTask = null;
        await refresh();
      }
    }
  }

  async function openSettings(world: ManagedWorldSummary) {
    if (settingsBusy || operationBusyWorldId) return;
    settingsBusy = true;
    error = '';
    try {
      settings = await runtimeProduct.worlds.settings(world.id);
    } catch (e) {
      if (pageActive) error = friendlyError(e);
    } finally {
      settingsBusy = false;
    }
  }

  async function saveSettings() {
    if (!settings || settingsBusy) return;
    settingsBusy = true;
    error = '';
    try {
      const timeOfDayTicks = Number(settings.timeOfDayTicks);
      if (!Number.isFinite(timeOfDayTicks) || timeOfDayTicks < 0 || timeOfDayTicks > 23999) {
        throw new Error('Time must be between 0 and 23999.');
      }
      const request: UpdateWorldSettingsRequest = {
        defaultGameMode: settings.defaultGameMode,
        timeOfDayTicks: Math.round(timeOfDayTicks),
        weather: settings.weather,
        naturalSpawning: settings.naturalSpawning,
        daylightCycle: settings.daylightCycle,
        weatherCycle: settings.weatherCycle
      };
      await runtimeProduct.worlds.updateSettings(settings.id, request);
      if (!pageActive) return;
      settings = null;
      await refresh();
    } catch (e) {
      if (pageActive) error = friendlyError(e);
    } finally {
      settingsBusy = false;
    }
  }

  async function runDuplicate() {
    if (!duplicateSource || operationBusyWorldId || !duplicateName.trim()) return;
    operationBusyWorldId = duplicateSource.id;
    error = '';
    try {
      operationTask = await runtimeProduct.worlds.duplicate({
        worldId: duplicateSource.id,
        destinationFolder: folderName(duplicateName, 'world-copy'),
        displayName: duplicateName.trim()
      });
      if (!pageActive) return;
      closePanels();
      await pollTask(operationTask.taskId);
    } catch (e) {
      if (pageActive) error = friendlyError(e);
    } finally {
      if (pageActive) {
        operationBusyWorldId = null;
        operationTask = null;
        await refresh();
      }
    }
  }

  async function runExport() {
    if (!exportSource || operationBusyWorldId || !exportName.trim()) return;
    operationBusyWorldId = exportSource.id;
    error = '';
    try {
      operationTask = await runtimeProduct.worlds.export({
        worldId: exportSource.id,
        targetFormat: 'JAVA_1_21_4',
        artifactName: exportName.trim()
      });
      if (!pageActive) return;
      closePanels();
      await pollTask(operationTask.taskId);
    } catch (e) {
      if (pageActive) error = friendlyError(e);
    } finally {
      if (pageActive) {
        operationBusyWorldId = null;
        operationTask = null;
        await refresh();
      }
    }
  }

  async function runDelete() {
    if (!deleteSource || deleteConfirmation.trim() !== deleteSource.displayName || operationBusyWorldId) return;
    operationBusyWorldId = deleteSource.id;
    error = '';
    try {
      operationTask = await runtimeProduct.worlds.delete({
        worldId: deleteSource.id,
        typedDisplayName: deleteConfirmation.trim()
      });
      if (!pageActive) return;
      closePanels();
      await pollTask(operationTask.taskId);
    } catch (e) {
      if (pageActive) error = friendlyError(e);
    } finally {
      if (pageActive) {
        operationBusyWorldId = null;
        operationTask = null;
        await refresh();
      }
    }
  }

  onMount(() => {
    pageActive = true;
    void (async () => {
      await refresh();
      if (pageActive && serverOnline) await recoverActiveTask();
    })();
    return () => { pageActive = false; };
  });
</script>

<section class="worlds-page">
  <header class="page-head">
    <div>
      <h2>Worlds</h2>
      <p>Create, import, back up and organize build worlds.</p>
    </div>
    {#if serverOnline}
      <div class="head-actions">
        <button class="secondary" disabled={busy || importBusy || operationBusyWorldId !== null} onclick={() => { importOpen = true; if (!importPath) void pickImport(); }}>Import</button>
        <button class="primary" disabled={busy || importBusy || operationBusyWorldId !== null} onclick={() => { createName = ''; createType = 'FLAT'; createOpen = true; }}>Create world</button>
      </div>
    {/if}
  </header>

  {#if error}<div class="notice error" role="alert">{error}</div>{/if}

  {#if !serverOnline}
    <section class="state-card">
      <div class="state-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24"><path d="M4 7.5 12 3l8 4.5v9L12 21l-8-4.5zM4 7.5l8 4.5 8-4.5M12 12v9" /></svg>
      </div>
      <h3>{serverState === 'Starting' ? 'Server is starting' : serverState === 'Stopping' ? 'Server is stopping' : 'Server must be running to manage worlds'}</h3>
      <p>{serverState === 'Starting' || serverState === 'Stopping' ? 'World controls will become available when the server is ready.' : 'Start this server from Overview, then return to Worlds.'}</p>
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
        <span aria-hidden="true">⌕</span>
        <input bind:value={search} placeholder="Search worlds" aria-label="Search worlds" />
      </label>
    {/if}

    {#if worlds.length === 0 && !busy}
      <section class="state-card">
        <div class="state-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M4 7.5 12 3l8 4.5v9L12 21l-8-4.5zM4 7.5l8 4.5 8-4.5M12 12v9" /></svg></div>
        <h3>No worlds yet</h3>
        <p>Create a clean world for building, or import one you already use.</p>
        <div class="state-actions"><button class="primary" onclick={() => { createName = ''; createType = 'FLAT'; createOpen = true; }}>Create world</button><button class="secondary" onclick={() => { importOpen = true; if (!importPath) void pickImport(); }}>Import</button></div>
      </section>
    {:else if visibleWorlds().length === 0}
      <section class="search-empty"><strong>No matching worlds</strong><span>Try another world name.</span><button onclick={() => (search = '')}>Clear search</button></section>
    {:else}
      {#if activeWorlds().length > 0}
        <div class="world-list">
          {#each activeWorlds() as world}
            <article class="world-row">
              <div class="world-icon" aria-hidden="true">
                {#if world.kind === 'VOID'}
                  <svg viewBox="0 0 24 24"><rect x="4" y="4" width="16" height="16" rx="2" /></svg>
                {:else}
                  <svg viewBox="0 0 24 24"><path d="M4 7.5 12 3l8 4.5v9L12 21l-8-4.5zM4 7.5l8 4.5 8-4.5M12 12v9" /></svg>
                {/if}
              </div>
              <div class="world-copy">
                <strong>{world.displayName}</strong>
                <div class="world-meta"><span>{kindLabel(world)}</span><span>{modeLabel(world.defaultGameMode)}</span></div>
              </div>
              <details class="menu">
                <summary aria-label={`Actions for ${world.displayName}`} title="World actions">•••</summary>
                <div class="menu-popover">
                  <button disabled={operationBusyWorldId !== null || settingsBusy} onclick={() => openSettings(world)}>World settings</button>
                  <div class="menu-separator"></div>
                  <button disabled={operationBusyWorldId !== null} onclick={() => runWorldTask(world, 'backup')}>Create backup</button>
                  <button disabled={operationBusyWorldId !== null} onclick={() => { closePanels(); duplicateSource = world; duplicateName = `${world.displayName} Copy`; }}>Duplicate world…</button>
                  <button disabled={operationBusyWorldId !== null} onclick={() => { closePanels(); exportSource = world; exportName = `${slugify(world.displayName) || 'world'}-export`; }}>Export…</button>
                  <button disabled={operationBusyWorldId !== null} onclick={() => runWorldTask(world, 'archive')}>Archive</button>
                  <div class="menu-separator"></div>
                  <button class="danger" disabled={operationBusyWorldId !== null} onclick={() => { closePanels(); deleteSource = world; }}>Delete permanently…</button>
                </div>
              </details>
            </article>
          {/each}
        </div>
      {/if}

      {#if archivedWorlds().length > 0}
        <details class="archived-section">
          <summary>Archived worlds <span>{archivedWorlds().length}</span></summary>
          <div class="world-list archived-list">
            {#each archivedWorlds() as world}
              <article class="world-row archived">
                <div class="world-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M5 7h14v12H5zM4 4h16v3H4zM9 11h6" /></svg></div>
                <div class="world-copy"><strong>{world.displayName}</strong><div class="world-meta"><span>Archived</span></div></div>
                <div class="archived-actions">
                  <button class="secondary compact" disabled={operationBusyWorldId !== null} onclick={() => runWorldTask(world, 'restore')}>Restore</button>
                  <details class="menu"><summary aria-label={`Actions for ${world.displayName}`} title="World actions">•••</summary><div class="menu-popover"><button class="danger" disabled={operationBusyWorldId !== null} onclick={() => { closePanels(); deleteSource = world; }}>Delete permanently…</button></div></details>
                </div>
              </article>
            {/each}
          </div>
        </details>
      {/if}
    {/if}
  {/if}
</section>

{#if createOpen}
  <div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !busy && (createOpen = false)}>
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="create-world-title">
      <div class="modal-head"><div><h2 id="create-world-title">Create world</h2><p>Start with a simple space made for building.</p></div><button class="icon-button" aria-label="Close" disabled={busy} onclick={() => (createOpen = false)}>×</button></div>
      <label>World name<input bind:value={createName} placeholder="Build World" disabled={busy} /></label>
      <label>World type<select bind:value={createType} disabled={busy}><option value="FLAT">Flat — normal building surface</option><option value="VOID">Void — empty building space</option></select></label>
      <div class="modal-actions"><button class="secondary" disabled={busy} onclick={() => (createOpen = false)}>Cancel</button><button class="primary" disabled={busy || !createName.trim()} onclick={createWorld}>{busy ? 'Creating…' : 'Create world'}</button></div>
    </div>
  </div>
{/if}

{#if importOpen}
  <div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !importBusy && (importOpen = false)}>
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="import-world-title">
      <div class="modal-head"><div><h2 id="import-world-title">Import world</h2><p>Add an existing Java world to this server.</p></div><button class="icon-button" aria-label="Close" disabled={importBusy} onclick={() => (importOpen = false)}>×</button></div>
      <label>World file<div class="file-picker"><span title={importPath}>{importPath ? importPath.split(/[\\/]/).pop() : 'No file selected'}</span><button class="secondary compact" disabled={importBusy} onclick={pickImport}>Choose file</button></div></label>
      <label>World name<input bind:value={importName} disabled={!importPath || importBusy} placeholder="Imported World" /></label>
      <div class="modal-actions"><button class="secondary" disabled={importBusy} onclick={() => (importOpen = false)}>Cancel</button><button class="primary" disabled={importBusy || !importPath || !importName.trim()} onclick={runImport}>{importBusy ? 'Importing…' : 'Import world'}</button></div>
    </div>
  </div>
{/if}

{#if settings}
  <div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !settingsBusy && (settings = null)}>
    <div class="modal settings-modal" role="dialog" aria-modal="true" aria-labelledby="world-settings-title">
      <div class="modal-head"><div><h2 id="world-settings-title">{settings.displayName}</h2><p>World settings</p></div><button class="icon-button" aria-label="Close" disabled={settingsBusy} onclick={() => (settings = null)}>×</button></div>
      <div class="settings-grid">
        <label>Game mode<select bind:value={settings.defaultGameMode} disabled={settingsBusy}><option value="CREATIVE">Creative</option><option value="SURVIVAL">Survival</option><option value="ADVENTURE">Adventure</option><option value="SPECTATOR">Spectator</option></select></label>
        <label>Weather<select bind:value={settings.weather} disabled={settingsBusy}><option value="CLEAR">Clear</option><option value="RAIN">Rain</option><option value="THUNDER">Thunder</option></select></label>
        <label>Time <span class="field-hint">0–23999</span><input type="number" min="0" max="23999" bind:value={settings.timeOfDayTicks} disabled={settingsBusy} /></label>
      </div>
      <div class="toggle-list">
        <label><span><strong>Mob spawning</strong><small>Allow natural mobs to spawn.</small></span><input type="checkbox" bind:checked={settings.naturalSpawning} disabled={settingsBusy} /></label>
        <label><span><strong>Daylight cycle</strong><small>Let time move normally.</small></span><input type="checkbox" bind:checked={settings.daylightCycle} disabled={settingsBusy} /></label>
        <label><span><strong>Weather cycle</strong><small>Let weather change naturally.</small></span><input type="checkbox" bind:checked={settings.weatherCycle} disabled={settingsBusy} /></label>
      </div>
      <div class="modal-actions"><button class="secondary" disabled={settingsBusy} onclick={() => (settings = null)}>Cancel</button><button class="primary" disabled={settingsBusy} onclick={saveSettings}>{settingsBusy ? 'Saving…' : 'Save changes'}</button></div>
    </div>
  </div>
{/if}

{#if duplicateSource}
  <div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && operationBusyWorldId === null && closePanels()}>
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="duplicate-world-title">
      <div class="modal-head"><div><h2 id="duplicate-world-title">Duplicate world</h2><p>Create a separate copy of {duplicateSource.displayName}.</p></div><button class="icon-button" aria-label="Close" disabled={operationBusyWorldId !== null} onclick={closePanels}>×</button></div>
      <label>New world name<input bind:value={duplicateName} disabled={operationBusyWorldId !== null} /></label>
      <div class="modal-actions"><button class="secondary" disabled={operationBusyWorldId !== null} onclick={closePanels}>Cancel</button><button class="primary" disabled={operationBusyWorldId !== null || !duplicateName.trim()} onclick={runDuplicate}>{operationBusyWorldId ? 'Duplicating…' : 'Duplicate'}</button></div>
    </div>
  </div>
{/if}

{#if exportSource}
  <div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && operationBusyWorldId === null && closePanels()}>
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="export-world-title">
      <div class="modal-head"><div><h2 id="export-world-title">Export world</h2><p>Save {exportSource.displayName} as a Java world ZIP.</p></div><button class="icon-button" aria-label="Close" disabled={operationBusyWorldId !== null} onclick={closePanels}>×</button></div>
      <label>Export name<input bind:value={exportName} disabled={operationBusyWorldId !== null} /></label>
      <div class="modal-actions"><button class="secondary" disabled={operationBusyWorldId !== null} onclick={closePanels}>Cancel</button><button class="primary" disabled={operationBusyWorldId !== null || !exportName.trim()} onclick={runExport}>{operationBusyWorldId ? 'Exporting…' : 'Export'}</button></div>
    </div>
  </div>
{/if}

{#if deleteSource}
  <div class="modal-backdrop" role="presentation">
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="delete-world-title">
      <div class="modal-head"><div><h2 id="delete-world-title">Delete {deleteSource.displayName}?</h2><p>This permanently removes the world from this server.</p></div><button class="icon-button" aria-label="Close" disabled={operationBusyWorldId !== null} onclick={closePanels}>×</button></div>
      <div class="danger-callout">This cannot be undone. Type <strong>{deleteSource.displayName}</strong> to confirm.</div>
      <label>World name<input bind:value={deleteConfirmation} disabled={operationBusyWorldId !== null} /></label>
      <div class="modal-actions"><button class="secondary" disabled={operationBusyWorldId !== null} onclick={closePanels}>Cancel</button><button class="danger-button" disabled={operationBusyWorldId !== null || deleteConfirmation.trim() !== deleteSource.displayName} onclick={runDelete}>{operationBusyWorldId ? 'Deleting…' : 'Delete permanently'}</button></div>
    </div>
  </div>
{/if}

<style>
  .worlds-page{width:min(920px,100%)}
  .page-head{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;margin-bottom:16px}.page-head h2{margin:0;font-size:18px}.page-head p{margin:4px 0 0;color:var(--muted);font-size:12px}
  .head-actions,.state-actions,.modal-actions,.archived-actions{display:flex;gap:8px;align-items:center}
  .primary,.secondary,.danger-button{min-height:var(--control-height);border-radius:var(--radius-sm);padding:8px 12px;font-weight:650;cursor:pointer;transition:background-color 120ms ease,border-color 120ms ease,transform 120ms ease}
  .primary{border:1px solid var(--accent);background:var(--accent);color:var(--accent-ink)}.primary:hover:not(:disabled){background:var(--accent-hover);border-color:var(--accent-hover)}
  .secondary{border:1px solid var(--border);background:var(--surface-2);color:var(--text)}.secondary:hover:not(:disabled){background:var(--surface-3);border-color:var(--border-strong)}
  .primary:active:not(:disabled),.secondary:active:not(:disabled),.danger-button:active:not(:disabled){transform:scale(.98)}
  .compact{min-height:32px;padding:6px 9px;font-size:11px}.danger-button{border:1px solid #a74650;background:#8d3039;color:#fff}button:disabled{opacity:.5;cursor:default}
  .notice{margin-bottom:12px;padding:10px 12px;border:1px solid #70343a;border-radius:var(--radius-sm);background:var(--danger-bg);color:#ffd9dc;font-size:12px}
  .state-card{min-height:260px;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;border:1px dashed var(--border);border-radius:var(--radius);background:var(--bg-elevated);padding:32px}
  .state-icon{width:44px;height:44px;display:grid;place-items:center;border-radius:11px;background:var(--surface-2);color:var(--muted)}
  .state-icon svg,.world-icon svg{width:20px;height:20px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}
  .state-card h3{margin:12px 0 5px;font-size:16px}.state-card p{max-width:420px;margin:0 0 16px;color:var(--muted);font-size:12px}
  .search-field{display:flex;align-items:center;gap:8px;margin-bottom:12px;padding:0 11px;min-height:var(--control-height);border:1px solid var(--border-soft);border-radius:var(--radius-sm);background:var(--surface);color:var(--muted)}
  .search-field:focus-within{border-color:color-mix(in srgb,var(--accent) 45%,var(--border));box-shadow:0 0 0 3px color-mix(in srgb,var(--accent) 10%,transparent)}.search-field input{width:100%;border:0;outline:0;background:transparent;color:var(--text)}
  .task-strip{display:grid;grid-template-columns:minmax(0,1fr) minmax(160px,320px) auto;gap:12px;align-items:center;margin-bottom:12px;padding:10px 12px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--surface);font-size:11px}
  .progress{height:5px;overflow:hidden;border-radius:999px;background:var(--surface-3)}.progress i{display:block;height:100%;background:var(--accent)}
  .world-list{overflow:visible;border:1px solid var(--border-soft);border-radius:var(--radius);background:var(--surface);box-shadow:var(--shadow-card)}
  .world-row{position:relative;display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:12px;min-height:64px;padding:10px 12px;border-bottom:1px solid var(--border-soft);transition:background-color 120ms ease}.world-row:last-child{border-bottom:0}.world-row:hover{background:rgba(255,255,255,.018)}.world-row.archived{opacity:.76}
  .world-icon{width:36px;height:36px;display:grid;place-items:center;border-radius:9px;background:var(--surface-2);color:var(--muted)}.world-icon svg{width:18px;height:18px}
  .world-copy{min-width:0;display:grid;gap:3px}.world-copy>strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px}.world-meta{display:flex;align-items:center;flex-wrap:wrap;color:var(--muted);font-size:10px}.world-meta span+span::before{content:'•';margin:0 6px;color:var(--muted-2)}
  .menu{position:relative}.menu summary{list-style:none;width:34px;height:32px;display:grid;place-items:center;border-radius:var(--radius-sm);color:var(--muted);cursor:pointer;font-weight:800}.menu summary::-webkit-details-marker{display:none}.menu summary:hover,.menu[open] summary{background:var(--surface-2);color:var(--text)}
  .menu-popover{position:absolute;z-index:20;right:0;top:38px;width:194px;padding:6px;border:1px solid var(--border);border-radius:var(--radius);background:var(--surface-2);box-shadow:var(--shadow-popover)}.menu-popover button{width:100%;min-height:32px;padding:7px 9px;border-radius:7px;background:transparent;color:var(--text-soft);text-align:left;font-size:11px;cursor:pointer}.menu-popover button:hover:not(:disabled){background:var(--surface-3);color:var(--text)}.menu-popover .danger{color:#f1a5aa}.menu-separator{height:1px;margin:5px 3px;background:var(--border)}
  .archived-section{margin-top:14px}.archived-section>summary{width:max-content;display:flex;align-items:center;gap:7px;color:var(--muted);font-size:11px;cursor:pointer}.archived-section>summary span{padding:2px 6px;border-radius:999px;background:var(--surface-2);font-size:9px}.archived-list{margin-top:8px}
  .search-empty{min-height:160px;display:grid;place-content:center;justify-items:center;gap:4px;color:var(--muted);font-size:11px}.search-empty strong{color:var(--text);font-size:13px}.search-empty button{margin-top:4px;background:transparent;color:var(--accent);cursor:pointer}
  .modal-backdrop{position:fixed;z-index:100;inset:0;display:grid;place-items:center;padding:24px;background:rgba(4,6,8,.72);backdrop-filter:blur(5px)}
  .modal{width:min(480px,100%);display:grid;gap:16px;padding:20px;border:1px solid var(--border);border-radius:var(--radius-lg);background:var(--surface);box-shadow:var(--shadow-popover)}.settings-modal{width:min(600px,100%)}
  .modal-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.modal-head h2{margin:0;font-size:19px;letter-spacing:-.02em}.modal-head p{margin:4px 0 0;color:var(--muted);font-size:11px}
  .icon-button{width:32px;height:32px;flex:0 0 32px;border-radius:var(--radius-sm);background:transparent;color:var(--muted);font-size:20px;cursor:pointer}.icon-button:hover:not(:disabled){background:var(--surface-2);color:var(--text)}
  .modal label{display:grid;gap:6px;color:var(--text-soft);font-size:11px;font-weight:600}.field-hint{color:var(--muted-2);font-weight:400}.modal input,.modal select{width:100%;min-height:var(--control-height);padding:8px 10px}
  .file-picker{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px;align-items:center;padding:6px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--bg)}.file-picker span{padding-left:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--muted);font-size:10px}
  .settings-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px}.toggle-list{display:grid;border:1px solid var(--border);border-radius:var(--radius-sm);overflow:hidden}.toggle-list label{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:10px 11px;border-bottom:1px solid var(--border-soft)}.toggle-list label:last-child{border-bottom:0}.toggle-list label>span{display:grid;gap:2px}.toggle-list small{color:var(--muted);font-size:10px;font-weight:400}.toggle-list input{width:auto}
  .modal-actions{justify-content:flex-end;padding-top:2px}.danger-callout{padding:10px 11px;border:1px solid #6c363d;border-radius:var(--radius-sm);background:var(--danger-bg);color:#f1c5c9;font-size:11px;line-height:1.45}
  @media(max-width:760px){.page-head{align-items:flex-start;flex-direction:column}.head-actions{width:100%}.head-actions button{flex:1}.world-row{grid-template-columns:auto minmax(0,1fr)}.world-row>.menu,.archived-actions{grid-column:2;justify-self:start}.settings-grid,.task-strip{grid-template-columns:1fr}.modal-backdrop{padding:14px}}
</style>
