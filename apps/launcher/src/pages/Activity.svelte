<script lang="ts">
  import { onMount } from 'svelte';
  import StartupRecoveryCenter from '../components/StartupRecoveryCenter.svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import { operationTitle } from '../app/operations/operationPresentation';
  import { refreshActivityFeed, subscribeActivityFeed } from '../app/operations/activityFeed';
  import type { LauncherOperationSnapshot, StartupReport, WorldTaskSnapshot } from '../app/bridge/runtimeApi';

  let operations: LauncherOperationSnapshot[] = [];
  let worldTasks: WorldTaskSnapshot[] = [];
  let activityWarnings: string[] = [];
  let startup: StartupReport | null = null;
  let loading = true;
  let error = '';
  let cancellingId = '';
  let historyVisibleLimit = 40;
  let activityUnsubscribe: (() => void) | null = null;

  const ACTIVE_STATES = new Set(['QUEUED', 'RUNNING', 'CANCELLING']);
  const HISTORY_PAGE_SIZE = 40;

  function isActive(operation: LauncherOperationSnapshot) {
    return ACTIVE_STATES.has(operation.state);
  }

  function worldTaskActive(task: WorldTaskSnapshot) {
    return task.state === 'QUEUED' || task.state === 'RUNNING';
  }

  function worldTaskTitle(task: WorldTaskSnapshot) {
    const label = task.taskType.toLowerCase().replace(/_/g, ' ');
    return label.replace(/\b\w/g, (letter) => letter.toUpperCase());
  }

  function worldTaskStateLabel(state: WorldTaskSnapshot['state']) {
    return { QUEUED: 'Queued', RUNNING: 'Running', SUCCEEDED: 'Completed', FAILED: 'Failed' }[state];
  }

  function formatIsoTime(value: string) {
    const parsed = Date.parse(value);
    if (!Number.isFinite(parsed)) return '';
    return new Date(parsed).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
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

  function hasTechnicalDetails(operation: LauncherOperationSnapshot) {
    return Boolean(operation.phase || operation.details || operation.warnings.length || operation.error?.details || operation.correlationId);
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
      await refreshActivityFeed();
    } catch (value) {
      error = value instanceof Error ? value.message : 'Could not cancel this task.';
    } finally {
      cancellingId = '';
    }
  }

  onMount(() => {
    activityUnsubscribe = subscribeActivityFeed((next) => {
      loading = next.loading;
      error = next.error;
      operations = next.snapshot?.launcherOperations ?? [];
      worldTasks = next.snapshot?.worldTasks ?? [];
      activityWarnings = next.snapshot?.warnings ?? [];
    });
    void loadStartup();

    return () => {
      activityUnsubscribe?.();
      activityUnsubscribe = null;
    };
  });

  $: activeOperations = operations.filter(isActive);
  $: activeWorldTasks = worldTasks.filter(worldTaskActive);
  $: worldTaskHistory = worldTasks.filter((task) => !worldTaskActive(task));
  $: history = operations.filter((operation) => !isActive(operation));
  $: visibleHistory = history.slice(0, historyVisibleLimit);
</script>

