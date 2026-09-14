<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { ServerResourceProfile } from '../app/bridge/runtimeApi';

  const emptyProfile: ServerResourceProfile = {
    totalMemoryMb: 0,
    safeMaxMemoryMb: 1024,
    currentMaxMemoryMb: 1024,
    currentMinMemoryMb: 1024,
    recommendedMaxMemoryMb: 1024,
    warning: ''
  };

  let profile: ServerResourceProfile = emptyProfile;
  let ramMb = 1024;
  let savedRamMb = 1024;
  let loaded = false;
  let busy = false;
  let error = '';
  let message = '';

  const gb = (mb: number) => mb / 1024;
  const dirty = () => loaded && ramMb !== savedRamMb;
  const usingRecommended = () => loaded && ramMb === profile.recommendedMaxMemoryMb;

  function syncFromProfile(next: ServerResourceProfile) {
    profile = next;
    ramMb = Math.min(next.currentMaxMemoryMb, next.safeMaxMemoryMb);
    savedRamMb = ramMb;
    loaded = true;
  }

  async function load() {
    error = '';
    try { syncFromProfile(await runtimeProduct.server.resources()); }
    catch (e) { loaded = false; error = friendlyError(e); }
  }

  function useRecommended() {
    if (!loaded) return;
    ramMb = profile.recommendedMaxMemoryMb;
    message = '';
  }

  async function save() {
    if (busy || !dirty()) return;
    busy = true; error = ''; message = '';
    try {
      const next = await runtimeProduct.server.saveResources({ maxMemoryMb: ramMb });
      syncFromProfile(next);
      message = 'Saved. Memory changes apply the next time the server starts.';
    } catch (e) { error = friendlyError(e); }
    finally { busy = false; }
  }

  function friendlyError(value: unknown) {
    return String(value).replace(/^Error:\s*/i, '').trim() || 'Something went wrong. Try again.';
  }

  onMount(() => void load());
</script>

<section class="settings-page">
  <header class="page-head"><div><h2>Settings</h2><p>Set the maximum memory available to this server.</p></div></header>

  {#if error}<div class="notice error" role="alert"><strong>Settings unavailable</strong><span>{error}</span></div>{/if}
  {#if message}<div class="notice success" aria-live="polite">{message}</div>{/if}

  <section class="settings-group">
    <div class="group-copy"><h3>Memory</h3><p>LazyBuilder provides one conservative recommendation. Change it only when this server needs a different limit.</p></div>
    <div class="group-content">
      <button class="recommendation" class:selected={usingRecommended()} disabled={busy || !loaded} onclick={useRecommended}>
        <div><strong>Recommended</strong><small>Simple default for running Paper alongside Minecraft and Windows.</small></div>
        <span>{gb(profile.recommendedMaxMemoryMb).toFixed(1)} GB</span>
      </button>
      <div class="memory-control">
        <div class="memory-head"><div><span>Maximum server memory</span><strong>{gb(ramMb).toFixed(1)} GB</strong></div><small>Safe maximum: {gb(profile.safeMaxMemoryMb).toFixed(1)} GB</small></div>
        <input aria-label="Maximum server RAM allocation" type="range" min="1024" max={Math.max(1024, profile.safeMaxMemoryMb)} step="256" bind:value={ramMb} disabled={busy || !loaded} />
        <div class="range-labels"><span>1 GB</span><span>{gb(profile.safeMaxMemoryMb).toFixed(1)} GB</span></div>
      </div>
    </div>
  </section>

  {#if profile.warning}<div class="warning-card"><strong>Memory warning</strong><p>{profile.warning}</p></div>{/if}

  <footer class="settings-footer">
    <div><strong>{usingRecommended() ? 'Recommended' : 'Custom'}</strong><span>{gb(ramMb).toFixed(1)} GB maximum</span></div>
    <button class="primary" disabled={busy || !dirty()} onclick={save}>{busy ? 'Saving…' : dirty() ? 'Save changes' : 'Saved'}</button>
  </footer>
</section>

<style>
  .settings-page{width:min(920px,100%)}.page-head{margin-bottom:18px}.page-head h2{margin:0;font-size:18px}.page-head p{margin:4px 0 0;color:var(--muted);font-size:12px}.notice{display:grid;gap:3px;margin-bottom:12px;padding:10px 12px;border-radius:8px;font-size:11px}.notice.error{border:1px solid #713940;background:var(--danger-bg);color:#ffdadd}.notice.success{border:1px solid var(--accent-border);background:var(--accent-soft);color:#a8e5b8}
  .settings-group{display:grid;grid-template-columns:180px minmax(0,1fr);gap:28px;padding:20px 0}.group-copy h3{margin:0;font-size:12px}.group-copy p{margin:5px 0 0;color:var(--muted);font-size:11px;line-height:1.45}.group-content{min-width:0;display:grid;gap:10px}
  .recommendation{width:100%;min-height:66px;display:flex;align-items:center;justify-content:space-between;gap:16px;padding:12px 14px;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface);color:var(--text);text-align:left;cursor:pointer}.recommendation:hover:not(:disabled){background:var(--surface-2)}.recommendation.selected{border-color:var(--accent-border);background:var(--accent-soft)}.recommendation>div{display:grid;gap:2px}.recommendation strong{font-size:12px}.recommendation small{color:var(--muted);font-size:10px}.recommendation>span{font-size:12px;font-weight:750;color:var(--text-soft)}
  .memory-control{padding:13px;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}.memory-head{display:flex;justify-content:space-between;align-items:flex-end;gap:18px}.memory-head>div{display:grid;gap:2px}.memory-head span,.memory-head small{color:var(--muted);font-size:10px}.memory-head strong{font-size:18px}input[type='range']{width:100%;min-height:auto;margin:16px 0 4px;padding:0;border:0;background:transparent;accent-color:var(--accent)}.range-labels{display:flex;justify-content:space-between;color:var(--muted-2);font-size:9px}
  .warning-card{margin-top:12px;padding:10px 12px;border:1px solid #6b5730;border-radius:8px;background:var(--warning-bg);color:#ebd9aa;font-size:11px}.warning-card p{margin:4px 0 0}.settings-footer{display:flex;align-items:center;justify-content:flex-end;gap:14px;margin-top:20px;padding-top:16px;border-top:1px solid var(--border-soft)}.settings-footer>div{display:grid;gap:1px;text-align:right}.settings-footer strong{font-size:10px}.settings-footer span{color:var(--muted);font-size:9px}.primary{min-height:var(--control-height);border:1px solid var(--accent);border-radius:8px;padding:8px 13px;background:var(--accent);color:var(--accent-ink);font-weight:650;cursor:pointer}.primary:hover:not(:disabled){background:var(--accent-hover)}button:disabled{opacity:.5;cursor:default}@media(max-width:760px){.settings-group{grid-template-columns:1fr;gap:10px}.settings-footer{align-items:stretch;flex-direction:column}.settings-footer>div{text-align:left}.memory-head{align-items:flex-start;flex-direction:column}}
</style>
