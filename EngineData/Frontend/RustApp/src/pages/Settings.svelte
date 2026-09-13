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
    currentPreset: 'Custom',
    performance: { name: 'Performance', maxMemoryMb: 1024, minMemoryMb: 1024 },
    boost: { name: 'Boost', maxMemoryMb: 1024, minMemoryMb: 1024 },
    warning: ''
  };

  let profile: ServerResourceProfile = emptyProfile;
  let snapshot: ServerSnapshot = {
    state: 'Offline', health: 'Offline', cpuLoadPercent: 0,
    usedMemoryBytes: 0, maxMemoryBytes: 0, pid: null, logPath: ''
  };
  let ramMb = 1024;
  let preset = 'Custom';
  let busy = false;
  let error = '';
  let message = '';

  const gb = (mb: number) => mb / 1024;
  const ramStep = () => profile.safeMaxMemoryMb >= 8192 ? 512 : 256;
  const selectedLabel = () => preset === 'Custom' ? 'Custom memory' : preset;

  function syncFromProfile(next: ServerResourceProfile) {
    profile = next;
    ramMb = Math.min(next.currentMaxMemoryMb, next.safeMaxMemoryMb);
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
      error = friendlyError(e);
    }
  }

  function selectPreset(name: 'Performance' | 'Boost') {
    const selected = name === 'Performance' ? profile.performance : profile.boost;
    ramMb = selected.maxMemoryMb;
    preset = name;
    message = '';
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
      const next = await runtimeProduct.server.saveResources({ maxMemoryMb: ramMb, preset });
      syncFromProfile(next);
      if (restart && snapshot.state === 'Online') {
        await runtimeProduct.server.restart();
        snapshot = await runtimeProduct.server.snapshot();
        message = 'Settings saved and server restarted.';
      } else {
        message = snapshot.state === 'Online'
          ? 'Settings saved. Restart the server when you are ready to apply them.'
          : 'Settings saved.';
      }
    } catch (e) {
      error = friendlyError(e);
    } finally {
      busy = false;
    }
  }

  function friendlyError(value: unknown) {
    return String(value).replace(/^Error:\s*/i, '').trim() || 'Something went wrong. Try again.';
  }

  onMount(() => { void load(); });
</script>

