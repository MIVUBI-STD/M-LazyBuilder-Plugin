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
  const selectedLabel = () => preset === 'Custom' ? 'Custom' : preset;
  const startupRamFor = (maxMb: number) => {
    if (maxMb <= 4096) return 1024;
    if (maxMb <= 8192) return 2048;
    if (maxMb <= 12288) return 3072;
    return Math.min(4096, maxMb);
  };

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
          ? 'Settings saved. Restart the server to apply them.'
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

<section class="page-head">
  <div>
    <h1>Server settings</h1>
    <p>Configure how much memory this server can use.</p>
  </div>
</section>

{#if error}
  <div class="notice error-notice"><strong>Couldn’t save settings</strong><span>{error}</span></div>
{/if}
{#if message}
  <div class="notice success-notice"><span>{message}</span></div>
{/if}

<section class="settings-section">
  <div class="section-copy">
    <h2>Performance</h2>
    <p>Choose a memory profile based on the size of your builds and workloads.</p>
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
        <small>More memory headroom for large builds, generation and imports.</small>
      </span>
      <span class="preset-value">{gb(profile.boost.maxMemoryMb).toFixed(1)} GB</span>
    </button>
  </div>
</section>

<section class="settings-section">
  <div class="section-copy">
    <h2>Memory</h2>
    <p>Set a custom maximum only when you need more control.</p>
  </div>

  <div class="memory-panel">
    <div class="memory-head">
      <div>
        <span class="label">Maximum memory</span>
        <strong>{gb(ramMb).toFixed(1)} GB</strong>
      </div>
      <span class="safe-limit">Up to {gb(profile.safeMaxMemoryMb).toFixed(1)} GB recommended</span>
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
  </div>
</section>

<section class="settings-section compact-section">
  <div class="section-copy">
    <h2>System</h2>
    <p>Detected hardware available to this server.</p>
  </div>
  <div class="system-grid">
    <div><span>System memory</span><strong>{gb(profile.totalMemoryMb).toFixed(1)} GB</strong></div>
    <div><span>Reserved for Windows</span><strong>{gb(profile.reservedSystemMemoryMb).toFixed(1)} GB</strong></div>
    <div><span>CPU</span><strong>{profile.logicalProcessors} logical cores</strong></div>
    <div><span>CPU scheduling</span><strong>Automatic</strong></div>
  </div>
</section>

{#if profile.warning}
  <div class="warning-card"><strong>Resource warning</strong><p>{profile.warning}</p></div>
{/if}

<div class="apply-bar">
  <div class="apply-summary">
    <strong>{selectedLabel()}</strong>
    <span>{gb(startupRamFor(ramMb)).toFixed(1)} GB startup · {gb(ramMb).toFixed(1)} GB maximum</span>
  </div>
  <div class="apply-actions">
    <button class="secondary" disabled={busy} onclick={() => save(false)}>{busy ? 'Saving…' : 'Save changes'}</button>
    {#if snapshot.state === 'Online'}
      <button class="primary" disabled={busy} onclick={() => save(true)}>Save & restart</button>
    {/if}
  </div>
</div>

<style>
  .page-head { margin-bottom:24px; }
  .page-head h1 { margin:0; font-size:26px; letter-spacing:-.02em; }
  .page-head p { margin:6px 0 0; color:var(--muted); font-size:13px; }
  .notice { display:grid; gap:3px; padding:12px 14px; border-radius:10px; margin-bottom:14px; font-size:13px; }
  .error-notice { border:1px solid #713940; background:#321b1f; color:#ffdadd; }
  .success-notice { border:1px solid #315d40; background:#172b1d; color:#a8e5b8; }
  .settings-section { display:grid; grid-template-columns:210px minmax(0,1fr); gap:34px; padding:22px 0; border-top:1px solid var(--border); }
  .settings-section:first-of-type { border-top:0; padding-top:0; }
  .section-copy h2 { margin:0; font-size:15px; }
  .section-copy p { margin:6px 0 0; color:var(--muted); font-size:12px; line-height:1.45; }
  .preset-list { display:grid; border:1px solid var(--border); border-radius:11px; overflow:hidden; background:var(--surface); }
  .preset-row { display:grid; grid-template-columns:auto minmax(0,1fr) auto; align-items:center; gap:12px; min-height:72px; padding:12px 14px; border:0; border-bottom:1px solid var(--border); background:transparent; color:var(--text); text-align:left; cursor:pointer; }
  .preset-row:last-child { border-bottom:0; }
  .preset-row:hover:not(:disabled) { background:rgba(255,255,255,.02); }
  .preset-row.selected { background:rgba(96,205,123,.055); }
  .radio-dot { width:16px; height:16px; border:2px solid #66717b; border-radius:50%; position:relative; }
  .preset-row.selected .radio-dot { border-color:var(--accent); }
  .preset-row.selected .radio-dot::after { content:''; position:absolute; inset:3px; border-radius:50%; background:var(--accent); }
  .preset-copy { display:grid; gap:4px; }
  .preset-copy small { color:var(--muted); font-size:11px; }
  .preset-value { color:#cbd2d8; font-size:12px; font-weight:700; }
  .memory-panel { padding:16px; border:1px solid var(--border); border-radius:11px; background:var(--surface); }
  .memory-head { display:flex; justify-content:space-between; align-items:flex-end; gap:18px; }
  .memory-head > div { display:grid; gap:4px; }
  .memory-head strong { font-size:22px; }
  .label, .safe-limit { color:var(--muted); font-size:11px; }
  input[type='range'] { width:100%; margin:20px 0 5px; accent-color:var(--accent); }
  .range-labels { display:flex; justify-content:space-between; color:var(--muted); font-size:10px; }
  .system-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); border:1px solid var(--border); border-radius:11px; overflow:hidden; background:var(--surface); }
  .system-grid div { display:grid; gap:4px; padding:13px 14px; border-right:1px solid var(--border); border-bottom:1px solid var(--border); }
  .system-grid div:nth-child(2n) { border-right:0; }
  .system-grid div:nth-last-child(-n+2) { border-bottom:0; }
  .system-grid span { color:var(--muted); font-size:11px; }
  .system-grid strong { font-size:13px; }
  .warning-card { margin-top:16px; padding:12px 14px; border:1px solid #6b5730; border-radius:10px; background:#2b2518; color:#ebd9aa; font-size:12px; }
  .warning-card p { margin:5px 0 0; }
  .apply-bar { position:sticky; bottom:10px; display:flex; justify-content:space-between; align-items:center; gap:18px; margin-top:24px; padding:11px 12px; border:1px solid var(--border); border-radius:11px; background:rgba(25,29,33,.94); backdrop-filter:blur(14px); box-shadow:0 12px 34px rgba(0,0,0,.28); }
  .apply-summary { display:grid; gap:3px; }
  .apply-summary span { color:var(--muted); font-size:11px; }
  .apply-actions { display:flex; gap:8px; }
  .primary, .secondary { border-radius:9px; padding:9px 13px; font-weight:650; cursor:pointer; }
  .primary { border:1px solid var(--accent); background:var(--accent); color:#07120b; }
  .secondary { border:1px solid var(--border); background:var(--surface-2); color:var(--text); }
  button:disabled { cursor:default; opacity:.48; }
  @media (max-width:760px) {
    .settings-section { grid-template-columns:1fr; gap:12px; }
    .system-grid { grid-template-columns:1fr; }
    .system-grid div { border-right:0; border-bottom:1px solid var(--border) !important; }
    .system-grid div:last-child { border-bottom:0 !important; }
    .apply-bar { flex-direction:column; align-items:stretch; }
    .apply-actions { justify-content:flex-end; }
  }
</style>