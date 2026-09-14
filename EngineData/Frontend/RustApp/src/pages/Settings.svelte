<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { ServerResourceProfile } from '../app/bridge/runtimeApi';

  const emptyProfile: ServerResourceProfile = {
    totalMemoryMb: 0, reservedSystemMemoryMb: 0, safeMaxMemoryMb: 1024, logicalProcessors: 1,
    currentMaxMemoryMb: 1024, currentMinMemoryMb: 1024, currentPreset: 'Custom',
    performance: { name: 'Performance', maxMemoryMb: 1024, minMemoryMb: 1024 },
    boost: { name: 'Boost', maxMemoryMb: 1024, minMemoryMb: 1024 }, warning: ''
  };

  let profile: ServerResourceProfile = emptyProfile;
  let ramMb = 1024;
  let preset = 'Custom';
  let savedRamMb = 1024;
  let savedPreset = 'Custom';
  let loaded = false;
  let busy = false;
  let error = '';
  let message = '';

  const gb = (mb: number) => mb / 1024;
  const selectedLabel = () => preset === 'Custom' ? 'Custom' : preset;
  const dirty = () => loaded && (ramMb !== savedRamMb || preset !== savedPreset);

  function syncFromProfile(next: ServerResourceProfile) {
    profile = next;
    ramMb = Math.min(next.currentMaxMemoryMb, next.safeMaxMemoryMb);
    preset = next.currentPreset;
    savedRamMb = ramMb;
    savedPreset = preset;
    loaded = true;
  }

  async function load() {
    error = '';
    try {
      syncFromProfile(await runtimeProduct.server.resources());
    } catch (e) {
      loaded = false;
      error = friendlyError(e);
    }
  }

  function selectPreset(name: 'Performance' | 'Boost') {
    if (!loaded) return;
    const selected = name === 'Performance' ? profile.performance : profile.boost;
    ramMb = selected.maxMemoryMb;
    preset = name;
    message = '';
  }

  function markCustom() {
    if (!loaded) return;
    preset = 'Custom';
    message = '';
  }

  async function save() {
    if (busy || !dirty()) return;
    busy = true;
    error = '';
    message = '';
    try {
      const next = await runtimeProduct.server.saveResources({ maxMemoryMb: ramMb, preset });
      syncFromProfile(next);
      message = 'Saved. Memory changes apply the next time the server starts.';
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
    <div><h2>Settings</h2><p>Choose how much server headroom to use on this PC.</p></div>
  </header>

  {#if error}<div class="notice error" role="alert"><strong>Settings unavailable</strong><span>{error}</span></div>{/if}
  {#if message}<div class="notice success" aria-live="polite">{message}</div>{/if}

  <section class="settings-group">
    <div class="group-copy">
      <h3>Performance</h3>
      <p>Use a preset unless this server has a specific memory requirement.</p>
    </div>

    <div class="group-content">
      <div class="preset-list">
        <button class="preset-row" class:selected={preset === 'Performance'} disabled={busy || !loaded} onclick={() => selectPreset('Performance')}>
          <span class="radio-dot"></span>
          <span class="preset-copy"><strong>Performance</strong><small>Recommended for everyday building</small></span>
          <span class="preset-value">{gb(profile.performance.maxMemoryMb).toFixed(1)} GB</span>
        </button>

        <button class="preset-row" class:selected={preset === 'Boost'} disabled={busy || !loaded} onclick={() => selectPreset('Boost')}>
          <span class="radio-dot"></span>
          <span class="preset-copy"><strong>Boost</strong><small>For imports, world generation and heavier build operations</small></span>
          <span class="preset-value">{gb(profile.boost.maxMemoryMb).toFixed(1)} GB</span>
        </button>
      </div>
      <p class="managed-copy">CPU is managed automatically so Paper can use spare capacity without taking responsiveness away from Minecraft.</p>
    </div>
  </section>

  <section class="settings-group">
    <div class="group-copy"><h3>Advanced</h3><p>Manual memory control is optional.</p></div>
    <details class="advanced" open={preset === 'Custom'}>
      <summary>
        <span><strong>Custom memory</strong><small>Set a different server memory limit.</small></span>
        <span>{preset === 'Custom' ? `${gb(ramMb).toFixed(1)} GB` : 'Configure'}</span>
      </summary>
      <div class="advanced-body">
        <div class="memory-head"><div><span>Maximum memory</span><strong>{gb(ramMb).toFixed(1)} GB</strong></div><small>Safe maximum: {gb(profile.safeMaxMemoryMb).toFixed(1)} GB</small></div>
        <input aria-label="Maximum server RAM allocation" type="range" min="1024" max={Math.max(1024, profile.safeMaxMemoryMb)} step="256" bind:value={ramMb} oninput={markCustom} disabled={busy || !loaded} />
        <div class="range-labels"><span>1 GB</span><span>{gb(profile.safeMaxMemoryMb).toFixed(1)} GB</span></div>
        <p>LazyBuilder keeps memory available for Windows, Minecraft and other applications.</p>
      </div>
    </details>
  </section>

  {#if profile.warning}<div class="warning-card"><strong>Memory warning</strong><p>{profile.warning}</p></div>{/if}

  <footer class="settings-footer">
    <div><strong>{selectedLabel()}</strong><span>{gb(ramMb).toFixed(1)} GB maximum</span></div>
    <button class="primary" disabled={busy || !dirty()} onclick={save}>{busy ? 'Saving…' : dirty() ? 'Save changes' : 'Saved'}</button>
  </footer>
</section>

<style>
  .settings-page{width:min(920px,100%)}
  .page-head{margin-bottom:18px}.page-head h2{margin:0;font-size:18px}.page-head p{margin:4px 0 0;color:var(--muted);font-size:12px}
  .notice{display:grid;gap:3px;margin-bottom:12px;padding:10px 12px;border-radius:8px;font-size:11px}.notice.error{border:1px solid #713940;background:var(--danger-bg);color:#ffdadd}.notice.success{border:1px solid var(--accent-border);background:var(--accent-soft);color:#a8e5b8}
  .settings-group{display:grid;grid-template-columns:180px minmax(0,1fr);gap:28px;padding:20px 0;border-top:1px solid var(--border-soft)}.settings-group:first-of-type{padding-top:0;border-top:0}.group-copy h3{margin:0;font-size:12px}.group-copy p{margin:5px 0 0;color:var(--muted);font-size:11px;line-height:1.45}.group-content{min-width:0}
  .preset-list{overflow:hidden;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}.preset-row{width:100%;min-height:66px;display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:11px;padding:11px 13px;border:0;border-bottom:1px solid var(--border-soft);background:transparent;color:var(--text);text-align:left;cursor:pointer;transition:background 120ms ease}.preset-row:last-child{border-bottom:0}.preset-row:hover:not(:disabled){background:var(--surface-2)}.preset-row.selected{background:var(--accent-soft)}.radio-dot{width:15px;height:15px;position:relative;border:2px solid #66717b;border-radius:50%}.preset-row.selected .radio-dot{border-color:var(--accent)}.preset-row.selected .radio-dot::after{content:'';position:absolute;inset:3px;border-radius:50%;background:var(--accent)}.preset-copy{display:grid;gap:2px}.preset-copy strong{font-size:12px}.preset-copy small{color:var(--muted);font-size:10px}.preset-value{color:var(--text-soft);font-size:11px;font-weight:750}.managed-copy{margin:8px 2px 0;color:var(--muted-2);font-size:10px;line-height:1.45}
  .advanced{border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}.advanced summary{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:12px 13px;list-style:none;cursor:pointer}.advanced summary::-webkit-details-marker{display:none}.advanced summary>span:first-child{display:grid;gap:2px}.advanced summary strong{font-size:11px}.advanced summary small,.advanced summary>span:last-child{color:var(--muted);font-size:10px}.advanced-body{padding:13px;border-top:1px solid var(--border-soft)}.memory-head{display:flex;justify-content:space-between;align-items:flex-end;gap:18px}.memory-head>div{display:grid;gap:2px}.memory-head span,.memory-head small{color:var(--muted);font-size:10px}.memory-head strong{font-size:18px}input[type='range']{width:100%;min-height:auto;margin:16px 0 4px;padding:0;border:0;background:transparent;box-shadow:none;accent-color:var(--accent)}input[type='range']:hover,input[type='range']:focus{background:transparent;box-shadow:none}.range-labels{display:flex;justify-content:space-between;color:var(--muted-2);font-size:9px}.advanced-body p{margin:9px 0 0;color:var(--muted-2);font-size:10px}
  .warning-card{margin-top:12px;padding:10px 12px;border:1px solid #6b5730;border-radius:8px;background:var(--warning-bg);color:#ebd9aa;font-size:11px}.warning-card p{margin:4px 0 0}
  .settings-footer{display:flex;align-items:center;justify-content:flex-end;gap:14px;margin-top:20px;padding-top:16px;border-top:1px solid var(--border-soft)}.settings-footer>div{display:grid;gap:1px;text-align:right}.settings-footer strong{font-size:10px}.settings-footer span{color:var(--muted);font-size:9px}.primary{min-height:var(--control-height);border:1px solid var(--accent);border-radius:8px;padding:8px 13px;background:var(--accent);color:var(--accent-ink);font-weight:650;cursor:pointer}.primary:hover:not(:disabled){background:var(--accent-hover)}button:disabled{opacity:.5;cursor:default}
  @media(max-width:760px){.settings-group{grid-template-columns:1fr;gap:10px}.settings-footer{align-items:stretch;flex-direction:column}.settings-footer>div{text-align:left}.memory-head{align-items:flex-start;flex-direction:column}}
</style>
