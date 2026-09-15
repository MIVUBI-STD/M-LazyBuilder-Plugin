<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { LauncherOperationSnapshot } from '../app/bridge/runtimeApi';

  let operations: LauncherOperationSnapshot[] = [];
  let loading = true;
  let error = '';
  let cancellingId = '';

  const ACTIVE_STATES = new Set(['QUEUED', 'RUNNING', 'CANCELLING']);

  function isActive(operation: LauncherOperationSnapshot) {
    return ACTIVE_STATES.has(operation.state);
  }

  function labelKind(kind: string) {
    const labels: Record<string, string> = {
      'duplicate-server': 'Duplicate server',
      'backup-server': 'Backup server',
      'restore-server': 'Restore server',
      'repair-server': 'Repair server',
      'launcher-update': 'Launcher update',
      'download-runtime': 'Runtime download'
    };
    return labels[kind] ?? kind.split('-').map((word) => word ? word[0].toUpperCase() + word.slice(1) : word).join(' ');
  }

  function stateLabel(state: LauncherOperationSnapshot['state']) {
    return {
      QUEUED: 'Queued',
      RUNNING: 'Running',
      SUCCEEDED: 'Completed',
      FAILED: 'Failed',
      CANCELLING: 'Cancelling',
      CANCELLED: 'Cancelled',
      RECOVERY_REQUIRED: 'Recovery required'
    }[state];
  }

  function formatTime(seconds?: number | null) {
    if (!seconds) return '';
    return new Date(seconds * 1000).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
  }

  function progressPercent(operation: LauncherOperationSnapshot) {
    const progress = operation.progress;
    if (!progress?.total || progress.total <= 0) return null;
    return Math.max(0, Math.min(100, Math.round((progress.current / progress.total) * 100)));
  }

  function formatProgress(operation: LauncherOperationSnapshot) {
    const progress = operation.progress;
    if (!progress) return '';
    if (progress.unit === 'bytes') {
      const format = (value: number) => value >= 1024 ** 3
        ? `${(value / 1024 ** 3).toFixed(1)} GB`
        : `${Math.ceil(value / 1024 ** 2)} MB`;
      return progress.total ? `${format(progress.current)} / ${format(progress.total)}` : format(progress.current);
    }
    return progress.total ? `${progress.current} / ${progress.total} ${progress.unit}` : `${progress.current} ${progress.unit}`;
  }

  async function refresh(showLoading = false) {
    if (showLoading) loading = true;
    try {
      operations = await runtimeProduct.operations.list();
      error = '';
    } catch (value) {
      error = value instanceof Error ? value.message : 'Could not load launcher activity.';
    } finally {
      if (showLoading) loading = false;
    }
  }

  async function cancel(operation: LauncherOperationSnapshot) {
    if (!operation.canCancel || cancellingId) return;
    cancellingId = operation.id;
    error = '';
    try {
      await runtimeProduct.operations.cancel(operation.id);
      await refresh();
    } catch (value) {
      error = value instanceof Error ? value.message : 'Could not cancel this operation.';
    } finally {
      cancellingId = '';
    }
  }

  onMount(() => {
    void refresh(true);
    // Snapshot queries remain authoritative. Poll while this surface is mounted so
    // background operations that start after the page opens are also discovered.
    const timer = window.setInterval(() => void refresh(), 2000);
    return () => window.clearInterval(timer);
  });

  $: activeOperations = operations.filter(isActive);
  $: history = operations.filter((operation) => !isActive(operation));
</script>