<section class="activity-page" aria-label="LazyBuilder activity">
  {#if error}<div class="activity-error" role="alert">{error}</div>{/if}
  {#each activityWarnings as warning}<div class="activity-warning" role="status">{warning}</div>{/each}

  <StartupRecoveryCenter report={startup} />

  {#if loading}
    <div class="activity-empty" aria-live="polite"><strong>Loading activity…</strong></div>
  {:else if operations.length === 0 && worldTasks.length === 0}
    <div class="activity-empty">
      <div class="empty-glyph" aria-hidden="true">✓</div>
      <strong>No activity yet</strong>
      <span>Server and LazyBuilder tasks will appear here while they run.</span>
    </div>
  {:else}
    <section class="activity-section" aria-labelledby="active-activity-heading">
      <div class="section-heading">
        <div><h2 id="active-activity-heading">In progress</h2><p>Tasks continue even if you leave this page.</p></div>
        <span class="section-count">{activeOperations.length + activeWorldTasks.length}</span>
      </div>

      {#if activeOperations.length === 0 && activeWorldTasks.length === 0}
        <div class="quiet-state">No tasks are currently running.</div>
      {:else}
        <div class="operation-list" aria-live="off">
          {#each activeOperations as operation (operation.id)}
            <article class="operation-card active-operation">
              <div class="operation-main">
                <div class="operation-title-row">
                  <div><strong>{operationTitle(operation.kind)}</strong><span class="operation-phase">{operation.status || 'Working…'}</span></div>
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
                {#if operation.correlationId}<small class="operation-reference">Reference: {operation.correlationId}</small>{/if}
              </div>
              {#if operation.canCancel}
                <button class="cancel-button" disabled={cancellingId === operation.id || operation.state === 'CANCELLING'} onclick={() => cancel(operation)}>
                  {cancellingId === operation.id || operation.state === 'CANCELLING' ? 'Cancelling…' : 'Cancel'}
                </button>
              {/if}
            </article>
          {/each}
          {#each activeWorldTasks as task (task.taskId)}
            <article class="operation-card active-operation">
              <div class="operation-main">
                <div class="operation-title-row">
                  <div><strong>{worldTaskTitle(task)}</strong><span class="operation-phase">{task.message || 'Working…'}</span></div>
                  <span class="state-badge">{worldTaskStateLabel(task.state)}</span>
                </div>
                <div class="progress-block" aria-label={`${task.progressPercent} percent complete`}>
                  <div class="progress-track"><span style={`width:${task.progressPercent}%`}></span></div>
                  <div class="progress-meta"><span>World Manager</span><strong>{task.progressPercent}%</strong></div>
                </div>
                <small class="operation-reference">World task: {task.taskId}</small>
              </div>
            </article>
          {/each}
        </div>
      {/if}
    </section>

    <section class="activity-section" aria-labelledby="recent-activity-heading">
      <div class="section-heading"><div><h2 id="recent-activity-heading">Recent</h2><p>Latest completed, cancelled, and interrupted tasks.</p></div><span class="section-count">{history.length + worldTaskHistory.length}</span></div>
      {#if history.length === 0 && worldTaskHistory.length === 0}
        <div class="quiet-state">Completed tasks will appear here.</div>
      {:else}
        <div class="history-list">
          {#each visibleHistory as operation (operation.id)}
            <article class="history-row">
              <div class:failed-icon={operation.state === 'FAILED' || operation.state === 'RECOVERY_REQUIRED'} class:cancelled-icon={operation.state === 'CANCELLED'} class="history-icon" aria-hidden="true">
                {operation.state === 'SUCCEEDED' ? '✓' : operation.state === 'CANCELLED' ? '–' : '!'}
              </div>
              <div class="history-copy">
                <div class="history-title"><strong>{operationTitle(operation.kind)}</strong><span>{stateLabel(operation.state)}</span></div>
                <span class="history-status">{operation.error?.message || operation.status || 'No additional details.'}</span>
                {#if operation.state === 'RECOVERY_REQUIRED'}<span class="recovery-note">Open the affected server before retrying or changing its files.</span>{/if}
                {#if hasTechnicalDetails(operation)}
                  <details class="operation-technical">
                    <summary>Task details</summary>
                    <div class="technical-grid">
                      {#if operation.phase}<div><span>Phase</span><strong>{operation.phase}</strong></div>{/if}
                      {#if operation.details}<div><span>Details</span><strong>{operation.details}</strong></div>{/if}
                      {#if operation.error?.details}<div><span>Error details</span><strong>{operation.error.details}</strong></div>{/if}
                      {#if operation.warnings.length}<div><span>Warnings</span><strong>{operation.warnings.join(' ')}</strong></div>{/if}
                      {#if operation.correlationId}<div><span>Reference</span><code>{operation.correlationId}</code></div>{/if}
                    </div>
                  </details>
                {/if}
              </div>
              <time datetime={operation.completedAtUnixSeconds ? new Date(operation.completedAtUnixSeconds * 1000).toISOString() : undefined}>{formatTime(operation.completedAtUnixSeconds ?? operation.updatedAtUnixSeconds)}</time>
            </article>
          {/each}
          {#each worldTaskHistory as task (task.taskId)}
            <article class="history-row">
              <div class:failed-icon={task.state === 'FAILED'} class="history-icon" aria-hidden="true">{task.state === 'SUCCEEDED' ? '✓' : '!'}</div>
              <div class="history-copy">
                <div class="history-title"><strong>{worldTaskTitle(task)}</strong><span>{worldTaskStateLabel(task.state)}</span></div>
                <span class="history-status">{task.error || task.message || 'No additional details.'}</span>
                <small class="operation-reference">World task: {task.taskId}</small>
              </div>
              <time datetime={task.updatedAt || undefined}>{formatIsoTime(task.updatedAt)}</time>
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
  .activity-page{display:flex;flex-direction:column;gap:28px;max-width:980px;margin:0 auto;padding-bottom:40px}.activity-error{border:1px solid rgba(239,68,68,.38);background:rgba(127,29,29,.18);color:#fecaca;border-radius:10px;padding:12px 14px}.activity-warning{border:1px solid rgba(245,158,11,.28);background:rgba(120,53,15,.12);color:#fde68a;border-radius:10px;padding:10px 14px;font-size:12px}.activity-empty{min-height:280px;border:1px dashed var(--border,#30343b);border-radius:14px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;color:var(--muted,#9ca3af);text-align:center}.activity-empty strong{color:var(--text,#f4f4f5);font-size:16px}.empty-glyph{width:42px;height:42px;border-radius:50%;display:grid;place-items:center;background:rgba(34,197,94,.12);color:#86efac;font-size:20px;margin-bottom:4px}.activity-section{display:flex;flex-direction:column;gap:12px}.section-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:16px}.section-heading h2{margin:0;color:var(--text,#f4f4f5);font-size:16px}.section-heading p{margin:4px 0 0;color:var(--muted,#9ca3af);font-size:13px}.section-count{min-width:28px;height:24px;padding:0 8px;border:1px solid var(--border,#30343b);border-radius:999px;display:grid;place-items:center;color:var(--muted,#9ca3af);font-size:12px}.quiet-state{border:1px solid var(--border,#30343b);border-radius:12px;padding:18px;color:var(--muted,#9ca3af);font-size:13px}.operation-list,.history-list{display:flex;flex-direction:column;gap:10px}.operation-card{display:flex;gap:18px;align-items:flex-start;border:1px solid var(--border,#30343b);background:var(--panel,#17191d);border-radius:12px;padding:16px}.active-operation{border-color:rgba(99,102,241,.42)}.operation-main{flex:1;min-width:0}.operation-title-row{display:flex;justify-content:space-between;gap:16px;align-items:flex-start}.operation-title-row>div{display:flex;flex-direction:column;gap:4px;min-width:0}.operation-title-row strong{color:var(--text,#f4f4f5)}.operation-phase,.progress-meta,.history-status,.recovery-note{color:var(--muted,#9ca3af);font-size:12px}.operation-details{margin:9px 0 0;color:var(--muted,#9ca3af);font-size:11px;line-height:1.45}.state-badge{white-space:nowrap;border-radius:999px;padding:4px 8px;background:rgba(99,102,241,.14);color:#c7d2fe;font-size:11px;font-weight:650}.warning-state{background:rgba(245,158,11,.13);color:#fde68a}.progress-block{margin-top:14px}.progress-track{height:6px;border-radius:999px;background:rgba(148,163,184,.15);overflow:hidden}.progress-track span{display:block;height:100%;border-radius:inherit;background:var(--accent,#7c83ff);transition:width .2s ease}.progress-meta{display:flex;justify-content:space-between;gap:12px;margin-top:6px}.progress-meta strong{color:var(--text,#f4f4f5);font-size:12px}.standalone-progress{margin-top:12px}.operation-warning,.recovery-note{display:block;margin-top:10px;color:#fde68a}.operation-reference{display:block;margin-top:8px;color:var(--muted-2);font:8px ui-monospace,SFMono-Regular,Consolas,monospace}.cancel-button{flex:0 0 auto;border:1px solid var(--border,#3f444d);background:transparent;color:var(--text,#f4f4f5);border-radius:8px;padding:7px 11px;cursor:pointer}.cancel-button:hover:not(:disabled){background:rgba(255,255,255,.05)}.cancel-button:disabled{opacity:.5;cursor:not-allowed}.history-row{display:grid;grid-template-columns:34px minmax(0,1fr) auto;gap:12px;align-items:start;border-bottom:1px solid var(--border,#2b2f36);padding:12px 4px}.history-row:last-child{border-bottom:0}.history-icon{width:28px;height:28px;border-radius:50%;display:grid;place-items:center;background:rgba(34,197,94,.12);color:#86efac;font-weight:700}.failed-icon{background:rgba(239,68,68,.12);color:#fca5a5}.cancelled-icon{background:rgba(148,163,184,.12);color:#cbd5e1}.history-copy{min-width:0;display:flex;flex-direction:column;gap:3px}.history-title{display:flex;gap:9px;align-items:baseline;min-width:0}.history-title strong{color:var(--text,#f4f4f5);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.history-title span{color:var(--muted,#9ca3af);font-size:11px}.history-row time{padding-top:2px;color:var(--muted,#9ca3af);font-size:11px;white-space:nowrap}.operation-technical{margin-top:5px}.operation-technical summary{width:max-content;color:var(--muted-2);font-size:9px;cursor:pointer}.technical-grid{display:grid;gap:5px;margin-top:7px;padding:8px;border:1px solid var(--border-soft);border-radius:7px;background:var(--bg-elevated)}.technical-grid>div{display:grid;grid-template-columns:90px minmax(0,1fr);gap:8px}.technical-grid span{color:var(--muted-2);font-size:8px;text-transform:uppercase}.technical-grid strong,.technical-grid code{overflow-wrap:anywhere;color:var(--text-soft);font-size:9px;font-weight:500}.technical-grid code{font-family:ui-monospace,SFMono-Regular,Consolas,monospace}.history-more{align-self:flex-start;border:1px solid var(--border,#3f444d);background:transparent;color:var(--text,#f4f4f5);border-radius:8px;padding:7px 11px;cursor:pointer}.history-more:hover{background:rgba(255,255,255,.05)}@media(max-width:720px){.activity-page{gap:22px}.operation-card{flex-direction:column}.cancel-button{align-self:flex-start}.history-row{grid-template-columns:34px minmax(0,1fr)}.history-row time{grid-column:2}.operation-title-row{flex-direction:column;gap:8px}.technical-grid>div{grid-template-columns:1fr;gap:2px}}
</style>
