<script lang="ts">
  import { onMount } from 'svelte';
  import RuntimeErrorNotice from '../components/RuntimeErrorNotice.svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import { presentRuntimeError } from '../app/runtimeErrorPresentation';
  import type { RuntimeErrorPresentation } from '../app/runtimeErrorPresentation';
  import type { ServerReadinessSnapshot, ServerRepairPlan, WorkspaceEntry } from '../app/bridge/runtimeApi';

  export let onRepaired: (() => Promise<void> | void) | undefined = undefined;

  let workspace: WorkspaceEntry | null = null;
  let readiness: ServerReadinessSnapshot | null = null;
  let plan: ServerRepairPlan | null = null;
  let loading = true;
  let repairing = false;
  let error: RuntimeErrorPresentation | null = null;
  let notice = '';

  async function refresh() {
    const state = await runtimeProduct.workspace.state();
    workspace = state.active ?? null;
    if (!workspace) {
      readiness = null;
      plan = null;
      return;
    }

    // The repair plan carries the exact readiness snapshot used to derive it, so one
    // backend diagnosis produces a temporally consistent plan + readiness view.
    const nextPlan = await runtimeProduct.readiness.repairPlan(workspace.id);
    plan = nextPlan;
    readiness = nextPlan.health;
  }

  async function initialLoad() {
    loading = true;
    error = null;
    try { await refresh(); }
    catch (value) { error = presentRuntimeError(value, 'Could not inspect server readiness.'); }
    finally { loading = false; }
  }

  async function repair() {
    if (!workspace || !plan?.canRepair || repairing) return;
    repairing = true;
    error = null;
    notice = '';
    try {
      const result = await runtimeProduct.readiness.repair(workspace.id);
      notice = result.health.ready
        ? 'Server components were repaired and the server setup is ready.'
        : 'Repairs completed. Your attention is still required for the remaining items.';
      await refresh();
      await onRepaired?.();
    } catch (value) {
      error = presentRuntimeError(value, 'Could not repair this server.');
      try { await refresh(); } catch {}
    } finally {
      repairing = false;
    }
  }

  onMount(() => { void initialLoad(); });

  $: failedChecks = readiness?.checks.filter((check) => !check.ready) ?? [];
</script>

<section class="health-panel" aria-labelledby="readiness-heading">
  <header class="health-heading">
    <div>
      <h3 id="readiness-heading">Server readiness</h3>
      <p>Checks that determine whether this server is complete and safe to start.</p>
    </div>
    {#if plan?.canRepair}
      <button class="repair-button" disabled={repairing} onclick={repair}>{repairing ? 'Repairing…' : `Repair ${plan.repairs.length} item${plan.repairs.length === 1 ? '' : 's'}`}</button>
    {/if}
  </header>

  <RuntimeErrorNotice {error} />
  {#if notice}<div class="health-notice success" aria-live="polite">{notice}</div>{/if}

  {#if loading}
    <div class="health-empty">Checking server readiness…</div>
  {:else if !workspace || !readiness || !plan}
    <div class="health-empty">Open a server to inspect its readiness.</div>
  {:else if readiness.ready}
    <div class="ready-state"><span aria-hidden="true">✓</span><div><strong>Server setup is ready</strong><small>Everything required to start this server is available.</small></div></div>
  {:else}
    {#if plan.blockedReason}<div class="health-notice warning"><strong>Automatic repair isn't available</strong><span>{plan.blockedReason}</span></div>{/if}

    {#if plan.repairs.length > 0}
      <div class="repair-plan">
        <strong>LazyBuilder can repair</strong>
        {#each plan.repairs as item}
          <div class="repair-row"><span class="repair-dot" aria-hidden="true"></span><div><b>{item.title}</b><small>{item.details}</small></div></div>
        {/each}
      </div>
    {/if}

    {#if plan.manualActions.length > 0}
      <div class="manual-plan">
        <strong>Requires your action</strong>
        {#each plan.manualActions as item}
          <div class="repair-row manual"><span class="manual-dot" aria-hidden="true">!</span><div><b>{item.title}</b><small>{item.details}</small></div></div>
        {/each}
      </div>
    {/if}

    {#if failedChecks.length > 0}
      <details class="technical-checks">
        <summary>Technical checks ({failedChecks.length})</summary>
        <div>{#each failedChecks as check}<p><strong>{check.key}</strong><span>{check.summary}</span></p>{/each}</div>
      </details>
    {/if}
  {/if}
</section>

<style>
  .health-panel{margin-top:16px;padding:16px;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}
  .health-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.health-heading h3{margin:0;font-size:14px}.health-heading p{margin:3px 0 0;color:var(--muted);font-size:10px}
  .repair-button{min-height:34px;padding:7px 12px;border:1px solid var(--accent-border);border-radius:8px;background:var(--accent-soft);color:#9ee8b9;font-weight:700;cursor:pointer}.repair-button:disabled{opacity:.5;cursor:default}
  .health-notice{display:grid;gap:3px;margin-top:11px;padding:10px 11px;border-radius:8px;font-size:10px}.health-notice.success{border:1px solid var(--accent-border);background:var(--accent-soft);color:#b7f0cb}.health-notice.warning{border:1px solid #5f5125;background:var(--warning-bg);color:var(--text-soft)}
  .health-empty{min-height:92px;display:flex;align-items:center;justify-content:center;color:var(--muted);font-size:10px}.ready-state{display:flex;align-items:center;gap:10px;margin-top:13px;padding:12px;border:1px solid var(--accent-border);border-radius:9px;background:var(--accent-soft)}.ready-state>span{width:28px;height:28px;display:grid;place-items:center;border-radius:50%;background:rgba(34,197,94,.16);color:#9ee8b9}.ready-state div{display:grid;gap:2px}.ready-state strong{font-size:11px}.ready-state small{color:var(--muted);font-size:9px}
  .repair-plan,.manual-plan{display:grid;gap:8px;margin-top:12px;padding:11px;border:1px solid var(--border-soft);border-radius:9px;background:var(--bg-elevated)}.repair-plan>strong,.manual-plan>strong{font-size:10px}.repair-row{display:grid;grid-template-columns:auto minmax(0,1fr);gap:9px;align-items:start}.repair-row div{display:grid;gap:2px}.repair-row b{font-size:10px}.repair-row small{color:var(--muted);font-size:9px;line-height:1.45}.repair-dot{width:7px;height:7px;margin-top:4px;border-radius:50%;background:var(--accent)}.manual-dot{width:18px;height:18px;display:grid;place-items:center;border-radius:50%;background:var(--warning-bg);color:var(--warning);font-size:9px;font-weight:800}
  .technical-checks{margin-top:11px;border-top:1px solid var(--border-soft);padding-top:9px}.technical-checks summary{color:var(--muted);font-size:9px;cursor:pointer}.technical-checks p{display:grid;grid-template-columns:130px 1fr;gap:8px;margin:8px 0;font-size:9px}.technical-checks span{color:var(--muted)}
  @media(max-width:760px){.health-heading{align-items:flex-start;flex-direction:column}.technical-checks p{grid-template-columns:1fr}}
</style>