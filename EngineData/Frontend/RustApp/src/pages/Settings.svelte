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
  let busy = false;
  let error = '';
  let message = '';

  const gb = (mb: number) => mb / 1024;
  const ramStep = () => 256;
  const selectedLabel = () => preset === 'Custom' ? 'Custom' : preset;

  function syncFromProfile(next: ServerResourceProfile) {
    profile = next;
    ramMb = Math.min(next.currentMaxMemoryMb, next.safeMaxMemoryMb);
    preset = next.currentPreset;
  }

  async function load() {
    try {
      syncFromProfile(await runtimeProduct.server.resources());
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

  async function save() {
    if (busy) return;
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
    <div>
      <h2>Settings</h2>
      <p>Choose how much headroom this server should use on your PC.</p>
    </div>
  </header>

  {#if error}<div class="notice error" role="alert"><strong>Couldn’t save settings</strong><span>{error}</span></div>{/if}
  {#if message}<div class="notice success" aria-live="polite">{message}</div>{/if}

  <section class="profile-section">
    <div class="preset-grid">
      <button class="preset-card" class:selected={preset === 'Performance'} disabled={busy} onclick={() => selectPreset('Performance')}>
        <div class="preset-head">
          <span class="radio-dot"></span>
          <div><strong>Performance</strong><small>Recommended</small></div>
          <span class="preset-value">{gb(profile.performance.maxMemoryMb).toFixed(1)} GB</span>
        </div>
        <p>Low memory use for everyday building while keeping room for Minecraft and Windows.</p>
      </button>

      <button class="preset-card" class:selected={preset === 'Boost'} disabled={busy} onclick={() => selectPreset('Boost')}>
        <div class="preset-head">
          <span class="radio-dot"></span>
          <div><strong>Boost</strong><small>Heavier tasks</small></div>
          <span class="preset-value">{gb(profile.boost.maxMemoryMb).toFixed(1)} GB</span>
        </div>
        <p>Extra headroom for large imports, world generation and heavier build operations.</p>
      </button>
    </div>

    <div class="automatic-note">
      <div class="automatic-icon">A</div>
      <div>
        <strong>CPU is managed automatically</strong>
        <p>LazyBuilder keeps Paper responsive while yielding CPU when your Minecraft client needs it.</p>
      </div>
    </div>
  </section>

  <details class="advanced" open={preset === 'Custom'}>
    <summary>
      <span><strong>Custom memory</strong><small>Only change this if this server needs a different limit.</small></span>
      <span>{preset === 'Custom' ? `${gb(ramMb).toFixed(1)} GB` : 'Advanced'}</span>
    </summary>
    <div class="advanced-body">
      <div class="memory-head">
        <div><span>Maximum memory</span><strong>{gb(ramMb).toFixed(1)} GB</strong></div>
        <small>Safe maximum for this PC: {gb(profile.safeMaxMemoryMb).toFixed(1)} GB</small>
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
      <p>LazyBuilder reserves memory for Windows, Minecraft and background applications automatically.</p>
    </div>
  </details>

  {#if profile.warning}
    <div class="warning-card"><strong>Memory warning</strong><p>{profile.warning}</p></div>
  {/if}

  <footer class="save-bar">
    <div class="save-copy"><strong>{selectedLabel()}</strong><span>{gb(ramMb).toFixed(1)} GB maximum</span></div>
    <button class="primary" disabled={busy} onclick={save}>{busy ? 'Saving…' : 'Save changes'}</button>
  </footer>
</section>

<style>
  .settings-page { width:min(880px,100%); }
  .page-head { margin-bottom:16px; }
  .page-head h2 { margin:0; font-size:18px; }
  .page-head p { margin:4px 0 0; color:var(--muted); font-size:12px; }
  .notice { display:grid; gap:3px; margin-bottom:12px; padding:11px 13px; border-radius:var(--radius); font-size:11px; }
  .notice.error { border:1px solid #713940; background:var(--danger-bg); color:#ffdadd; }
  .notice.success { border:1px solid var(--accent-border); background:var(--accent-soft); color:#a8e5b8; }

  .profile-section { margin-top:4px; }
  .preset-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; }
  .preset-card { min-height:126px; display:grid; align-content:start; gap:10px; padding:15px; border:1px solid var(--border-soft); border-radius:var(--radius); background:var(--surface); color:var(--text); text-align:left; cursor:pointer; box-shadow:var(--shadow-card); transition:background 120ms ease,border-color 120ms ease,transform 120ms ease; }
  .preset-card:hover:not(:disabled) { background:var(--surface-2); border-color:var(--border-strong); }
  .preset-card:active:not(:disabled) { transform:scale(.995); }
  .preset-card.selected { border-color:var(--accent-border); background:var(--accent-soft); }
  .preset-head { display:grid; grid-template-columns:auto minmax(0,1fr) auto; align-items:center; gap:10px; }
  .preset-head>div { display:grid; gap:1px; }
  .preset-head strong { font-size:13px; }
  .preset-head small { color:var(--muted); font-size:10px; }
  .preset-value { color:var(--text-soft); font-size:12px; font-weight:750; }
  .preset-card p { margin:0; color:var(--muted); font-size:11px; line-height:1.45; }
  .radio-dot { width:16px; height:16px; position:relative; border:2px solid #66717b; border-radius:50%; }
  .preset-card.selected .radio-dot { border-color:var(--accent); }
  .preset-card.selected .radio-dot::after { content:''; position:absolute; inset:3px; border-radius:50%; background:var(--accent); }

  .automatic-note { display:flex; align-items:flex-start; gap:10px; margin-top:10px; padding:12px 13px; border:1px solid var(--border-soft); border-radius:var(--radius); background:var(--bg-elevated); }
  .automatic-icon { width:28px; height:28px; flex:0 0 28px; display:grid; place-items:center; border-radius:8px; background:var(--surface-2); color:var(--accent); font-size:10px; font-weight:800; }
  .automatic-note strong { font-size:11px; }
  .automatic-note p { margin:3px 0 0; color:var(--muted); font-size:10px; line-height:1.45; }

  .advanced { margin-top:12px; border:1px solid var(--border-soft); border-radius:var(--radius); background:var(--surface); }
  .advanced summary { display:flex; align-items:center; justify-content:space-between; gap:18px; padding:13px 14px; list-style:none; cursor:pointer; }
  .advanced summary::-webkit-details-marker { display:none; }
  .advanced summary>span:first-child { display:grid; gap:2px; }
  .advanced summary strong { font-size:12px; }
  .advanced summary small,.advanced summary>span:last-child { color:var(--muted); font-size:10px; }
  .advanced-body { padding:14px; border-top:1px solid var(--border-soft); }
  .memory-head { display:flex; justify-content:space-between; align-items:flex-end; gap:18px; }
  .memory-head>div { display:grid; gap:3px; }
  .memory-head span,.memory-head small { color:var(--muted); font-size:10px; }
  .memory-head strong { font-size:20px; }
  input[type='range'] { width:100%; min-height:auto; margin:18px 0 5px; padding:0; border:0; background:transparent; box-shadow:none; accent-color:var(--accent); }
  input[type='range']:hover,input[type='range']:focus { background:transparent; box-shadow:none; }
  .range-labels { display:flex; justify-content:space-between; color:var(--muted-2); font-size:9px; }
  .advanced-body p { margin:10px 0 0; color:var(--muted-2); font-size:10px; }

  .warning-card { margin-top:12px; padding:11px 13px; border:1px solid #6b5730; border-radius:var(--radius); background:var(--warning-bg); color:#ebd9aa; font-size:11px; }
  .warning-card p { margin:4px 0 0; }

  .save-bar { position:sticky; bottom:10px; display:flex; align-items:center; justify-content:space-between; gap:16px; margin-top:18px; padding:10px 11px; border:1px solid var(--border); border-radius:var(--radius); background:rgba(24,27,30,.95); backdrop-filter:blur(14px); box-shadow:var(--shadow-popover); }
  .save-copy { display:grid; gap:1px; }
  .save-copy strong { font-size:11px; }
  .save-copy span { color:var(--muted); font-size:10px; }
  .primary { min-height:var(--control-height); border:1px solid var(--accent); border-radius:var(--radius-sm); padding:8px 12px; background:var(--accent); color:var(--accent-ink); font-weight:650; cursor:pointer; }
  .primary:hover:not(:disabled) { background:var(--accent-hover); }
  button:disabled { opacity:.5; cursor:default; }

  @media (max-width:760px) {
    .preset-grid { grid-template-columns:1fr; }
    .save-bar { align-items:stretch; flex-direction:column; }
    .memory-head { align-items:flex-start; flex-direction:column; }
  }
</style>
