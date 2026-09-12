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
  let connectionStatus = 'Server offline';
  let busy = false;
  let serverOnline = false;

  let createName = '';
  let createType: 'FLAT' | 'VOID' = 'FLAT';

  let settings: WorldSettingsSnapshot | null = null;
  let settingsBusy = false;

  let operationBusyWorldId: string | null = null;
  let operationTask: WorldTaskSnapshot | null = null;
  let cloneSource: ManagedWorldSummary | null = null;
  let cloneName = '';
  let exportSource: ManagedWorldSummary | null = null;
  let exportName = '';

  let importPath = '';
  let importName = '';
  let importBusy = false;

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
        cloneSource = null;
        exportSource = null;
        error = '';
        connectionStatus = 'Start the server to manage worlds';
        return;
      }

      worlds = await runtimeProduct.worlds.list();
      worlds = [...worlds].sort((left, right) =>
        left.lifecycle.localeCompare(right.lifecycle) || left.displayName.localeCompare(right.displayName)
      );
      connectionStatus = `World-Manager ready · ${worlds.length} world${worlds.length === 1 ? '' : 's'}`;
      error = '';
    } catch (e) {
      worlds = [];
      settings = null;
      connectionStatus = 'World-Manager unavailable';
      error = String(e);
    } finally {
      busy = false;
    }
  }

  async function createWorld() {
    const displayName = createName.trim();
    if (!displayName) return;
    busy = true;
    try {
      const folderName = slugify(displayName) || 'world';
      await runtimeProduct.worlds.create({ folderName, displayName, kind: createType });
      createName = '';
      error = '';
    } catch (e) {
      error = String(e);
    } finally {
      busy = false;
      await refresh();
    }
  }

  async function pickImport() {
    if (!serverOnline || importBusy) return;
    const selected = await runtimeProduct.worlds.pickImport();
    if (!selected) return;
    importPath = selected;
    const fileName = selected.split(/[\\/]/).pop() || 'Imported World';
    importName = fileName.replace(/\.(zip|mcworld)$/i, '').replace(/[-_]+/g, ' ').trim() || 'Imported World';
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
      operationTask = await runtimeProduct.worlds.import({
        artifactName,
        destinationFolder,
        displayName
      });
      await pollTask(operationTask.taskId);
      importPath = '';
      importName = '';
    } catch (e) {
      error = String(e);
    } finally {
      importBusy = false;
      await refresh();
    }
  }

  async function toggleRuntime(world: ManagedWorldSummary) {
    busy = true;
    try {
      if (world.runtimeState === 'LOADED') await runtimeProduct.worlds.unload(world.id);
      else await runtimeProduct.worlds.load(world.id);
      error = '';
    } catch (e) {
      error = String(e);
    } finally {
      busy = false;
      await refresh();
    }
  }

  async function runLifecycleTask(world: ManagedWorldSummary, operation: 'archive' | 'restore') {
    if (operationBusyWorldId || importBusy) return;
    if (operation === 'archive' && !window.confirm(`Archive ${world.displayName}? The world will be unloaded and auto-load disabled.`)) {
      return;
    }

    operationBusyWorldId = world.id;
    operationTask = null;
    error = '';
    try {
      operationTask = operation === 'archive'
        ? await runtimeProduct.worlds.archive(world.id)
        : await runtimeProduct.worlds.restore(world.id);
      await pollTask(operationTask.taskId);
    } catch (e) {
      error = String(e);
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
      error = String(e);
    } finally {
      operationBusyWorldId = null;
      await refresh();
    }
  }

  function openClone(world: ManagedWorldSummary) {
    if (importBusy) return;
    exportSource = null;
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
      cloneSource = null;
      cloneName = '';
      await pollTask(operationTask.taskId);
    } catch (e) {
      error = String(e);
    } finally {
      operationBusyWorldId = null;
      await refresh();
    }
  }

  function openExport(world: ManagedWorldSummary) {
    if (importBusy) return;
    cloneSource = null;
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
      exportSource = null;
      exportName = '';
      await pollTask(operationTask.taskId);
    } catch (e) {
      error = String(e);
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
      if (task.state === 'FAILED') {
        throw new Error(task.error || task.message || 'World task failed.');
      }
      await new Promise((resolve) => window.setTimeout(resolve, 750));
    }
  }

  async function openSettings(world: ManagedWorldSummary) {
    settingsBusy = true;
    try {
      settings = await runtimeProduct.worlds.settings(world.id);
      error = '';
    } catch (e) {
      error = String(e);
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
      settings = await runtimeProduct.worlds.updateSettings(settings.id, request);
      error = '';
      await refresh();
    } catch (e) {
      error = String(e);
    } finally {
      settingsBusy = false;
    }
  }

  function slugify(value: string) {
    return value
      .toLowerCase()
      .replace(/[^a-z0-9_-]+/g, '-')
      .replace(/^-+|-+$/g, '');
  }

  onMount(() => {
    void refresh();
    const timer = window.setInterval(() => void refresh(), 3000);
    return () => window.clearInterval(timer);
  });
