<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { ServerBackupEstimate, ServerResourceProfile, ServerRuntimeSummary, WorkspaceEntry } from '../app/bridge/runtimeApi';

  const MAX_CONCURRENT_SERVERS = 3;

  let workspace: WorkspaceEntry | null = null;
  let runtimes: ServerRuntimeSummary[] = [];
  let resources: ServerResourceProfile | null = null;
  let backupEstimate: ServerBackupEstimate | null = null;
  let backupCount = 0;
  let loading = true;
  let loadError = '';

  function formatBytes(bytes?: number | null) {
    if (bytes == null || !Number.isFinite(bytes)) return 'Unavailable';
    if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
    return `${Math.ceil(bytes / 1024 ** 2)} MB`;
  }

  function activeRuntimes() {
    return runtimes.filter((runtime) => ['Starting', 'Online', 'Stopping', 'Detached'].includes(runtime.state));
  }

  function managedRamBytes() {
    return activeRuntimes().reduce((total, runtime) => total + runtime.usedMemoryBytes, 0);
  }

  async function refresh() {
    loading = true;
    loadError = '';
    try {
      const state = await runtimeProduct.workspace.state();
      workspace = state.active ?? null;
      const [nextRuntimes, nextResources] = await Promise.all([
        runtimeProduct.server.runtimes(),
        runtimeProduct.server.resources().catch(() => null)
      ]);
      runtimes = nextRuntimes;
      resources = nextResources;

      if (!workspace) {
        backupEstimate = null;
        backupCount = 0;
        return;
      }

      const [estimate, backups] = await Promise.all([
        runtimeProduct.backups.estimate(workspace.id).catch(() => null),
        runtimeProduct.backups.list(workspace.id).catch(() => [])
      ]);
      backupEstimate = estimate;
      backupCount = backups.length;
    } catch (value) {
      loadError = value instanceof Error ? value.message : 'Resource information is unavailable.';
    } finally {
      loading = false;
    }
  }

  onMount(() => void refresh());
</script>

<section class="resource-overview" aria-labelledby="resource-overview-heading">
  <header>
    <div><h3 id="resource-overview-heading">Resources</h3><p>Capacity and storage signals for this Launcher session.</p></div>
    {#if !loading}<button class="refresh-button" onclick={refresh}>Refresh</button>{/if}
  </header>

  {#if loading}
    <div class="resource-state" aria-live="polite">Checking resources…</div>
  {:else if loadError}
    <div class="resource-state warning"><strong>Resource summary unavailable</strong><span>{loadError}</span></div>
  {:else}
    <div class="resource-grid">
      <div><span>Running servers</span><strong>{activeRuntimes().length} / {MAX_CONCURRENT_SERVERS}</strong><small>LazyBuilder runtime limit</small></div>
      <div><span>Managed RAM</span><strong>{formatBytes(managedRamBytes())}</strong><small>{resources ? `${Math.round(resources.totalMemoryMb / 1024)} GB system memory` : 'Current managed usage'}</small></div>
      <div><span>This server</span><strong>{formatBytes(backupEstimate?.sourceBytes)}</strong><small>{workspace ? workspace.name : 'No server open'}</small></div>
      <div><span>Restore points</span><strong>{workspace ? backupCount : '—'}</strong><small>{workspace ? 'Available for this server' : 'Open a server to inspect'}</small></div>
      <div><span>Disk available</span><strong>{formatBytes(backupEstimate?.availableBytes)}</strong><small>{backupEstimate ? `Backup needs about ${formatBytes(backupEstimate.requiredBytes)}` : 'Storage estimate unavailable'}</small></div>
      <div><span>Server RAM limit</span><strong>{resources ? `${(resources.currentMaxMemoryMb / 1024).toFixed(1)} GB` : 'Unavailable'}</strong><small>{resources ? `Safe max ${(resources.safeMaxMemoryMb / 1024).toFixed(1)} GB` : 'Open server settings for details'}</small></div>
    </div>
  {/if}
</section>

<style>
  .resource-overview{margin-top:16px;padding:15px;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}.resource-overview>header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:11px}.resource-overview h3{margin:0;font-size:14px}.resource-overview header p{margin:3px 0 0;color:var(--muted);font-size:10px}.refresh-button{min-height:30px;padding:6px 9px;border:1px solid var(--border);border-radius:7px;background:var(--surface-2);color:var(--text-soft);font-size:9px;font-weight:650;cursor:pointer}.resource-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));overflow:hidden;border:1px solid var(--border-soft);border-radius:9px;background:var(--bg-elevated)}.resource-grid>div{display:grid;gap:3px;padding:10px 11px;border-right:1px solid var(--border-soft);border-bottom:1px solid var(--border-soft)}.resource-grid>div:nth-child(3n){border-right:0}.resource-grid>div:nth-last-child(-n+3){border-bottom:0}.resource-grid span{color:var(--muted-2);font-size:8px;text-transform:uppercase;letter-spacing:.04em}.resource-grid strong{font-size:12px}.resource-grid small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--muted);font-size:8px}.resource-state{min-height:72px;display:flex;align-items:center;justify-content:center;color:var(--muted);font-size:10px}.resource-state.warning{display:grid;place-content:center;gap:3px;text-align:center}.resource-state.warning strong{color:var(--text-soft);font-size:10px}.resource-state.warning span{color:var(--muted);font-size:9px}
  @media(max-width:760px){.resource-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.resource-grid>div,.resource-grid>div:nth-child(3n),.resource-grid>div:nth-last-child(-n+3){border-right:1px solid var(--border-soft);border-bottom:1px solid var(--border-soft)}.resource-grid>div:nth-child(2n){border-right:0}.resource-grid>div:nth-last-child(-n+2){border-bottom:0}}
</style>
