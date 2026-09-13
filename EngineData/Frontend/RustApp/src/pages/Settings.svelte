<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { ServerResourceProfile, ServerSnapshot } from '../app/bridge/runtimeApi';

  const emptyProfile: ServerResourceProfile = {
    totalMemoryMb: 0,
    reservedSystemMemoryMb: 0,
    safeMaxMemoryMb: 1024,
    logicalProcessors: 1,
    currentMaxMemoryMb: 1024,
    currentMinMemoryMb: 1024,
    currentCpuThreads: 1,
    currentPreset: 'Custom',
    performance: { name: 'Performance', maxMemoryMb: 1024, minMemoryMb: 1024, cpuThreads: 1 },
    boost: { name: 'Boost', maxMemoryMb: 1024, minMemoryMb: 1024, cpuThreads: 1 },
    warning: ''
  };

  let profile: ServerResourceProfile = emptyProfile;
  let snapshot: ServerSnapshot = {
    state: 'Offline', health: 'Offline', cpuLoadPercent: 0,
    usedMemoryBytes: 0, maxMemoryBytes: 0, pid: null, logPath: ''
  };
  let ramMb = 1024;
  let cpuThreads = 1;
  let preset = 'Custom';
  let busy = false;
  let error = '';
  let message = '';

  const gb = (mb: number) => mb / 1024;
  const ramStep = () => profile.safeMaxMemoryMb >= 8192 ? 512 : 256;

  function syncFromProfile(next: ServerResourceProfile) {
    profile = next;
    ramMb = Math.min(next.currentMaxMemoryMb, next.safeMaxMemoryMb);
    cpuThreads = Math.min(next.currentCpuThreads, next.logicalProcessors);
    preset = next.currentPreset;
  }

  async function load() {
    try {
      const [resources, server] = await Promise.all([
        runtimeProduct.server.resources(),
        runtimeProduct.server.snapshot()
      ]);
      syncFromProfile(resources);
      snapshot = server;
      error = '';
    } catch (e) {
      error = String(e);
    }
  }

  function selectPreset(name: 'Performance' | 'Boost') {
    const selected = name === 'Performance' ? profile.performance : profile.boost;
    ramMb = selected.maxMemoryMb;
    cpuThreads = selected.cpuThreads;
    preset = name;
    message = `${name} selected. Save to apply the calculated settings.`;
  }

  function markCustom() {
    preset = 'Custom';
    message = '';
  }

  async function save(restart: boolean) {
    if (busy) return;
    busy = true;
    error = '';
    message = '';
    try {
      const next = await runtimeProduct.server.saveResources({
        maxMemoryMb: ramMb,
        cpuThreads,
        preset
      });
      syncFromProfile(next);
      if (restart && snapshot.state === 'Online') {
        await runtimeProduct.server.restart();
        snapshot = await runtimeProduct.server.snapshot();
        message = 'Resources saved and the server was restarted with the new limits.';
      } else {
        message = snapshot.state === 'Online'
          ? 'Resources saved. Restart the server to apply the new limits.'
          : 'Resources saved. They will apply on the next server start.';
      }
    } catch (e) {
      error = String(e);
    } finally {
      busy = false;
    }
  }

  onMount(() => { void load(); });
</script>

<h1>Settings</h1>
<p class="subtle">Server resources are calculated against this PC so the launcher keeps enough memory for Windows.</p>

<div class="card">
  <div class="label">Detected Hardware</div>
  <div class="cards" style="margin-top: 10px">
    <div class="card"><div class="label">System RAM</div><div class="value">{gb(profile.totalMemoryMb).toFixed(1)} GB</div></div>
    <div class="card"><div class="label">Reserved for Windows</div><div class="value">{gb(profile.reservedSystemMemoryMb).toFixed(1)} GB</div></div>
    <div class="card"><div class="label">Safe Server RAM</div><div class="value">{gb(profile.safeMaxMemoryMb).toFixed(1)} GB</div></div>
    <div class="card"><div class="label">Logical CPU</div><div class="value">{profile.logicalProcessors}</div></div>
  </div>
</div>

<div class="card" style="margin-top: 16px">
  <div class="label">Presets</div>
  <p class="subtle">Calculated automatically from the detected PC specification.</p>
  <div class="actions">
    <button class:active={preset === 'Performance'} disabled={busy} onclick={() => selectPreset('Performance')}>
      Performance · {gb(profile.performance.maxMemoryMb).toFixed(1)} GB · {profile.performance.cpuThreads} CPU
    </button>
    <button class:active={preset === 'Boost'} disabled={busy} onclick={() => selectPreset('Boost')}>
      Boost · {gb(profile.boost.maxMemoryMb).toFixed(1)} GB · {profile.boost.cpuThreads} CPU
    </button>
  </div>
</div>

<div class="card" style="margin-top: 16px">
  <div class="label">RAM Allocation</div>
  <div class="value" style="margin: 8px 0">{gb(ramMb).toFixed(1)} GB</div>
  <input
    aria-label="Server RAM allocation"
    type="range"
    min="1024"
    max={Math.max(1024, profile.safeMaxMemoryMb)}
    step={ramStep()}
    bind:value={ramMb}
    oninput={markCustom}
    style="width: 100%"
  />
  <p class="subtle">Maximum available to Paper. Windows reserve is protected automatically. JVM start RAM will be calculated safely from this value.</p>
</div>

<div class="card" style="margin-top: 16px">
  <div class="label">CPU Allocation</div>
  <div class="value" style="margin: 8px 0">{cpuThreads} / {profile.logicalProcessors} logical processors</div>
  <input
    aria-label="Server CPU allocation"
    type="range"
    min="1"
    max={Math.max(1, profile.logicalProcessors)}
    step="1"
    bind:value={cpuThreads}
    oninput={markCustom}
    style="width: 100%"
  />
  <p class="subtle">Controls the CPU count exposed to the JVM. This avoids aggressive percentage throttling that can destabilize Paper ticks.</p>
</div>

{#if profile.warning}
  <div class="card" style="margin-top: 16px">
    <div class="label">Resource Warning</div>
    <p>{profile.warning}</p>
  </div>
{/if}

{#if error}<p style="color: var(--danger)">{error}</p>{/if}
{#if message}<p>{message}</p>{/if}

<div class="actions" style="margin-top: 16px">
  <button disabled={busy} onclick={() => save(false)}>Save Resources</button>
  {#if snapshot.state === 'Online'}
    <button disabled={busy} onclick={() => save(true)}>Save & Restart Server</button>
  {/if}
</div>
