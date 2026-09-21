<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import { subscribeActivityFeed } from '../app/operations/activityFeed';
  import type { LauncherOperationSnapshot, StartupReport, WorldTaskSnapshot } from '../app/bridge/runtimeApi';

  const ACTIVE_STATES = new Set(['QUEUED', 'RUNNING', 'CANCELLING']);

  let operations = $state<LauncherOperationSnapshot[]>([]);
  let startup = $state<StartupReport | null>(null);
  let worldTasks = $state<WorldTaskSnapshot[]>([]);
  let activityUnsubscribe: (() => void) | null = null;

  let activeCount = $derived(operations.filter((operation) => ACTIVE_STATES.has(operation.state)).length + worldTasks.filter((task) => task.state === 'QUEUED' || task.state === 'RUNNING').length);
  let recoveryCount = $derived(operations.filter((operation) => operation.state === 'RECOVERY_REQUIRED').length);
  let startupWarningCount = $derived(startup?.steps.filter((step) => step.state === 'WARNING').length ?? 0);
  let attentionCount = $derived(recoveryCount + startupWarningCount);
  let label = $derived(
    activeCount > 0
      ? `${activeCount} task${activeCount === 1 ? '' : 's'} in progress`
      : attentionCount > 0
        ? `${attentionCount} item${attentionCount === 1 ? '' : 's'} need attention`
        : 'No tasks in progress'
  );

  async function loadStartup() {
    try { startup = await runtimeProduct.startup.status(); }
    catch { startup = null; }
  }

  onMount(() => {
    activityUnsubscribe = subscribeActivityFeed((next) => {
      operations = next.snapshot?.launcherOperations ?? [];
      worldTasks = next.snapshot?.worldTasks ?? [];
    });
    void loadStartup();

    return () => {
      activityUnsubscribe?.();
      activityUnsubscribe = null;
    };
  });
</script>

<span class="activity-status" class:active={activeCount > 0} class:attention={activeCount === 0 && attentionCount > 0} title={label} aria-label={label}>
  {#if activeCount > 0}
    <span class="pulse" aria-hidden="true"></span><strong>{activeCount}</strong>
  {:else if attentionCount > 0}
    <span class="attention-mark" aria-hidden="true">!</span>
  {/if}
</span>

<style>
  .activity-status{margin-left:auto;min-width:20px;height:20px;display:flex;align-items:center;justify-content:center;gap:5px;border-radius:999px;color:var(--muted-2);font-size:9px}.activity-status:empty{display:none}.activity-status.active{padding:0 7px;background:var(--accent-soft);color:#9ee8b9}.activity-status.attention{width:20px;background:var(--warning-bg);color:#fde68a}.activity-status strong{font-size:9px}.pulse{width:6px;height:6px;border-radius:50%;background:var(--accent);box-shadow:0 0 0 0 rgba(72,187,120,.35);animation:pulse 1.8s infinite}.attention-mark{font-weight:800}@keyframes pulse{0%{box-shadow:0 0 0 0 rgba(72,187,120,.32)}70%{box-shadow:0 0 0 5px rgba(72,187,120,0)}100%{box-shadow:0 0 0 0 rgba(72,187,120,0)}}@media(prefers-reduced-motion:reduce){.pulse{animation:none}}
</style>
