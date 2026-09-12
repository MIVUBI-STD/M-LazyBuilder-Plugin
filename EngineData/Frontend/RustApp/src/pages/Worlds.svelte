<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type {
    ManagedWorldSummary,
    WorldSettingsSnapshot,
    UpdateWorldSettingsRequest
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

  async function refresh() {
    if (busy) return;
    busy = true;
    try {
      const server = await runtimeProduct.server.snapshot();
      serverOnline = server.state === 'Online';
      if (!serverOnline) {
        worlds = [];
        settings = null;
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
      const folderName = displayName
        .toLowerCase()
        .replace(/[^a-z0-9_-]+/g, '-')
        .replace(/^-+|-+$/g, '') || 'world';
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
    <input disabled={!serverOnline || busy} bind:value={createName} placeholder="World name" />
    <select disabled={!serverOnline || busy} bind:value={createType}>
      <option value="FLAT">Flat</option>
      <option value="VOID">Void</option>
    </select>
    <button disabled={!serverOnline || busy || !createName.trim()} onclick={createWorld}>Create</button>
  </div>
</div>

<div class="actions"><button disabled={busy} onclick={refresh}>Refresh</button></div>
{#if error}<p style="color: var(--danger)">{error}</p>{/if}

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
          disabled={busy || world.lifecycle !== 'ACTIVE' || !['LOADED', 'UNLOADED'].includes(world.runtimeState)}
          onclick={() => toggleRuntime(world)}
        >{world.runtimeState === 'LOADED' ? 'Unload' : 'Load'}</button>
        <button disabled={settingsBusy || world.lifecycle !== 'ACTIVE'} onclick={() => openSettings(world)}>Settings</button>
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
