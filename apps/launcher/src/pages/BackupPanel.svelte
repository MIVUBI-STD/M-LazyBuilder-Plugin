<script lang="ts">
  import { onMount } from 'svelte';
  import RuntimeErrorNotice from '../components/RuntimeErrorNotice.svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import { dialogFocus } from '../app/dialogFocus';
  import { presentRuntimeError } from '../app/runtimeErrorPresentation';
  import type { RuntimeErrorPresentation } from '../app/runtimeErrorPresentation';
  import type { ServerBackupEstimate, ServerBackupSummary, ServerSnapshot, WorkspaceEntry } from '../app/bridge/runtimeApi';

  export let onRestored: (() => Promise<void> | void) | undefined = undefined;

  const BACKUP_PAGE_SIZE = 20;

  let workspace: WorkspaceEntry | null = null;
  let backups: ServerBackupSummary[] = [];
  let estimate: ServerBackupEstimate | null = null;
  let estimateBusy = false;
  let visibleLimit = BACKUP_PAGE_SIZE;
  let snapshot: ServerSnapshot | null = null;
  let loading = true;
  let creating = false;
  let restoringId = '';
  let deletingId = '';
  let restoreCandidate: ServerBackupSummary | null = null;
  let deleteCandidate: ServerBackupSummary | null = null;
  let error: RuntimeErrorPresentation | null = null;
  let notice = '';

  function formatBytes(bytes?: number | null) {
    if (bytes == null || !Number.isFinite(bytes)) return 'Not calculated';
    if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
    return `${Math.ceil(bytes / 1024 ** 2)} MB`;
  }

  function formatDate(seconds: number) {
    return new Date(seconds * 1000).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
  }

  function closeConfirmation() {
    if (mutationBusy) return;
    restoreCandidate = null;
    deleteCandidate = null;
  }

  async function refresh() {
    const state = await runtimeProduct.workspace.state();
    workspace = state.active ?? null;
    if (!workspace) {
      backups = [];
      estimate = null;
      snapshot = null;
      visibleLimit = BACKUP_PAGE_SIZE;
      restoreCandidate = null;
      deleteCandidate = null;
      return;
    }
    const [items, runtime] = await Promise.all([
      runtimeProduct.backups.list(workspace.id),
      runtimeProduct.server.snapshot()
    ]);
    backups = items;
    snapshot = runtime;
    visibleLimit = Math.max(BACKUP_PAGE_SIZE, Math.min(visibleLimit, Math.max(items.length, BACKUP_PAGE_SIZE)));
  }

  async function calculateEstimate() {
    if (!workspace || estimateBusy || mutationBusy) return;
    estimateBusy = true;
    error = null;
    try { estimate = await runtimeProduct.backups.estimate(workspace.id); }
    catch (value) { error = presentRuntimeError(value, 'Could not calculate backup storage.'); estimate = null; }
    finally { estimateBusy = false; }
  }

  async function initialLoad() {
    loading = true;
    error = null;
    try { await refresh(); }
    catch (value) { error = presentRuntimeError(value, 'Could not load server backups.'); }
    finally { loading = false; }
  }

  async function createBackup() {
    if (!workspace || creating || restoringId || deletingId) return;
    creating = true;
    error = null;
    notice = '';
    try {
      const backup = await runtimeProduct.backups.create(workspace.id);
      notice = `Backup created: ${formatDate(backup.createdUnixSeconds)}.`;
      estimate = null;
      await refresh();
    } catch (value) {
      error = presentRuntimeError(value, 'Could not create server backup.');
    } finally {
      creating = false;
    }
  }

  async function restoreBackup() {
    const backup = restoreCandidate;
    if (!workspace || !backup || restoringId || creating || deletingId || !serverOffline) return;
    restoringId = backup.id;
    error = null;
    notice = '';
    try {
      const result = await runtimeProduct.backups.restore(workspace.id, backup.id);
      restoreCandidate = null;
      notice = result.cleanupPending
        ? 'Server restored successfully. Final cleanup will finish automatically the next time LazyBuilder starts.'
        : `Server restored. The previous server state was backed up at ${formatDate(result.safetyBackup.createdUnixSeconds)}.`;
      estimate = null;
      await refresh();
      await onRestored?.();
    } catch (value) {
      error = presentRuntimeError(value, 'Could not restore this server backup.');
    } finally {
      restoringId = '';
    }
  }

  async function deleteBackup() {
    const backup = deleteCandidate;
    if (!workspace || !backup || deletingId || restoringId || creating) return;
    deletingId = backup.id;
    error = null;
    notice = '';
    try {
      await runtimeProduct.backups.delete(workspace.id, backup.id);
      deleteCandidate = null;
      notice = 'Backup deleted.';
      await refresh();
    } catch (value) {
      error = presentRuntimeError(value, 'Could not delete this server backup.');
    } finally {
      deletingId = '';
    }
  }

  onMount(() => { void initialLoad(); });

  $: serverOffline = snapshot ? ['Offline', 'Crashed'].includes(snapshot.state) : false;
  $: mutationBusy = creating || !!restoringId || !!deletingId;
  $: visibleBackups = backups.slice(0, visibleLimit);
