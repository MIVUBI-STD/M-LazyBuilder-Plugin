<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { LauncherOperationSnapshot, LauncherOperationState } from '../app/bridge/runtimeApi';

  type Notice = { id: string; title: string; detail: string; state: LauncherOperationState; reference: string };

  const ACTIVE = new Set<LauncherOperationState>(['QUEUED', 'RUNNING', 'CANCELLING']);
  const TERMINAL = new Set<LauncherOperationState>(['SUCCEEDED', 'FAILED', 'CANCELLED', 'RECOVERY_REQUIRED']);
  const ACTIVE_POLL_MS = 2500;
  const IDLE_POLL_MS = 15000;
  const HIDDEN_POLL_MS = 10000;
  const MAX_NOTICES = 3;
  const NOTICE_LIFETIME_MS = 8000;

  let notices: Notice[] = [];
  let knownStates = new Map<string, LauncherOperationState>();
  let seeded = false;
  let disposed = false;
  let timer: number | null = null;
  let refreshInFlight = false;

  function labelKind(kind: string) {
    return kind.split('-').map((part) => part ? part[0].toUpperCase() + part.slice(1) : part).join(' ');
  }

  function terminalDetail(operation: LauncherOperationSnapshot) {
    if (operation.state === 'RECOVERY_REQUIRED') return operation.error?.message || 'Recovery is required before retrying this operation.';
    if (operation.state === 'FAILED') return operation.error?.message || operation.status || 'The operation failed.';
    if (operation.state === 'CANCELLED') return operation.status || 'The operation was cancelled.';
    return operation.status || 'The operation completed.';
  }

  function dismiss(id: string) {
    notices = notices.filter((notice) => notice.id !== id);
  }

  function pushNotice(operation: LauncherOperationSnapshot) {
    const notice: Notice = {
      id: operation.id,
      title: labelKind(operation.kind),
      detail: terminalDetail(operation),
      state: operation.state,
      reference: operation.state === 'FAILED' || operation.state === 'RECOVERY_REQUIRED' ? operation.correlationId ?? '' : ''
    };
    notices = [notice, ...notices.filter((item) => item.id !== notice.id)].slice(0, MAX_NOTICES);
    window.setTimeout(() => dismiss(notice.id), NOTICE_LIFETIME_MS);
  }

  async function refresh() {
    if (disposed || refreshInFlight) return [] as LauncherOperationSnapshot[];
    if (document.querySelector('.activity-page')) {
      // Activity already presents the canonical snapshots. Reset the presentation
      // baseline so leaving Activity never produces stale duplicate completion toasts.
      knownStates.clear();
      seeded = false;
      return [] as LauncherOperationSnapshot[];
    }

    refreshInFlight = true;
    try {
      const operations = await runtimeProduct.operations.list();
      if (!seeded) {
        knownStates = new Map(operations.map((operation) => [operation.id, operation.state]));
        seeded = true;
        return operations;
      }

      const nextStates = new Map<string, LauncherOperationState>();
      for (const operation of operations) {
        nextStates.set(operation.id, operation.state);
        const previous = knownStates.get(operation.id);
        if (previous && previous !== operation.state && !TERMINAL.has(previous) && TERMINAL.has(operation.state)) {
          pushNotice(operation);
        }
      }
      knownStates = nextStates;
      return operations;
    } catch {
      return [] as LauncherOperationSnapshot[];
    } finally {
      refreshInFlight = false;
    }
  }

  function schedule(operations: LauncherOperationSnapshot[] = []) {
    if (disposed) return;
    if (timer !== null) window.clearTimeout(timer);
    if (document.querySelector('.activity-page')) {
      timer = window.setTimeout(async () => schedule(await refresh()), IDLE_POLL_MS);
      return;
    }
    const delay = document.hidden
      ? HIDDEN_POLL_MS
      : operations.some((operation) => ACTIVE.has(operation.state)) ? ACTIVE_POLL_MS : IDLE_POLL_MS;
    timer = window.setTimeout(async () => schedule(await refresh()), delay);
  }

  onMount(() => {
    void refresh().then(schedule);
    const onFocus = () => {
      if (timer !== null) { window.clearTimeout(timer); timer = null; }
      void refresh().then(schedule);
    };
    window.addEventListener('focus', onFocus);
    document.addEventListener('visibilitychange', onFocus);
    return () => {
      disposed = true;
      if (timer !== null) window.clearTimeout(timer);
      window.removeEventListener('focus', onFocus);
      document.removeEventListener('visibilitychange', onFocus);
    };
  });
</script>

<div class="operation-notifications" aria-label="Launcher notifications">
  {#each notices as notice (notice.id)}
    <article class:danger={notice.state === 'FAILED' || notice.state === 'RECOVERY_REQUIRED'} class="operation-notice" role="status" aria-live="polite">
      <div>
        <strong>{notice.title}</strong>
        <span>{notice.detail}</span>
        {#if notice.reference}<small>Reference: {notice.reference}</small>{/if}
      </div>
      <button aria-label={`Dismiss ${notice.title} notification`} onclick={() => dismiss(notice.id)}>×</button>
    </article>
  {/each}
</div>

<style>
  .operation-notifications{position:fixed;z-index:180;right:18px;bottom:18px;width:min(360px,calc(100vw - 36px));display:grid;gap:8px;pointer-events:none}
  .operation-notice{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:12px;align-items:start;padding:11px 12px;border:1px solid var(--accent-border);border-radius:9px;background:var(--surface);box-shadow:var(--shadow-popover);pointer-events:auto}.operation-notice.danger{border-color:#713940}.operation-notice>div{display:grid;gap:3px}.operation-notice strong{font-size:11px}.operation-notice span{color:var(--muted);font-size:9px;line-height:1.45}.operation-notice small{color:var(--muted-2);font-size:8px;overflow-wrap:anywhere}.operation-notice button{width:28px;height:28px;border-radius:7px;background:transparent;color:var(--muted);font-size:18px;cursor:pointer}.operation-notice button:hover{background:var(--surface-2);color:var(--text)}
  @media(max-width:760px){.operation-notifications{right:12px;bottom:12px;width:calc(100vw - 24px)}}
</style>