<section class="activity-page" aria-label="Launcher activity">
  {#if error}<div class="activity-error" role="alert">{error}</div>{/if}

  {#if loading}
    <div class="activity-empty" aria-live="polite"><strong>Loading activity…</strong></div>
  {:else if operations.length === 0}
    <div class="activity-empty">
      <div class="empty-glyph" aria-hidden="true">✓</div>
      <strong>No launcher activity yet</strong>
      <span>Long-running server and launcher operations will appear here.</span>
    </div>
  {:else}
    <section class="activity-section" aria-labelledby="active-activity-heading">
      <div class="section-heading">
        <div><h2 id="active-activity-heading">In progress</h2><p>Operations continue even if you leave this page.</p></div>
        <span class="section-count">{activeOperations.length}</span>
      </div>

      {#if activeOperations.length === 0}
        <div class="quiet-state">No operations are currently running.</div>
      {:else}
        <div class="operation-list" aria-live="polite">
          {#each activeOperations as operation (operation.id)}
            <article class="operation-card active-operation">
              <div class="operation-main">
                <div class="operation-title-row">
                  <div><strong>{labelKind(operation.kind)}</strong><span class="operation-phase">{operation.status || operation.phase}</span></div>
                  <span class:warning-state={operation.state === 'CANCELLING'} class="state-badge">{stateLabel(operation.state)}</span>
                </div>
                {#if operation.details}<p class="operation-details">{operation.details}</p>{/if}
                {#if progressPercent(operation) !== null}
                  <div class="progress-block" aria-label={`${progressPercent(operation)} percent complete`}>
                    <div class="progress-track"><span style={`width:${progressPercent(operation)}%`}></span></div>
                    <div class="progress-meta"><span>{formatProgress(operation)}</span><strong>{progressPercent(operation)}%</strong></div>
                  </div>
                {:else if operation.progress}
                  <div class="progress-meta standalone-progress"><span>{formatProgress(operation)}</span></div>
                {/if}
                {#if operation.warnings.length}<div class="operation-warning">{operation.warnings.join(' ')}</div>{/if}
              </div>
              {#if operation.canCancel}
                <button class="cancel-button" disabled={cancellingId === operation.id || operation.state === 'CANCELLING'} onclick={() => cancel(operation)}>
                  {cancellingId === operation.id || operation.state === 'CANCELLING' ? 'Cancelling…' : 'Cancel'}
                </button>
              {/if}
            </article>
          {/each}
        </div>
      {/if}
    </section>

    <section class="activity-section" aria-labelledby="recent-activity-heading">
      <div class="section-heading"><div><h2 id="recent-activity-heading">Recent</h2><p>Latest completed and interrupted Launcher operations.</p></div><span class="section-count">{history.length}</span></div>
      {#if history.length === 0}
        <div class="quiet-state">Completed operations will appear here.</div>
      {:else}
        <div class="history-list">
          {#each history as operation (operation.id)}
            <article class="history-row">
              <div class:failed-icon={operation.state === 'FAILED' || operation.state === 'RECOVERY_REQUIRED'} class:cancelled-icon={operation.state === 'CANCELLED'} class="history-icon" aria-hidden="true">
                {operation.state === 'SUCCEEDED' ? '✓' : operation.state === 'CANCELLED' ? '–' : '!'}
              </div>
              <div class="history-copy">
                <div class="history-title"><strong>{labelKind(operation.kind)}</strong><span>{stateLabel(operation.state)}</span></div>
                <span class="history-status">{operation.error?.message || operation.status}</span>
                {#if operation.state === 'RECOVERY_REQUIRED'}<span class="recovery-note">Open the affected server before retrying or changing its files.</span>{/if}
              </div>
              <time datetime={operation.completedAtUnixSeconds ? new Date(operation.completedAtUnixSeconds * 1000).toISOString() : undefined}>{formatTime(operation.completedAtUnixSeconds ?? operation.updatedAtUnixSeconds)}</time>
            </article>
          {/each}
        </div>
      {/if}
    </section>
  {/if}
</section>

<style>
  .activity-page{display:flex;flex-direction:column;gap:28px;max-width:980px;margin:0 auto;padding-bottom:40px}.activity-error{border:1px solid rgba(239,68,68,.38);background:rgba(127,29,29,.18);color:#fecaca;border-radius:10px;padding:12px 14px}.activity-empty{min-height:280px;border:1px dashed var(--border,#30343b);border-radius:14px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;color:var(--muted,#9ca3af);text-align:center}.activity-empty strong{color:var(--text,#f4f4f5);font-size:16px}.empty-glyph{width:42px;height:42px;border-radius:50%;display:grid;place-items:center;background:rgba(34,197,94,.12);color:#86efac;font-size:20px;margin-bottom:4px}.activity-section{display:flex;flex-direction:column;gap:12px}.section-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:16px}.section-heading h2{margin:0;color:var(--text,#f4f4f5);font-size:16px}.section-heading p{margin:4px 0 0;color:var(--muted,#9ca3af);font-size:13px}.section-count{min-width:28px;height:24px;padding:0 8px;border:1px solid var(--border,#30343b);border-radius:999px;display:grid;place-items:center;color:var(--muted,#9ca3af);font-size:12px}.quiet-state{border:1px solid var(--border,#30343b);border-radius:12px;padding:18px;color:var(--muted,#9ca3af);font-size:13px}.operation-list,.history-list{display:flex;flex-direction:column;gap:10px}.operation-card{display:flex;gap:18px;align-items:flex-start;border:1px solid var(--border,#30343b);background:var(--panel,#17191d);border-radius:12px;padding:16px}.active-operation{border-color:rgba(99,102,241,.42)}.operation-main{flex:1;min-width:0}.operation-title-row{display:flex;justify-content:space-between;gap:16px;align-items:flex-start}.operation-title-row>div{display:flex;flex-direction:column;gap:4px;min-width:0}.operation-title-row strong{color:var(--text,#f4f4f5)}.operation-phase,.operation-details,.progress-meta,.history-status,.recovery-note{color:var(--muted,#9ca3af);font-size:12px}.state-badge{white-space:nowrap;border-radius:999px;padding:4px 8px;background:rgba(99,102,241,.14);color:#c7d2fe;font-size:11px;font-weight:650}.warning-state{background:rgba(245,158,11,.13);color:#fde68a}.operation-details{margin:10px 0 0;line-height:1.45}.progress-block{margin-top:14px}.progress-track{height:6px;border-radius:999px;background:rgba(148,163,184,.15);overflow:hidden}.progress-track span{display:block;height:100%;border-radius:inherit;background:var(--accent,#7c83ff);transition:width .2s ease}.progress-meta{display:flex;justify-content:space-between;gap:12px;margin-top:6px}.progress-meta strong{color:var(--text,#f4f4f5);font-size:12px}.standalone-progress{margin-top:12px}.operation-warning,.recovery-note{display:block;margin-top:10px;color:#fde68a}.cancel-button{flex:0 0 auto;border:1px solid var(--border,#3f444d);background:transparent;color:var(--text,#f4f4f5);border-radius:8px;padding:7px 11px;cursor:pointer}.cancel-button:hover:not(:disabled){background:rgba(255,255,255,.05)}.cancel-button:disabled{opacity:.5;cursor:not-allowed}.history-row{display:grid;grid-template-columns:34px minmax(0,1fr) auto;gap:12px;align-items:center;border-bottom:1px solid var(--border,#2b2f36);padding:12px 4px}.history-row:last-child{border-bottom:0}.history-icon{width:28px;height:28px;border-radius:50%;display:grid;place-items:center;background:rgba(34,197,94,.12);color:#86efac;font-weight:700}.failed-icon{background:rgba(239,68,68,.12);color:#fca5a5}.cancelled-icon{background:rgba(148,163,184,.12);color:#cbd5e1}.history-copy{min-width:0;display:flex;flex-direction:column;gap:3px}.history-title{display:flex;gap:9px;align-items:baseline;min-width:0}.history-title strong{color:var(--text,#f4f4f5);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.history-title span{color:var(--muted,#9ca3af);font-size:11px}.history-row time{color:var(--muted,#9ca3af);font-size:11px;white-space:nowrap}@media(max-width:720px){.activity-page{gap:22px}.operation-card{flex-direction:column}.cancel-button{align-self:flex-start}.history-row{grid-template-columns:34px minmax(0,1fr)}.history-row time{grid-column:2}.operation-title-row{flex-direction:column;gap:8px}}
</style>
