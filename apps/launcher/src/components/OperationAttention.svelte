<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import { operationTitle } from '../app/operations/operationPresentation';
  import type { LauncherOperationSnapshot, LauncherOperationState } from '../app/bridge/runtimeApi';

  export let onOpenActivity: (() => void) | undefined = undefined;

  const ACTIVE_STATES = new Set<LauncherOperationState>(['QUEUED', 'RUNNING', 'CANCELLING']);
  const TERMINAL_STATES = new Set<LauncherOperationState>(['SUCCEEDED', 'FAILED', 'CANCELLED', 'RECOVERY_REQUIRED']);
  const ACTIVE_POLL_MS = 3000;
  const IDLE_POLL_MS = 15000;
  const SUCCESS_DISMISS_MS = 7000;

  let previous = new Map<string, LauncherOperationState>();
  let initialized = false;
  let initializedAtUnixSeconds = 0;
  let notice: LauncherOperationSnapshot | null = null;
  let dismissTimer: number | null = null;
  let refreshInFlight = false;

  function clearDismissTimer() {
    if (dismissTimer !== null) window.clearTimeout(dismissTimer);
    dismissTimer = null;
  }

  function dismiss() {
    clearDismissTimer();
    notice = null;
  }

  function show(operation: LauncherOperationSnapshot) {
    clearDismissTimer();
    notice = operation;
    if (operation.state === 'SUCCEEDED') {
      dismissTimer = window.setTimeout(dismiss, SUCCESS_DISMISS_MS);
    }
  }

  function tone(operation: LauncherOperationSnapshot) {
    return operation.state === 'SUCCEEDED' ? 'success' : operation.state === 'CANCELLED' ? 'neutral' : 'attention';
  }

  function headline(operation: LauncherOperationSnapshot) {
    if (operation.state === 'SUCCEEDED') return `${operationTitle(operation.kind)} completed`;
    if (operation.state === 'CANCELLED') return `${operationTitle(operation.kind)} cancelled`;
    if (operation.state === 'RECOVERY_REQUIRED') return `${operationTitle(operation.kind)} needs recovery`;
    return `${operationTitle(operation.kind)} failed`;
  }

  function details(operation: LauncherOperationSnapshot) {
    return operation.error?.message || operation.status || operation.details || 'Open Activity for more information.';
  }

  function inspect(next: LauncherOperationSnapshot[]) {
    if (!initialized) {
      previous = new Map(next.map((operation) => [operation.id, operation.state]));
      initializedAtUnixSeconds = Math.floor(Date.now() / 1000);
      initialized = true;
      return;
    }

    for (const operation of next) {
      const before = previous.get(operation.id);
      const transitionedToTerminal = Boolean(before && ACTIVE_STATES.has(before) && TERMINAL_STATES.has(operation.state));
      const completedBetweenPolls = before === undefined
        && TERMINAL_STATES.has(operation.state)
        && operation.createdAtUnixSeconds >= initializedAtUnixSeconds;
      if (transitionedToTerminal || completedBetweenPolls) show(operation);
      previous.set(operation.id, operation.state);
    }
  }

  async function refresh() {
    if (refreshInFlight) return false;
    refreshInFlight = true;
    try {
      const operations = await runtimeProduct.operations.list();
      inspect(operations);
      return operations.some((operation) => ACTIVE_STATES.has(operation.state));
    } catch {
      return false;
    } finally {
      refreshInFlight = false;
    }
  }

  function openActivity() {
    dismiss();
    onOpenActivity?.();
  }

  onMount(() => {
    let disposed = false;
    let timer: number | null = null;

    const schedule = (hasActive: boolean) => {
      if (disposed || document.hidden) return;
      timer = window.setTimeout(async () => {
        timer = null;
        if (disposed || document.hidden) return;
        const active = await refresh();
        schedule(active);
      }, hasActive ? ACTIVE_POLL_MS : IDLE_POLL_MS);
    };

    const refreshNow = () => {
      if (disposed || document.hidden) return;
      if (timer !== null) window.clearTimeout(timer);
      timer = null;
      void refresh().then(schedule);
    };

    const handleVisibility = () => {
      if (document.hidden) {
        if (timer !== null) window.clearTimeout(timer);
        timer = null;
        return;
      }
      refreshNow();
    };

    void refresh().then(schedule);
    document.addEventListener('visibilitychange', handleVisibility);
    window.addEventListener('focus', refreshNow);

    return () => {
      disposed = true;
      clearDismissTimer();
      if (timer !== null) window.clearTimeout(timer);
      document.removeEventListener('visibilitychange', handleVisibility);
      window.removeEventListener('focus', refreshNow);
    };
  });
</script>

{#if notice}
  <aside class="operation-attention {tone(notice)}" aria-live={notice.state === 'SUCCEEDED' ? 'polite' : 'assertive'}>
    <div class="attention-icon" aria-hidden="true">{notice.state === 'SUCCEEDED' ? '✓' : notice.state === 'CANCELLED' ? '–' : '!'}</div>
    <div class="attention-copy">
      <strong>{headline(notice)}</strong>
      <span>{details(notice)}</span>
    </div>
    <div class="attention-actions">
      <button class="open-activity" onclick={openActivity}>Open Activity</button>
      <button class="dismiss" aria-label="Dismiss task notification" onclick={dismiss}>×</button>
    </div>
  </aside>
{/if}

<style>
  .operation-attention{position:fixed;z-index:220;right:20px;bottom:20px;width:min(430px,calc(100vw - 40px));display:grid;grid-template-columns:auto minmax(0,1fr) auto;gap:11px;align-items:start;padding:13px;border:1px solid var(--border);border-radius:11px;background:var(--surface-2);box-shadow:var(--shadow-popover)}
  .operation-attention.success{border-color:var(--accent-border)}.operation-attention.attention{border-color:#705c26}.operation-attention.neutral{border-color:var(--border)}
  .attention-icon{width:28px;height:28px;display:grid;place-items:center;border-radius:8px;background:var(--surface-3);font-weight:800}.success .attention-icon{background:var(--accent-soft);color:#9ee8b9}.attention .attention-icon{background:var(--warning-bg);color:#fde68a}
  .attention-copy{display:grid;gap:3px;min-width:0}.attention-copy strong{font-size:11px}.attention-copy span{color:var(--muted);font-size:10px;line-height:1.45;overflow-wrap:anywhere}
  .attention-actions{display:flex;align-items:center;gap:5px}.open-activity{min-height:30px;padding:6px 9px;border:1px solid var(--border);border-radius:7px;background:var(--surface-3);color:var(--text);font-size:9px;font-weight:700;cursor:pointer}.dismiss{width:30px;height:30px;border:0;border-radius:7px;background:transparent;color:var(--muted);font-size:18px;cursor:pointer}.open-activity:hover,.dismiss:hover{background:var(--surface-3)}
  @media(max-width:620px){.operation-attention{right:12px;bottom:12px;width:calc(100vw - 24px);grid-template-columns:auto minmax(0,1fr)}.attention-actions{grid-column:2;justify-content:flex-end}}
</style>