</script>

<h1>Worlds</h1>
<p class="subtle">World lifecycle stays owned by World-Manager. Desktop only sends structured requests.</p>
<p class="subtle">{connectionStatus}</p>

<div class="card" style="margin-top: 20px">
  <strong>Create World</strong>
  <div class="world-form">
    <input disabled={!serverOnline || busy || importBusy} bind:value={createName} placeholder="World name" />
    <select disabled={!serverOnline || busy || importBusy} bind:value={createType}>
      <option value="FLAT">Flat</option>
      <option value="VOID">Void</option>
    </select>
    <button disabled={!serverOnline || busy || importBusy || !createName.trim()} onclick={createWorld}>Create</button>
  </div>
</div>

<div class="card" style="margin-top: 12px">
  <strong>Import World</strong>
  <div class="world-form">
    <button disabled={!serverOnline || importBusy || operationBusyWorldId !== null} onclick={pickImport}>Choose .zip / .mcworld</button>
    <input disabled={!serverOnline || importBusy || !importPath} bind:value={importName} placeholder="Imported world name" />
    <button disabled={!serverOnline || importBusy || operationBusyWorldId !== null || !importPath || !importName.trim()} onclick={runImport}>
      {importBusy ? 'Importing…' : 'Import'}
    </button>
  </div>
  {#if importPath}<div class="subtle">Selected: {importPath.split(/[\\/]/).pop()}</div>{/if}
  <div class="subtle">Desktop streams the selected file through the authenticated World-Manager import inbox before the async import task starts.</div>
</div>

<div class="actions"><button disabled={busy || importBusy || operationBusyWorldId !== null} onclick={refresh}>Refresh</button></div>
{#if error}<p style="color: var(--danger)">{error}</p>{/if}

{#if operationTask}
  <div class="card" style="margin-top: 12px">
    <strong>{operationTask.taskType} · {operationTask.state}</strong>
    <div class="subtle">{operationTask.progressPercent}% · {operationTask.message}</div>
    {#if operationTask.result}<div class="subtle">Result: {operationTask.result}</div>{/if}
    {#if operationTask.error}<div style="color: var(--danger)">{operationTask.error}</div>{/if}
  </div>
{/if}

{#if cloneSource}
  <div class="card" style="margin-top: 12px">
    <strong>Clone {cloneSource.displayName}</strong>
    <div class="world-form">
      <input bind:value={cloneName} disabled={operationBusyWorldId !== null || importBusy} placeholder="New world name" />
      <button disabled={operationBusyWorldId !== null || importBusy || !cloneName.trim()} onclick={runClone}>Clone</button>
      <button disabled={operationBusyWorldId !== null || importBusy} onclick={() => (cloneSource = null)}>Cancel</button>
    </div>
    <div class="subtle">The source is temporarily unloaded while a consistent copy is created, then restored to its previous load state.</div>
  </div>
{/if}

{#if exportSource}
  <div class="card" style="margin-top: 12px">
    <strong>Export {exportSource.displayName}</strong>
    <div class="world-form">
      <input bind:value={exportName} disabled={operationBusyWorldId !== null || importBusy} placeholder="Artifact name" />
      <button disabled={operationBusyWorldId !== null || importBusy || !exportName.trim()} onclick={runExport}>Export Java ZIP</button>
      <button disabled={operationBusyWorldId !== null || importBusy} onclick={() => (exportSource = null)}>Cancel</button>
    </div>
    <div class="subtle">Native Java 1.21.4 export. The source is restored after the snapshot is captured while packaging continues off the Paper main thread.</div>
  </div>
{/if}

{#each worlds as world}
  <div class="card" style="margin-top: 12px">
    <div class="world-row">
      <div>
        <strong>{world.displayName}</strong>
        <div class="subtle">{world.kind} · {world.runtimeState} · {world.lifecycle}</div>
        <div class="subtle">Default mode: {world.defaultGameMode} · Auto load: {world.autoLoad ? 'On' : 'Off'}</div>
      </div>
      <div class="world-actions">
        <button
          disabled={busy || importBusy || operationBusyWorldId !== null || world.lifecycle !== 'ACTIVE' || !['LOADED', 'UNLOADED'].includes(world.runtimeState)}
          onclick={() => toggleRuntime(world)}
        >{world.runtimeState === 'LOADED' ? 'Unload' : 'Load'}</button>
        <button
          disabled={settingsBusy || importBusy || operationBusyWorldId !== null || world.lifecycle !== 'ACTIVE'}
          onclick={() => openSettings(world)}
        >Settings</button>
        {#if world.lifecycle === 'ACTIVE'}
          <button disabled={busy || importBusy || operationBusyWorldId !== null} onclick={() => runBackup(world)}>Backup</button>
          <button disabled={busy || importBusy || operationBusyWorldId !== null} onclick={() => openClone(world)}>Clone</button>
          <button disabled={busy || importBusy || operationBusyWorldId !== null} onclick={() => openExport(world)}>Export</button>
          <button
            disabled={busy || importBusy || operationBusyWorldId !== null}
            onclick={() => runLifecycleTask(world, 'archive')}
          >{operationBusyWorldId === world.id ? 'Working…' : 'Archive'}</button>
        {:else if world.lifecycle === 'ARCHIVED'}
          <button
            disabled={busy || importBusy || operationBusyWorldId !== null}
            onclick={() => runLifecycleTask(world, 'restore')}
          >{operationBusyWorldId === world.id ? 'Working…' : 'Restore'}</button>
        {/if}
      </div>
    </div>
  </div>
{/each}

{#if worlds.length === 0 && !error && connectionStatus.startsWith('World-Manager ready')}
  <p class="subtle" style="margin-top:20px">No managed worlds detected.</p>
{/if}

{#if settings}
  <div class="card settings-panel" style="margin-top: 20px">
    <div class="world-row">
      <div>
        <strong>{settings.displayName} Settings</strong>
        <div class="subtle">Basic builder settings only. Advanced gamerules stay outside the normal surface.</div>
      </div>
      <button disabled={settingsBusy} onclick={() => (settings = null)}>Close</button>
    </div>

    <div class="settings-grid">
      <label>Default Game Mode
        <select bind:value={settings.defaultGameMode} disabled={settingsBusy}>
          <option value="CREATIVE">Creative</option>
          <option value="SURVIVAL">Survival</option>
          <option value="ADVENTURE">Adventure</option>
          <option value="SPECTATOR">Spectator</option>
        </select>
      </label>
      <label>Weather
        <select bind:value={settings.weather} disabled={settingsBusy}>
          <option value="CLEAR">Clear</option>
          <option value="RAIN">Rain</option>
          <option value="THUNDER">Thunder</option>
        </select>
      </label>
      <label>Time
        <input type="number" min="0" max="23999" bind:value={settings.timeOfDayTicks} disabled={settingsBusy} />
      </label>
      <label class="check"><input type="checkbox" bind:checked={settings.autoLoad} disabled={settingsBusy} /> Auto Load</label>
      <label class="check"><input type="checkbox" bind:checked={settings.naturalSpawning} disabled={settingsBusy} /> Mob Spawning</label>
      <label class="check"><input type="checkbox" bind:checked={settings.daylightCycle} disabled={settingsBusy} /> Daylight Cycle</label>
      <label class="check"><input type="checkbox" bind:checked={settings.weatherCycle} disabled={settingsBusy} /> Weather Cycle</label>
    </div>

    <div class="actions"><button disabled={settingsBusy} onclick={saveSettings}>Save Settings</button></div>
  </div>
{/if}
