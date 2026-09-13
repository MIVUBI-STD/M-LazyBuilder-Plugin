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
  const selectedLabel = () => preset === 'Custom' ? 'Custom / Advanced' : preset;
  const startupRamFor = (maxMb: number) => {
    if (maxMb <= 4096) return 1024;
    if (maxMb <= 8192) return 2048;
    if (maxMb <= 12288) return 3072;
    return Math.min(4096, maxMb);
  };

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
    message = `${name} selected. Apply resources to save this profile.`;
  }

  function markCustom() {
    preset = 'Custom';
    message = 'Advanced values changed. Apply resources to save the custom profile.';
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
        message = `${selectedLabel()} resources applied and the server restarted.`;
      } else {
        message = snapshot.state === 'Online'
          ? `${selectedLabel()} resources saved. Restart the server to activate them.`
          : `${selectedLabel()} resources saved. They will activate on the next server start.`;
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
<p class="subtle">Choose a preset for efficient Paper performance. The server starts with a small heap and can grow only when the workload needs more memory.</p>

<section class="resource-overview">
  <div>
    <div class="label">Current Resource Mode</div>
    <div class="mode-value">{selectedLabel()}</div>
  </div>
  <div class="hardware-line">
    <span>{gb(profile.totalMemoryMb).toFixed(1)} GB system RAM</span>
    <span>•</span>
    <span>{profile.logicalProcessors} logical CPU</span>
    <span>•</span>
    <span>{gb(profile.safeMaxMemoryMb).toFixed(1)} GB absolute safe ceiling</span>
  </div>
</section>

<section class="preset-section">
  <div class="section-heading">
    <div>
      <div class="label">Recommended Presets</div>
      <p class="subtle">Preset memory uses an efficiency cap instead of scaling endlessly with PC RAM. Windows headroom remains protected.</p>
    </div>
  </div>

  <div class="preset-grid">
    <button
      class="preset-card"
      class:selected={preset === 'Performance'}
      disabled={busy}
      onclick={() => selectPreset('Performance')}
      aria-pressed={preset === 'Performance'}
    >
      <div class="preset-topline">
        <strong>Performance</strong>
        {#if preset === 'Performance'}<span class="selected-badge">Selected</span>{/if}
      </div>
      <p>Default mode. Strong everyday Paper performance with a small startup heap and enough room to grow under real load.</p>
      <div class="preset-stats">
        <span><b>{gb(profile.performance.minMemoryMb).toFixed(1)} GB</b> startup</span>
        <span><b>{gb(profile.performance.maxMemoryMb).toFixed(1)} GB</b> max RAM</span>
        <span><b>{profile.performance.cpuThreads}</b> CPU threads</span>
      </div>
    </button>

    <button
      class="preset-card"
      class:selected={preset === 'Boost'}
      disabled={busy}
      onclick={() => selectPreset('Boost')}
      aria-pressed={preset === 'Boost'}
    >
      <div class="preset-topline">
        <strong>Boost</strong>
        {#if preset === 'Boost'}<span class="selected-badge">Selected</span>{/if}
      </div>
      <p>Temporary extra headroom for heavy imports, world generation, large builds, plugins, or more players without using all available RAM.</p>
      <div class="preset-stats">
        <span><b>{gb(profile.boost.minMemoryMb).toFixed(1)} GB</b> startup</span>
        <span><b>{gb(profile.boost.maxMemoryMb).toFixed(1)} GB</b> max RAM</span>
        <span><b>{profile.boost.cpuThreads}</b> CPU threads</span>
      </div>
    </button>
  </div>
</section>

<details class="advanced-panel">
  <summary>
    <span>
      <strong>Advanced Resource Control</strong>
      <small>Manual maximum RAM and JVM CPU allocation</small>
    </span>
    <span class="advanced-state">{preset === 'Custom' ? 'Custom active' : 'Optional'}</span>
  </summary>

  <div class="advanced-content">
    <div class="advanced-note">
      Changing either slider switches the resource mode to <strong>Custom</strong>. The selected RAM value is the maximum heap, not memory consumed immediately at startup.
    </div>

    <div class="slider-block">
      <div class="slider-heading">
        <div>
          <div class="label">Maximum Server RAM</div>
          <div class="slider-value">{gb(ramMb).toFixed(1)} GB</div>
        </div>
        <span class="limit-note">Startup {gb(startupRamFor(ramMb)).toFixed(1)} GB · Safe max {gb(profile.safeMaxMemoryMb).toFixed(1)} GB</span>
      </div>
      <input
        aria-label="Maximum server RAM allocation"
        type="range"
        min="1024"
        max={Math.max(1024, profile.safeMaxMemoryMb)}
        step={ramStep()}
        bind:value={ramMb}
        oninput={markCustom}
      />
      <p class="subtle">Paper starts with a smaller heap and can grow toward this maximum when actual workload requires it.</p>
    </div>

    <div class="slider-block">
      <div class="slider-heading">
        <div>
          <div class="label">Server CPU</div>
          <div class="slider-value">{cpuThreads} / {profile.logicalProcessors} threads</div>
        </div>
        <span class="limit-note">JVM processor count</span>
      </div>
      <input
        aria-label="Server CPU allocation"
        type="range"
        min="1"
        max={Math.max(1, profile.logicalProcessors)}
        step="1"
        bind:value={cpuThreads}
        oninput={markCustom}
      />
      <p class="subtle">Controls the logical processor count exposed to Java without percentage-based CPU throttling.</p>
    </div>
  </div>
</details>

<section class="safety-card">
  <div>
    <div class="label">Resource Safety</div>
    <div class="safety-grid">
      <span>System RAM <b>{gb(profile.totalMemoryMb).toFixed(1)} GB</b></span>
      <span>Windows reserve <b>{gb(profile.reservedSystemMemoryMb).toFixed(1)} GB</b></span>
      <span>Absolute safe ceiling <b>{gb(profile.safeMaxMemoryMb).toFixed(1)} GB</b></span>
    </div>
  </div>
  <p class="subtle">Presets normally stay well below the absolute ceiling. The ceiling exists as a final safety boundary for Custom settings and manually edited configuration.</p>
</section>

{#if profile.warning}
  <div class="warning-card">
    <strong>Resource warning</strong>
    <p>{profile.warning}</p>
  </div>
{/if}

{#if error}<p style="color: var(--danger)">{error}</p>{/if}
{#if message}<p>{message}</p>{/if}

<div class="apply-bar">
  <div>
    <strong>{selectedLabel()}</strong>
    <span>{gb(startupRamFor(ramMb)).toFixed(1)} GB startup → {gb(ramMb).toFixed(1)} GB max · {cpuThreads} CPU threads</span>
  </div>
  <div class="actions">
    <button disabled={busy} onclick={() => save(false)}>Apply Resources</button>
    {#if snapshot.state === 'Online'}
      <button disabled={busy} onclick={() => save(true)}>Apply & Restart</button>
    {/if}
  </div>
</div>

<style>
  .resource-overview,
  .preset-section,
  .advanced-panel,
  .safety-card,
  .warning-card,
  .apply-bar { margin-top: 16px; }

  .resource-overview,
  .preset-section,
  .safety-card,
  .warning-card,
  .apply-bar,
  .advanced-panel {
    border: 1px solid var(--border, rgba(255,255,255,.1));
    border-radius: 12px;
    background: var(--panel, rgba(255,255,255,.03));
  }

  .resource-overview,
  .preset-section,
  .safety-card,
  .warning-card { padding: 16px; }

  .mode-value { margin-top: 5px; font-size: 1.35rem; font-weight: 700; }
  .hardware-line { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; font-size: .9rem; opacity: .75; }
  .preset-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 14px; }

  .preset-card {
    width: 100%; min-height: 164px; padding: 16px; text-align: left; border-radius: 12px;
    border: 1px solid var(--border, rgba(255,255,255,.12));
    background: var(--panel-strong, rgba(255,255,255,.035)); cursor: pointer;
  }
  .preset-card.selected { outline: 2px solid currentColor; outline-offset: -2px; }
  .preset-topline { display: flex; justify-content: space-between; gap: 12px; align-items: center; font-size: 1.05rem; }
  .selected-badge, .advanced-state { font-size: .75rem; font-weight: 600; opacity: .75; }
  .preset-card p { min-height: 48px; margin: 10px 0 14px; opacity: .72; line-height: 1.45; }
  .preset-stats { display: flex; flex-wrap: wrap; gap: 12px 16px; font-size: .88rem; }

  .advanced-panel > summary {
    list-style: none; display: flex; align-items: center; justify-content: space-between;
    gap: 16px; padding: 16px; cursor: pointer;
  }
  .advanced-panel > summary::-webkit-details-marker { display: none; }
  .advanced-panel summary small { display: block; margin-top: 4px; opacity: .62; }
  .advanced-content { padding: 0 16px 16px; border-top: 1px solid var(--border, rgba(255,255,255,.08)); }
  .advanced-note { margin: 14px 0; padding: 10px 12px; border-radius: 8px; background: rgba(255,255,255,.04); font-size: .88rem; line-height: 1.45; }
  .slider-block + .slider-block { margin-top: 20px; }
  .slider-heading { display: flex; justify-content: space-between; align-items: end; gap: 16px; }
  .slider-value { margin-top: 4px; font-size: 1.12rem; font-weight: 700; }
  .limit-note { font-size: .78rem; opacity: .6; text-align: right; }
  input[type='range'] { width: 100%; margin: 12px 0 4px; }
  .safety-grid { display: flex; flex-wrap: wrap; gap: 8px 20px; margin: 9px 0; font-size: .9rem; }
  .warning-card { border-color: var(--danger, currentColor); }
  .warning-card p { margin-bottom: 0; }

  .apply-bar {
    position: sticky; bottom: 12px; display: flex; justify-content: space-between;
    align-items: center; gap: 18px; padding: 12px 14px; backdrop-filter: blur(14px);
  }
  .apply-bar > div:first-child { display: flex; flex-direction: column; gap: 3px; }
  .apply-bar span { font-size: .82rem; opacity: .68; }

  @media (max-width: 760px) {
    .preset-grid { grid-template-columns: 1fr; }
    .apply-bar { align-items: stretch; flex-direction: column; }
  }
</style>
