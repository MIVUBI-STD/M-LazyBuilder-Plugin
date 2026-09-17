<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { ServerBackupEstimate, ServerBackupSummary, ServerResourceProfile, ServerState, WorkspaceEntry } from '../app/bridge/runtimeApi';

  const ACTIVE_RUNTIME_STATES = new Set<ServerState>(['Starting', 'Online', 'Stopping', 'Detached']);

  let workspace: WorkspaceEntry | null = null;
  let resources: ServerResourceProfile | null = null;
  let serverState: ServerState = 'Offline';
  let backupEstimate: ServerBackupEstimate | null = null;
  let latestBackup: ServerBackupSummary | null = null;
  let backupCount = 0;
  let loading = true;
  let loadError = '';
  let updatedAt: Date | null = null;

  function formatBytes(bytes?: number | null) {
    if (bytes == null || !Number.isFinite(bytes)) return 'Unavailable';
    if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
    return `${Math.ceil(bytes / 1024 ** 2)} MB`;
  }

  function formatBackupDate(seconds?: number | null) {
    if (!seconds) return 'No backup yet';
    return new Date(seconds * 1000).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
  }

  function snapshotLabel() {
    if (!updatedAt) return 'Snapshot data';
    return `Snapshot updated ${updatedAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
  }

  function serverSizeLabel() {
    if (backupEstimate) return formatBytes(backupEstimate.sourceBytes);
    if (runtimeActive && latestBackup) return formatBytes(latestBackup.sourceBytes);
    return 'Unavailable';
  }

  function serverSizeHint() {
    if (!workspace) return 'No server open';
    if (backupEstimate) return workspace.name;
    if (runtimeActive && latestBackup) return 'Last restore-point size; stop server for a current estimate';
    if (runtimeActive) return 'Stop server to calculate current size';
    return 'Refresh to calculate current size and disk headroom';
  }

  async function refresh() {
    loading = true;
    loadError = '';
    try {
      const state = await runtimeProduct.workspace.state();
      workspace = state.active ?? null;
      const [snapshot, nextResources] = await Promise.all([
        runtimeProduct.server.snapshot().catch(() => null),
        runtimeProduct.server.resources().catch(() => null)
      ]);
      serverState = snapshot?.state ?? 'Offline';
      resources = nextResources;

      if (!workspace) {
        backupEstimate = null;
        latestBackup = null;
        backupCount = 0;
        updatedAt = new Date();
        return;
      }

      const backups = await runtimeProduct.backups.list(workspace.id).catch(() => []);
      backupCount = backups.length;
      latestBackup = backups[0] ?? null;
      backupEstimate = ACTIVE_RUNTIME_STATES.has(serverState)
        ? null
        : await runtimeProduct.backups.estimate(workspace.id).catch(() => null);
      updatedAt = new Date();
    } catch (value) {
      loadError = value instanceof Error ? value.message : 'Resource information is unavailable.';
    } finally {
      loading = false;
    }
  }

  onMount(() => void refresh());

  $: runtimeActive = ACTIVE_RUNTIME_STATES.has(serverState);
</script>

<section class="resource-overview" aria-labelledby="resource-overview-heading">
  <header>
    <div><h3 id="resource-overview-heading">Resources</h3><p>Storage and server capacity snapshot. {snapshotLabel()}.</p></div>
    {#if !loading}<button class="refresh-button" onclick={refresh}>Refresh</button>{/if}
  </header>

  {#if loading}
    <div class="resource-state" aria-live="polite">Checking resources…</div>
  {:else if loadError}
    <div class="resource-state warning"><strong>Resource summary unavailable</strong><span>{loadError}</span></div>
  {:else}
    <div class="resource-grid">
      <div><span>This server</span><strong>{serverSizeLabel()}</strong><small title={serverSizeHint()}>{serverSizeHint()}</small></div>
      <div><span>Restore points</span><strong>{workspace ? backupCount : '—'}</strong><small>{workspace ? `Last backup: ${formatBackupDate(latestBackup?.createdUnixSeconds)}` : 'Open a server to inspect'}</small></div>
      <div><span>Disk available</span><strong>{runtimeActive ? 'Check offline' : formatBytes(backupEstimate?.availableBytes)}</strong><small>{runtimeActive ? 'Stop this server for a safe backup estimate' : backupEstimate ? `Backup needs about ${formatBytes(backupEstimate.requiredBytes)}` : 'Refresh to calculate backup storage'}</small></div>
      <div><span>Server RAM limit</span><strong>{resources ? `${(resources.currentMaxMemoryMb / 1024).toFixed(1)} GB` : 'Unavailable'}</strong><small>{resources ? `Safe max ${(resources.safeMaxMemoryMb / 1024).toFixed(1)} GB` : 'Open server settings for details'}</small></div>
    </div>
  {/if}
</section>

<style>
  .resource-overview{margin-top:16px;padding:15px;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}.resource-overview>header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:11px}.resource-overview h3{margin:0;font-size:14px}.resource-overview header p{margin:3px 0 0;color:var(--muted);font-size:10px}.refresh-button{min-height:30px;padding:6px 9px;border:1px solid var(--border);border-radius:7px;background:var(--surface-2);color:var(--text-soft);font-size:9px;font-weight:650;cursor:pointer}.resource-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));overflow:hidden;border:1px solid var(--border-soft);border-radius:9px;background:var(--bg-elevated)}.resource-grid>div{display:grid;gap:3px;padding:10px 11px;border-right:1px solid var(--border-soft);border-bottom:1px solid var(--border-soft)}.resource-grid>div:nth-child(2n){border-right:0}.resource-grid>div:nth-last-child(-n+2){border-bottom:0}.resource-grid span{color:var(--muted-2);font-size:8px;text-transform:uppercase;letter-spacing:.04em}.resource-grid strong{font-size:12px}.resource-grid small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--muted);font-size:8px}.resource-state{min-height:72px;display:flex;align-items:center;justify-content:center;color:var(--muted);font-size:10px}.resource-state.warning{display:grid;place-content:center;gap:3px;text-align:center}.resource-state.warning strong{color:var(--text-soft);font-size:10px}.resource-state.warning span{color:var(--muted);font-size:9px}@media(max-width:760px){.resource-grid{grid-template-columns:1fr}.resource-grid>div{border-right:0;border-bottom:1px solid var(--border-soft)}.resource-grid>div:nth-last-child(-n+2){border-bottom:1px solid var(--border-soft)}.resource-grid>div:last-child{border-bottom:0}}
</style>
