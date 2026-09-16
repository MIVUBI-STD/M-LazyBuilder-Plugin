<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { LauncherOperationSnapshot, StartupReport } from '../app/bridge/runtimeApi';

  let operations: LauncherOperationSnapshot[] = [];
  let startup: StartupReport | null = null;
  let loading = true;
  let error = '';
  let cancellingId = '';
  let historyVisibleLimit = 40;
  let refreshInFlight = false;

  const ACTIVE_STATES = new Set(['QUEUED', 'RUNNING', 'CANCELLING']);
  const ACTIVE_POLL_MS = 2000;
  const IDLE_POLL_MS = 10000;
  const HISTORY_PAGE_SIZE = 40;

  function isActive(operation: LauncherOperationSnapshot) {
    return ACTIVE_STATES.has(operation.state);
  }

  function warningSteps() {
    return startup?.steps.filter((step) => step.state === 'WARNING') ?? [];
  }

  function labelKind(kind: string) {
    const labels: Record<string, string> = {
      'create-server': 'Create server',
      'adopt-server': 'Add existing server',
      'duplicate-server': 'Duplicate server',
      'backup-server': 'Backup server',
      'restore-server': 'Restore server',
      'repair-server': 'Repair server',
      'export-support-bundle': 'Create support package',
      'launcher-update': 'Update LazyBuilder',
      'download-runtime': 'Prepare server software'
    };
    return labels[kind] ?? 'Launcher task';
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
    if (refreshInFlight) return;
    refreshInFlight = true;
    if (showLoading) loading = true;
    try {
      operations = await runtimeProduct.operations.list();
      error = '';
    } catch (value) {
      error = value instanceof Error ? value.message : 'Could not load activity.';
    } finally {
      refreshInFlight = false;
      if (showLoading) loading = false;
    }
  }

  async function loadStartup() {
    try { startup = await runtimeProduct.startup.status(); }
    catch { startup = null; }
  }

  async function cancel(operation: LauncherOperationSnapshot) {
    if (!operation.canCancel || cancellingId) return;
    cancellingId = operation.id;
    error = '';
    try {
      await runtimeProduct.operations.cancel(operation.id);
      await refresh();
    } catch (value) {
      error = value instanceof Error ? value.message : 'Could not cancel this task.';
    } finally {
      cancellingId = '';
    }
  }

  onMount(() => {
    let disposed = false;
    let timer: number | null = null;

    const schedule = () => {
      if (disposed || document.hidden) return;
      const hasActive = operations.some(isActive);
      timer = window.setTimeout(async () => {
        timer = null;
        if (disposed || document.hidden) return;
        await refresh();
        schedule();
      }, hasActive ? ACTIVE_POLL_MS : IDLE_POLL_MS);
    };

    const refreshNow = () => {
      if (disposed || document.hidden) return;
      if (timer !== null) { window.clearTimeout(timer); timer = null; }
      void refresh().finally(schedule);
    };

    const handleVisibility = () => {
      if (document.hidden) {
        if (timer !== null) { window.clearTimeout(timer); timer = null; }
        return;
      }
      refreshNow();
    };

    void Promise.all([refresh(true), loadStartup()]).finally(schedule);
    document.addEventListener('visibilitychange', handleVisibility);
    window.addEventListener('focus', refreshNow);

    return () => {
      disposed = true;
      if (timer !== null) window.clearTimeout(timer);
      document.removeEventListener('visibilitychange', handleVisibility);
      window.removeEventListener('focus', refreshNow);
    };
  });

  $: activeOperations = operations.filter(isActive);
  $: history = operations.filter((operation) => !isActive(operation));
  $: visibleHistory = history.slice(0, historyVisibleLimit);
</script>