<section class="settings-page">
  <header class="page-head">
    <div>
      <h2>Server settings</h2>
      <p>Choose how much memory LazyBuilder should give this server.</p>
    </div>
  </header>

  {#if error}
    <div class="notice error-notice" role="alert"><strong>Couldn’t save settings</strong><span>{error}</span></div>
  {/if}
  {#if message}
    <div class="notice success-notice"><span>{message}</span></div>
  {/if}

  <section class="settings-section">
    <div class="section-copy">
      <h3>Performance profile</h3>
      <p>Use a preset unless this server has a specific memory requirement.</p>
    </div>

    <div class="preset-list">
      <button class="preset-row" class:selected={preset === 'Performance'} disabled={busy} onclick={() => selectPreset('Performance')}>
        <span class="radio-dot"></span>
        <span class="preset-copy">
          <strong>Performance</strong>
          <small>Recommended for everyday building and normal Paper workloads.</small>
        </span>
        <span class="preset-value">{gb(profile.performance.maxMemoryMb).toFixed(1)} GB</span>
      </button>

      <button class="preset-row" class:selected={preset === 'Boost'} disabled={busy} onclick={() => selectPreset('Boost')}>
        <span class="radio-dot"></span>
        <span class="preset-copy">
          <strong>Boost</strong>
          <small>Extra headroom for large builds, world generation and imports.</small>
        </span>
        <span class="preset-value">{gb(profile.boost.maxMemoryMb).toFixed(1)} GB</span>
      </button>
    </div>
  </section>

  <section class="settings-section">
    <div class="section-copy">
      <h3>Custom memory</h3>
      <p>Adjust this only when your build workload needs something different.</p>
    </div>

    <div class="memory-panel">
      <div class="memory-head">
        <div>
          <span class="label">Maximum memory</span>
          <strong>{gb(ramMb).toFixed(1)} GB</strong>
        </div>
        <span class="safe-limit">Recommended limit: {gb(profile.safeMaxMemoryMb).toFixed(1)} GB</span>
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
      <div class="range-labels"><span>1 GB</span><span>{gb(profile.safeMaxMemoryMb).toFixed(1)} GB</span></div>
      <p class="memory-help">LazyBuilder keeps enough memory reserved for Windows automatically.</p>
    </div>
  </section>

  {#if profile.warning}
    <div class="warning-card"><strong>Memory warning</strong><p>{profile.warning}</p></div>
  {/if}

  <div class="apply-bar">
    <div class="apply-summary">
      <strong>{selectedLabel()}</strong>
      <span>{gb(ramMb).toFixed(1)} GB maximum memory</span>
    </div>
    <div class="apply-actions">
      <button class="secondary" disabled={busy} onclick={() => save(false)}>{busy ? 'Saving…' : 'Save changes'}</button>
      {#if snapshot.state === 'Online'}
        <button class="primary" disabled={busy} onclick={() => save(true)}>Save & restart</button>
      {/if}
    </div>
  </div>
</section>

<style>
  .settings-page { width: min(920px, 100%); }
  .page-head { margin-bottom: 20px; }
  .page-head h2 { margin: 0; font-size: 18px; }
  .page-head p { margin: 5px 0 0; color: var(--muted); font-size: 12px; }
  .notice { display: grid; gap: 3px; padding: 11px 13px; border-radius: var(--radius); margin-bottom: 14px; font-size: 12px; }
  .error-notice { border: 1px solid #713940; background: var(--danger-bg); color: #ffdadd; }
  .success-notice { border: 1px solid var(--accent-border); background: var(--accent-soft); color: #a8e5b8; }

  .settings-section { display: grid; grid-template-columns: 210px minmax(0, 1fr); gap: 34px; padding: 22px 0; border-top: 1px solid var(--border-soft); }
  .settings-section:first-of-type { border-top: 0; padding-top: 0; }
  .section-copy h3 { margin: 0; font-size: 13px; }
  .section-copy p { margin: 6px 0 0; color: var(--muted); font-size: 12px; line-height: 1.45; }

  .preset-list { display: grid; border: 1px solid var(--border-soft); border-radius: var(--radius); overflow: hidden; background: var(--surface); box-shadow: var(--shadow-card); }
  .preset-row { display: grid; grid-template-columns: auto minmax(0,1fr) auto; align-items: center; gap: 12px; min-height: 72px; padding: 12px 14px; border: 0; border-bottom: 1px solid var(--border-soft); background: transparent; color: var(--text); text-align: left; cursor: pointer; transition: background-color 120ms ease, transform 120ms ease; }
  .preset-row:last-child { border-bottom: 0; }
  .preset-row:hover:not(:disabled) { background: var(--surface-2); }
  .preset-row:active:not(:disabled) { transform: scale(.995); }
  .preset-row.selected { background: var(--accent-soft); }
  .radio-dot { width: 16px; height: 16px; border: 2px solid #66717b; border-radius: 50%; position: relative; }
  .preset-row.selected .radio-dot { border-color: var(--accent); }
  .preset-row.selected .radio-dot::after { content: ''; position: absolute; inset: 3px; border-radius: 50%; background: var(--accent); }
  .preset-copy { display: grid; gap: 4px; }
  .preset-copy small { color: var(--muted); font-size: 11px; }
  .preset-value { color: var(--text-soft); font-size: 12px; font-weight: 700; }

  .memory-panel { padding: 16px; border: 1px solid var(--border-soft); border-radius: var(--radius); background: var(--surface); box-shadow: var(--shadow-card); }
  .memory-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 18px; }
  .memory-head > div { display: grid; gap: 4px; }
  .memory-head strong { font-size: 22px; }
  .label, .safe-limit { color: var(--muted); font-size: 11px; }
  input[type='range'] { width: 100%; min-height: auto; margin: 20px 0 5px; padding: 0; border: 0; background: transparent; box-shadow: none; accent-color: var(--accent); }
  input[type='range']:hover, input[type='range']:focus { background: transparent; box-shadow: none; }
  .range-labels { display: flex; justify-content: space-between; color: var(--muted); font-size: 10px; }
  .memory-help { margin: 12px 0 0; color: var(--muted-2); font-size: 10px; }

  .warning-card { margin-top: 16px; padding: 12px 14px; border: 1px solid #6b5730; border-radius: var(--radius); background: var(--warning-bg); color: #ebd9aa; font-size: 12px; }
  .warning-card p { margin: 5px 0 0; }

  .apply-bar { position: sticky; bottom: 10px; display: flex; justify-content: space-between; align-items: center; gap: 18px; margin-top: 24px; padding: 11px 12px; border: 1px solid var(--border); border-radius: var(--radius); background: rgba(24,27,30,.94); backdrop-filter: blur(14px); box-shadow: var(--shadow-popover); }
  .apply-summary { display: grid; gap: 3px; }
  .apply-summary span { color: var(--muted); font-size: 11px; }
  .apply-actions { display: flex; gap: 8px; }
  .primary, .secondary { min-height: var(--control-height); border-radius: var(--radius-sm); padding: 8px 13px; font-weight: 650; cursor: pointer; transition: background-color 120ms ease, border-color 120ms ease, transform 120ms ease; }
  .primary { border: 1px solid var(--accent); background: var(--accent); color: var(--accent-ink); }
  .secondary { border: 1px solid var(--border); background: var(--surface-2); color: var(--text); }
  .primary:hover:not(:disabled) { background: var(--accent-hover); border-color: var(--accent-hover); }
  .secondary:hover:not(:disabled) { background: var(--surface-3); border-color: var(--border-strong); }
  .primary:active:not(:disabled), .secondary:active:not(:disabled) { transform: scale(.98); }
  button:disabled { cursor: default; opacity: .5; }

  @media (max-width: 760px) {
    .settings-section { grid-template-columns: 1fr; gap: 12px; }
    .apply-bar { flex-direction: column; align-items: stretch; }
    .apply-actions { justify-content: flex-end; }
  }
</style>
