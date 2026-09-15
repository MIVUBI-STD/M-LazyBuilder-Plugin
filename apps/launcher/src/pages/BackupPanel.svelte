<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { ServerBackupEstimate, ServerBackupSummary, ServerSnapshot, WorkspaceEntry } from '../app/bridge/runtimeApi';

  let workspace: WorkspaceEntry | null = null;
  let backups: ServerBackupSummary[] = [];
  let estimate: ServerBackupEstimate | null = null;
  let snapshot: ServerSnapshot | null = null;
  let loading = true;
  let creating = false;
  let deletingId = '';
  let error = '';
  let notice = '';

  function friendlyError(value: unknown) {
    return value instanceof Error && value.message.trim()
      ? value.message.trim()
      : String(value ?? '').replace(/^Error:\s*/i, '').trim() || 'Something went wrong. Try again.';
  }

  function formatBytes(bytes?: number | null) {
    if (bytes == null || !Number.isFinite(bytes)) return 'Unknown';
    if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
    return `${Math.ceil(bytes / 1024 ** 2)} MB`;
  }

  function formatDate(seconds: number) {
    return new Date(seconds * 1000).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
  }

  async function refresh() {
    const state = await runtimeProduct.workspace.state();
    workspace = state.active ?? null;
    if (!workspace) {
      backups = [];
      estimate = null;
      snapshot = null;
      return;
    }
    const [items, runtime] = await Promise.all([
      runtimeProduct.backups.list(workspace.id),
      runtimeProduct.server.snapshot()
    ]);
    backups = items;
    snapshot = runtime;
    try { estimate = await runtimeProduct.backups.estimate(workspace.id); }
    catch { estimate = null; }
  }

  async function initialLoad() {
    loading = true;
    error = '';
    try { await refresh(); }
    catch (value) { error = friendlyError(value); }
    finally { loading = false; }
  }

  async function createBackup() {
    if (!workspace || creating) return;
    creating = true;
    error = '';
    notice = '';
    try {
      const backup = await runtimeProduct.backups.create(workspace.id);
      notice = `Backup created: ${formatDate(backup.createdUnixSeconds)}.`;
      await refresh();
    } catch (value) {
      error = friendlyError(value);
    } finally {
      creating = false;
    }
  }

  async function deleteBackup(backup: ServerBackupSummary) {
    if (!workspace || deletingId) return;
    if (!window.confirm(`Delete this backup from ${formatDate(backup.createdUnixSeconds)}? The current server is not affected.`)) return;
    deletingId = backup.id;
    error = '';
    notice = '';
    try {
      await runtimeProduct.backups.delete(workspace.id, backup.id);
      notice = 'Backup deleted.';
      await refresh();
    } catch (value) {
      error = friendlyError(value);
    } finally {
      deletingId = '';
    }
  }

  onMount(() => { void initialLoad(); });

  $: serverOffline = snapshot ? ['Offline', 'Crashed'].includes(snapshot.state) : false;
</script>

<section class="backup-panel" aria-labelledby="backup-heading">
  <header class="backup-heading">
    <div>
      <h3 id="backup-heading">Server backups</h3>
      <p>Restore points for the entire LazyBuilder server workspace.</p>
    </div>
    <button class="backup-button" disabled={loading || creating || !workspace || !serverOffline} onclick={createBackup}>
      {creating ? 'Creating backup…' : 'Create backup'}
    </button>
  </header>

  {#if error}<div class="backup-notice danger" role="alert">{error}</div>{/if}
  {#if notice}<div class="backup-notice success" aria-live="polite">{notice}</div>{/if}

  {#if loading}
    <div class="backup-empty">Loading backups…</div>
  {:else if !workspace}
    <div class="backup-empty">Open a server to manage its backups.</div>
  {:else}
    <div class="backup-summary">
      <div><span>Server data</span><strong>{formatBytes(estimate?.sourceBytes)}</strong></div>
      <div><span>Required with margin</span><strong>{formatBytes(estimate?.requiredBytes)}</strong></div>
      <div><span>Available</span><strong>{formatBytes(estimate?.availableBytes)}</strong></div>
    </div>

    {#if !serverOffline}
      <div class="offline-note">Stop the server before creating a full backup so world and plugin data are captured consistently.</div>
    {/if}

    {#if backups.length === 0}
      <div class="backup-empty compact"><strong>No backups yet</strong><span>Create one before major changes or updates.</span></div>
    {:else}
      <div class="backup-list">
        {#each backups as backup (backup.id)}
          <article class="backup-row">
            <div class="backup-icon" aria-hidden="true">↻</div>
            <div class="backup-copy">
              <strong>{formatDate(backup.createdUnixSeconds)}</strong>
              <span>{formatBytes(backup.sourceBytes)} · full server restore point</span>
            </div>
            <button class="delete-button" disabled={!!deletingId} onclick={() => deleteBackup(backup)}>
              {deletingId === backup.id ? 'Deleting…' : 'Delete'}
            </button>
          </article>
        {/each}
      </div>
    {/if}
  {/if}
</section>

<style>
  .backup-panel{margin-top:16px;padding:16px;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}
  .backup-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.backup-heading h3{margin:0;font-size:14px}.backup-heading p{margin:3px 0 0;color:var(--muted);font-size:10px}
  .backup-button,.delete-button{min-height:34px;border-radius:8px;font-weight:700;cursor:pointer}.backup-button{padding:7px 12px;border:1px solid var(--accent-border);background:var(--accent-soft);color:#9ee8b9}.delete-button{padding:6px 10px;border:1px solid var(--border);background:var(--surface-2);color:var(--text-soft);font-size:10px}.backup-button:disabled,.delete-button:disabled{opacity:.5;cursor:default}
  .backup-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:7px;margin-top:13px}.backup-summary>div{display:grid;gap:3px;padding:9px 10px;border:1px solid var(--border-soft);border-radius:8px;background:var(--bg-elevated)}.backup-summary span{color:var(--muted-2);font-size:8px;text-transform:uppercase}.backup-summary strong{font-size:11px}
  .offline-note,.backup-notice{margin-top:11px;padding:10px 11px;border-radius:8px;font-size:10px}.offline-note{border:1px solid #5f5125;background:var(--warning-bg);color:var(--text-soft)}.backup-notice.danger{border:1px solid #62343a;background:var(--danger-bg);color:#ffd9dc}.backup-notice.success{border:1px solid var(--accent-border);background:var(--accent-soft);color:#b7f0cb}
  .backup-list{display:grid;margin-top:12px}.backup-row{display:grid;grid-template-columns:auto minmax(0,1fr) auto;gap:10px;align-items:center;padding:10px 2px;border-top:1px solid var(--border-soft)}.backup-row:first-child{border-top:0}.backup-icon{width:30px;height:30px;display:grid;place-items:center;border-radius:8px;background:var(--surface-2);color:var(--muted)}.backup-copy{display:grid;gap:2px;min-width:0}.backup-copy strong{font-size:11px}.backup-copy span{color:var(--muted);font-size:9px}
  .backup-empty{min-height:92px;display:flex;align-items:center;justify-content:center;color:var(--muted);font-size:10px}.backup-empty.compact{min-height:84px;flex-direction:column;gap:3px}.backup-empty.compact strong{color:var(--text-soft);font-size:11px}
  @media(max-width:760px){.backup-heading{align-items:flex-start;flex-direction:column}.backup-summary{grid-template-columns:1fr}.backup-row{grid-template-columns:auto minmax(0,1fr)}.delete-button{grid-column:2;width:max-content}}
</style>