<section class="activity-page" aria-label="LazyBuilder activity">
  {#if error}<div class="activity-error" role="alert">{error}</div>{/if}

  {#if startup?.degraded && warningSteps().length > 0}
    <section class="startup-recovery" aria-labelledby="startup-recovery-heading">
      <div class="startup-heading">
        <div><h2 id="startup-recovery-heading">Startup recovery</h2><p>LazyBuilder found issues during startup and completed recovery checks before normal use.</p></div>
        <span>{warningSteps().length} notice{warningSteps().length === 1 ? '' : 's'}</span>
      </div>
      <div class="startup-step-list">
        {#each warningSteps() as step (step.key)}
          <article><strong>{step.summary}</strong><p>{step.details}</p></article>
        {/each}
      </div>
    </section>
  {/if}

  {#if loading}
    <div class="activity-empty" aria-live="polite"><strong>Loading activity…</strong></div>
  {:else if operations.length === 0}
    <div class="activity-empty">
      <div class="empty-glyph" aria-hidden="true">✓</div>
      <strong>No activity yet</strong>
      <span>Server and LazyBuilder tasks will appear here while they run.</span>
    </div>
  {:else}
    <section class="activity-section" aria-labelledby="active-activity-heading">
      <div class="section-heading">
        <div><h2 id="active-activity-heading">In progress</h2><p>Tasks continue even if you leave this page.</p></div>
        <span class="section-count">{activeOperations.length}</span>
      </div>

      {#if activeOperations.length === 0}
        <div class="quiet-state">No tasks are currently running.</div>
      {:else}
        <div class="operation-list" aria-live="polite">
          {#each activeOperations as operation (operation.id)}
            <article class="operation-card active-operation">
              <div class="operation-main">
                <div class="operation-title-row">
                  <div><strong>{labelKind(operation.kind)}</strong><span class="operation-phase">{operation.status || 'Working…'}</span></div>
                  <span class:warning-state={operation.state === 'CANCELLING'} class="state-badge">{stateLabel(operation.state)}</span>
                </div>
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
      <div class="section-heading"><div><h2 id="recent-activity-heading">Recent</h2><p>Latest completed, cancelled, and interrupted tasks.</p></div><span class="section-count">{history.length}</span></div>
      {#if history.length === 0}
        <div class="quiet-state">Completed tasks will appear here.</div>
      {:else}
        <div class="history-list">
          {#each visibleHistory as operation (operation.id)}
            <article class="history-row">
              <div class:failed-icon={operation.state === 'FAILED' || operation.state === 'RECOVERY_REQUIRED'} class:cancelled-icon={operation.state === 'CANCELLED'} class="history-icon" aria-hidden="true">
                {operation.state === 'SUCCEEDED' ? '✓' : operation.state === 'CANCELLED' ? '–' : '!'}
              </div>
              <div class="history-copy">
                <div class="history-title"><strong>{labelKind(operation.kind)}</strong><span>{stateLabel(operation.state)}</span></div>
                <span class="history-status">{operation.error?.message || operation.status || 'No additional details.'}</span>
                {#if operation.state === 'RECOVERY_REQUIRED'}<span class="recovery-note">Open the affected server before retrying or changing its files.</span>{/if}
              </div>
              <time datetime={operation.completedAtUnixSeconds ? new Date(operation.completedAtUnixSeconds * 1000).toISOString() : undefined}>{formatTime(operation.completedAtUnixSeconds ?? operation.updatedAtUnixSeconds)}</time>
            </article>
          {/each}
        </div>
        {#if visibleHistory.length < history.length}
          <button class="history-more" onclick={() => (historyVisibleLimit += HISTORY_PAGE_SIZE)}>Show more activity</button>
        {/if}
      {/if}
    </section>
  {/if}
</section>

<style>
  .activity-page{display:flex;flex-direction:column;gap:28px;max-width:980px;margin:0 auto;padding-bottom:40px}.activity-error{border:1px solid rgba(239,68,68,.38);background:rgba(127,29,29,.18);color:#fecaca;border-radius:10px;padding:12px 14px}.startup-recovery{display:grid;gap:12px;padding:15px;border:1px solid rgba(245,158,11,.36);border-radius:12px;background:rgba(120,83,18,.12)}.startup-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.startup-heading h2{margin:0;font-size:15px;color:var(--text,#f4f4f5)}.startup-heading p{margin:4px 0 0;color:var(--muted,#9ca3af);font-size:12px}.startup-heading>span{padding:4px 8px;border-radius:999px;background:rgba(245,158,11,.13);color:#fde68a;font-size:10px;white-space:nowrap}.startup-step-list{display:grid;gap:7px}.startup-step-list article{padding:10px 11px;border:1px solid rgba(245,158,11,.2);border-radius:8px;background:rgba(0,0,0,.1)}.startup-step-list strong{font-size:11px;color:#fde68a}.startup-step-list p{margin:3px 0 0;color:var(--muted,#9ca3af);font-size:11px;line-height:1.45}.activity-empty{min-height:280px;border:1px dashed var(--border,#30343b);border-radius:14px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;color:var(--muted,#9ca3af);text-align:center}.activity-empty strong{color:var(--text,#f4f4f5);font-size:16px}.empty-glyph{width:42px;height:42px;border-radius:50%;display:grid;place-items:center;background:rgba(34,197,94,.12);color:#86efac;font-size:20px;margin-bottom:4px}.activity-section{display:flex;flex-direction:column;gap:12px}.section-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:16px}.section-heading h2{margin:0;color:var(--text,#f4f4f5);font-size:16px}.section-heading p{margin:4px 0 0;color:var(--muted,#9ca3af);font-size:13px}.section-count{min-width:28px;height:24px;padding:0 8px;border:1px solid var(--border,#30343b);border-radius:999px;display:grid;place-items:center;color:var(--muted,#9ca3af);font-size:12px}.quiet-state{border:1px solid var(--border,#30343b);border-radius:12px;padding:18px;color:var(--muted,#9ca3af);font-size:13px}.operation-list,.history-list{display:flex;flex-direction:column;gap:10px}.operation-card{display:flex;gap:18px;align-items:flex-start;border:1px solid var(--border,#30343b);background:var(--panel,#17191d);border-radius:12px;padding:16px}.active-operation{border-color:rgba(99,102,241,.42)}.operation-main{flex:1;min-width:0}.operation-title-row{display:flex;justify-content:space-between;gap:16px;align-items:flex-start}.operation-title-row>div{display:flex;flex-direction:column;gap:4px;min-width:0}.operation-title-row strong{color:var(--text,#f4f4f5)}.operation-phase,.progress-meta,.history-status,.recovery-note{color:var(--muted,#9ca3af);font-size:12px}.state-badge{white-space:nowrap;border-radius:999px;padding:4px 8px;background:rgba(99,102,241,.14);color:#c7d2fe;font-size:11px;font-weight:650}.warning-state{background:rgba(245,158,11,.13);color:#fde68a}.progress-block{margin-top:14px}.progress-track{height:6px;border-radius:999px;background:rgba(148,163,184,.15);overflow:hidden}.progress-track span{display:block;height:100%;border-radius:inherit;background:var(--accent,#7c83ff);transition:width .2s ease}.progress-meta{display:flex;justify-content:space-between;gap:12px;margin-top:6px}.progress-meta strong{color:var(--text,#f4f4f5);font-size:12px}.standalone-progress{margin-top:12px}.operation-warning,.recovery-note{display:block;margin-top:10px;color:#fde68a}.cancel-button{flex:0 0 auto;border:1px solid var(--border,#3f444d);background:transparent;color:var(--text,#f4f4f5);border-radius:8px;padding:7px 11px;cursor:pointer}.cancel-button:hover:not(:disabled){background:rgba(255,255,255,.05)}.cancel-button:disabled{opacity:.5;cursor:not-allowed}.history-row{display:grid;grid-template-columns:34px minmax(0,1fr) auto;gap:12px;align-items:center;border-bottom:1px solid var(--border,#2b2f36);padding:12px 4px}.history-row:last-child{border-bottom:0}.history-icon{width:28px;height:28px;border-radius:50%;display:grid;place-items:center;background:rgba(34,197,94,.12);color:#86efac;font-weight:700}.failed-icon{background:rgba(239,68,68,.12);color:#fca5a5}.cancelled-icon{background:rgba(148,163,184,.12);color:#cbd5e1}.history-copy{min-width:0;display:flex;flex-direction:column;gap:3px}.history-title{display:flex;gap:9px;align-items:baseline;min-width:0}.history-title strong{color:var(--text,#f4f4f5);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.history-title span{color:var(--muted,#9ca3af);font-size:11px}.history-row time{color:var(--muted,#9ca3af);font-size:11px;white-space:nowrap}.history-more{align-self:flex-start;border:1px solid var(--border,#3f444d);background:transparent;color:var(--text,#f4f4f5);border-radius:8px;padding:7px 11px;cursor:pointer}.history-more:hover{background:rgba(255,255,255,.05)}@media(max-width:720px){.activity-page{gap:22px}.startup-heading{flex-direction:column}.operation-card{flex-direction:column}.cancel-button{align-self:flex-start}.history-row{grid-template-columns:34px minmax(0,1fr)}.history-row time{grid-column:2}.operation-title-row{flex-direction:column;gap:8px}}
</style>