</script>

<section class="backup-panel" aria-labelledby="backup-heading">
  <header class="backup-heading">
    <div>
      <h3 id="backup-heading">Server backups</h3>
      <p>Full restore points for worlds, configuration, plugins, and plugin data.</p>
    </div>
    <button class="backup-button" disabled={loading || mutationBusy || !workspace || !serverOffline} onclick={createBackup}>
      {creating ? 'Creating backup…' : 'Create backup'}
    </button>
  </header>

  <RuntimeErrorNotice {error} />
  {#if notice}<div class="backup-notice success" aria-live="polite">{notice}</div>{/if}

  {#if loading}
    <div class="backup-empty">Loading backups…</div>
  {:else if !workspace}
    <div class="backup-empty">Open a server to manage its backups.</div>
  {:else}
    <div class="backup-summary">
      <div><span>Server data</span><strong>{formatBytes(estimate?.sourceBytes)}</strong></div>
      <div><span>Space needed</span><strong>{formatBytes(estimate?.requiredBytes)}</strong></div>
      <div><span>Available space</span><strong>{formatBytes(estimate?.availableBytes)}</strong></div>
    </div>
    <div class="estimate-actions">
      <span>Check storage to confirm there is enough free space for a full backup.</span>
      <button class="estimate-button" disabled={estimateBusy || mutationBusy} onclick={calculateEstimate}>{estimateBusy ? 'Calculating…' : estimate ? 'Recalculate storage' : 'Check storage'}</button>
    </div>

    {#if !serverOffline}
      <div class="offline-note">Stop the server before creating or restoring a full backup so world and plugin data remain consistent.</div>
    {/if}

    {#if backups.length === 0}
      <div class="backup-empty compact"><strong>No backups yet</strong><span>Create one before major changes or updates.</span></div>
    {:else}
      <div class="backup-list">
        {#each visibleBackups as backup (backup.id)}
          <article class="backup-row">
            <div class="backup-icon" aria-hidden="true">↻</div>
            <div class="backup-copy">
              <strong>{formatDate(backup.createdUnixSeconds)}</strong>
              <span>{formatBytes(backup.sourceBytes)} · full server restore point</span>
            </div>
            <div class="backup-actions">
              <button class="restore-button" disabled={mutationBusy || !serverOffline} onclick={() => { restoreCandidate = backup; deleteCandidate = null; }}>
                {restoringId === backup.id ? 'Restoring…' : 'Restore'}
              </button>
              <button class="delete-button" disabled={mutationBusy} onclick={() => { deleteCandidate = backup; restoreCandidate = null; }}>
                {deletingId === backup.id ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          </article>
        {/each}
      </div>
      {#if visibleBackups.length < backups.length}
        <button class="show-more" onclick={() => (visibleLimit += BACKUP_PAGE_SIZE)}>Show more backups ({backups.length - visibleBackups.length} remaining)</button>
      {/if}
    {/if}
  {/if}
</section>

{#if restoreCandidate}
  <div class="confirm-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && closeConfirmation()}>
    <section
      use:dialogFocus={{ onEscape: closeConfirmation, initialFocusSelector: '.cancel-button', escapeDisabled: mutationBusy }}
      class="confirm-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="restore-backup-title"
    >
      <header><div><h3 id="restore-backup-title">Restore this server?</h3><p>Return the complete server to {formatDate(restoreCandidate.createdUnixSeconds)}.</p></div><button class="close-button" disabled={mutationBusy} aria-label="Close restore confirmation" onclick={closeConfirmation}>×</button></header>
      <div class="safety-note"><strong>Your current server will be protected first.</strong><span>LazyBuilder creates a full safety backup before replacing the current server state. Keep the server offline until restore finishes.</span></div>
      <div class="confirm-actions"><button class="cancel-button" disabled={mutationBusy} onclick={closeConfirmation}>Cancel</button><button class="restore-confirm" disabled={mutationBusy || !serverOffline} onclick={restoreBackup}>{restoringId ? 'Restoring…' : 'Restore server'}</button></div>
    </section>
  </div>
{/if}

{#if deleteCandidate}
  <div class="confirm-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && closeConfirmation()}>
    <section
      use:dialogFocus={{ onEscape: closeConfirmation, initialFocusSelector: '.cancel-button', escapeDisabled: mutationBusy }}
      class="confirm-dialog danger-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="delete-backup-title"
    >
      <header><div><h3 id="delete-backup-title">Delete this restore point?</h3><p>{formatDate(deleteCandidate.createdUnixSeconds)} · {formatBytes(deleteCandidate.sourceBytes)}</p></div><button class="close-button" disabled={mutationBusy} aria-label="Close delete restore point confirmation" onclick={closeConfirmation}>×</button></header>
      <div class="delete-note"><strong>This restore point will be permanently removed.</strong><span>The current server and other backups are not affected.</span></div>
      <div class="confirm-actions"><button class="cancel-button" disabled={mutationBusy} onclick={closeConfirmation}>Cancel</button><button class="delete-confirm" disabled={mutationBusy} onclick={deleteBackup}>{deletingId ? 'Deleting…' : 'Delete restore point'}</button></div>
    </section>
  </div>
{/if}

<style>
  .backup-panel{margin-top:16px;padding:16px;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}
  .backup-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.backup-heading h3{margin:0;font-size:14px}.backup-heading p{margin:3px 0 0;color:var(--muted);font-size:10px}
  .backup-button,.restore-button,.delete-button,.estimate-button,.show-more{min-height:34px;border-radius:8px;font-weight:700;cursor:pointer}.backup-button{padding:7px 12px;border:1px solid var(--accent-border);background:var(--accent-soft);color:#9ee8b9}.restore-button,.delete-button,.estimate-button,.show-more{padding:6px 10px;border:1px solid var(--border);background:var(--surface-2);color:var(--text-soft);font-size:10px}.restore-button{border-color:var(--accent-border);color:#b7f0cb}.backup-button:disabled,.restore-button:disabled,.delete-button:disabled,.estimate-button:disabled{opacity:.5;cursor:default}
  .backup-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:7px;margin-top:13px}.backup-summary>div{display:grid;gap:3px;padding:9px 10px;border:1px solid var(--border-soft);border-radius:8px;background:var(--bg-elevated)}.backup-summary span{color:var(--muted-2);font-size:8px;text-transform:uppercase}.backup-summary strong{font-size:11px}.estimate-actions{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:8px}.estimate-actions span{color:var(--muted);font-size:9px}.show-more{margin-top:10px}
  .offline-note,.backup-notice{margin-top:11px;padding:10px 11px;border-radius:8px;font-size:10px}.offline-note{border:1px solid #5f5125;background:var(--warning-bg);color:var(--text-soft)}.backup-notice.success{border:1px solid var(--accent-border);background:var(--accent-soft);color:#b7f0cb}
  .backup-list{display:grid;margin-top:12px}.backup-row{display:grid;grid-template-columns:auto minmax(0,1fr) auto;gap:10px;align-items:center;padding:10px 2px;border-top:1px solid var(--border-soft)}.backup-row:first-child{border-top:0}.backup-icon{width:30px;height:30px;display:grid;place-items:center;border-radius:8px;background:var(--surface-2);color:var(--muted)}.backup-copy{display:grid;gap:2px;min-width:0}.backup-copy strong{font-size:11px}.backup-copy span{color:var(--muted);font-size:9px}.backup-actions{display:flex;gap:6px}
  .backup-empty{min-height:92px;display:flex;align-items:center;justify-content:center;color:var(--muted);font-size:10px}.backup-empty.compact{min-height:84px;flex-direction:column;gap:3px}.backup-empty.compact strong{color:var(--text-soft);font-size:11px}
  .confirm-backdrop{position:fixed;z-index:120;inset:0;display:grid;place-items:center;padding:24px;background:rgba(4,6,8,.74);backdrop-filter:blur(4px)}.confirm-dialog{width:min(470px,100%);display:grid;gap:15px;padding:19px;border:1px solid var(--border);border-radius:13px;background:var(--surface);box-shadow:var(--shadow-popover)}.confirm-dialog.danger-dialog{border-color:#6c363d}.confirm-dialog header{display:flex;align-items:flex-start;justify-content:space-between;gap:14px}.confirm-dialog h3{margin:0;font-size:16px}.confirm-dialog header p{margin:4px 0 0;color:var(--muted);font-size:10px}.close-button{width:30px;height:30px;border:0;border-radius:7px;background:transparent;color:var(--muted);font-size:19px;cursor:pointer}.safety-note,.delete-note{display:grid;gap:4px;padding:11px 12px;border:1px solid var(--border-soft);border-radius:8px;background:var(--bg-elevated)}.safety-note strong,.delete-note strong{font-size:10px}.safety-note span,.delete-note span{color:var(--muted);font-size:10px;line-height:1.45}.delete-note{border-color:#6c363d;background:var(--danger-bg)}.confirm-actions{display:flex;justify-content:flex-end;gap:8px}.cancel-button,.restore-confirm,.delete-confirm{min-height:34px;padding:7px 11px;border-radius:8px;font-weight:700;cursor:pointer}.cancel-button{border:1px solid var(--border);background:var(--surface-2);color:var(--text)}.restore-confirm{border:1px solid var(--accent);background:var(--accent);color:var(--accent-ink)}.delete-confirm{border:1px solid #a74650;background:#8d3039;color:#fff}.confirm-dialog button:disabled{opacity:.5;cursor:default}
  @media(max-width:760px){.backup-heading,.estimate-actions{align-items:flex-start;flex-direction:column}.backup-summary{grid-template-columns:1fr}.backup-row{grid-template-columns:auto minmax(0,1fr)}.backup-actions{grid-column:2;justify-content:flex-start}.confirm-backdrop{padding:14px}.confirm-actions{align-items:stretch;flex-direction:column-reverse}}
</style>