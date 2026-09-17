<script lang="ts">
  import StartupRecoveryCenter from './StartupRecoveryCenter.svelte';
  import type { StartupReport } from '../app/bridge/runtimeApi';

  export let report: StartupReport | null = null;
  export let statusError = '';
  export let onOpenActivity: () => void;
  export let onOpenSupport: () => void;
</script>

<section class="blocked-shell" aria-labelledby="blocked-heading">
  <div class="blocked-copy">
    <span class="eyebrow">Protected startup</span>
    <h1 id="blocked-heading">Server management is temporarily unavailable</h1>
    <p>LazyBuilder stopped before enabling server-changing controls because startup safety could not be confirmed. Your registered server files are not modified by this screen.</p>
    <div class="blocked-actions">
      <button class="primary" onclick={onOpenActivity}>Open Activity</button>
      <button class="secondary" onclick={onOpenSupport}>Open Support settings</button>
    </div>
  </div>

  {#if report}
    <StartupRecoveryCenter {report} />
  {:else}
    <div class="status-unavailable" role="alert">
      <strong>Startup status unavailable</strong>
      <span>{statusError || 'LazyBuilder could not verify the startup recovery report for this session.'}</span>
    </div>
  {/if}

  <aside class="safety-note" aria-label="Startup safety behavior">
    <strong>Fail-closed behavior</strong>
    <span>Server start, plugin changes, world management, client synchronization, and workspace mutations stay unavailable until a future Launcher startup reports that core recovery checks are ready.</span>
  </aside>
</section>

<style>
  .blocked-shell{width:min(920px,100%);display:grid;gap:16px;margin:0 auto;padding:34px 0 56px}.blocked-copy{display:grid;gap:8px;padding:18px;border:1px solid #6d3d42;border-radius:12px;background:linear-gradient(180deg,rgba(127,29,29,.14),var(--surface))}.eyebrow{color:var(--muted-2);font-size:8px;font-weight:800;text-transform:uppercase;letter-spacing:.07em}.blocked-copy h1{font-size:22px}.blocked-copy p{max-width:720px;margin:0;color:var(--muted);font-size:11px;line-height:1.55}.blocked-actions{display:flex;gap:8px;margin-top:5px}.primary,.secondary{min-height:36px;padding:8px 13px;border-radius:8px;font-weight:700;cursor:pointer}.primary{border:1px solid var(--accent);background:var(--accent);color:var(--accent-ink)}.secondary{border:1px solid var(--border);background:var(--surface-2);color:var(--text)}.status-unavailable,.safety-note{display:grid;gap:3px;padding:12px 13px;border:1px solid var(--border-soft);border-radius:9px;background:var(--bg-elevated)}.status-unavailable{border-color:#6d3d42}.status-unavailable strong,.safety-note strong{font-size:10px}.status-unavailable span,.safety-note span{color:var(--muted);font-size:9px;line-height:1.5}@media(max-width:760px){.blocked-shell{padding:20px 0 42px}.blocked-actions{align-items:stretch;flex-direction:column}.blocked-actions button{width:100%}}
</style>
