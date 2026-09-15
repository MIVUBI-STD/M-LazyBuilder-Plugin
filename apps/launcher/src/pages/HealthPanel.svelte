<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { ServerHealthSnapshot, ServerRepairPlan, WorkspaceEntry } from '../app/bridge/runtimeApi';

  export let onRepaired: (() => Promise<void> | void) | undefined = undefined;

  let workspace: WorkspaceEntry | null = null;
  let health: ServerHealthSnapshot | null = null;
  let plan: ServerRepairPlan | null = null;
  let loading = true;
  let repairing = false;
  let error = '';
  let notice = '';

  function friendlyError(value: unknown) {
    return value instanceof Error && value.message.trim()
      ? value.message.trim()
      : String(value ?? '').replace(/^Error:\s*/i, '').trim() || 'Could not inspect server health.';
  }

  async function refresh() {
    const state = await runtimeProduct.workspace.state();
    workspace = state.active ?? null;
    if (!workspace) {
      health = null;
      plan = null;
      return;
    }
    const [nextHealth, nextPlan] = await Promise.all([
      runtimeProduct.health.server(workspace.id),
      runtimeProduct.health.repairPlan(workspace.id)
    ]);
    health = nextHealth;
    plan = nextPlan;
  }

  async function initialLoad() {
    loading = true;
    error = '';
    try { await refresh(); }
    catch (value) { error = friendlyError(value); }
    finally { loading = false; }
  }

  async function repair() {
    if (!workspace || !plan?.canRepair || repairing) return;
    repairing = true;
    error = '';
    notice = '';
    try {
      const result = await runtimeProduct.health.repair(workspace.id);
      notice = result.health.ready
        ? 'LazyBuilder-owned server components were repaired and the server is ready.'
        : 'LazyBuilder-owned repairs completed. Manual attention is still required for the remaining items.';
      await refresh();
      await onRepaired?.();
    } catch (value) {
      error = friendlyError(value);
      try { await refresh(); } catch {}
    } finally {
      repairing = false;
    }
  }

  onMount(() => { void initialLoad(); });

  $: failedChecks = health?.checks.filter((check) => !check.ready) ?? [];
</script>

<section class="health-panel" aria-labelledby="health-heading">
  <header class="health-heading">
    <div>
      <h3 id="health-heading">Server health</h3>
      <p>Runtime and workspace checks derived from the Launcher backend.</p>
    </div>
    {#if plan?.canRepair}
      <button class="repair-button" disabled={repairing} onclick={repair}>{repairing ? 'Repairing…' : `Repair ${plan.repairs.length} item${plan.repairs.length === 1 ? '' : 's'}`}</button>
    {/if}
  </header>

  {#if error}<div class="health-notice danger" role="alert">{error}</div>{/if}
  {#if notice}<div class="health-notice success" aria-live="polite">{notice}</div>{/if}

  {#if loading}
    <div class="health-empty">Checking server health…</div>
  {:else if !workspace || !health || !plan}
    <div class="health-empty">Open a server to inspect its health.</div>
  {:else if health.ready}
    <div class="ready-state"><span aria-hidden="true">✓</span><div><strong>Server is ready</strong><small>Required runtime, workspace, core components, and EULA checks are healthy.</small></div></div>
  {:else}
    {#if plan.blockedReason}<div class="health-notice warning"><strong>Automatic repair is blocked</strong><span>{plan.blockedReason}</span></div>{/if}

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
  .health-notice{display:grid;gap:3px;margin-top:11px;padding:10px 11px;border-radius:8px;font-size:10px}.health-notice.danger{border:1px solid #62343a;background:var(--danger-bg);color:#ffd9dc}.health-notice.success{border:1px solid var(--accent-border);background:var(--accent-soft);color:#b7f0cb}.health-notice.warning{border:1px solid #5f5125;background:var(--warning-bg);color:var(--text-soft)}
  .health-empty{min-height:92px;display:flex;align-items:center;justify-content:center;color:var(--muted);font-size:10px}.ready-state{display:flex;align-items:center;gap:10px;margin-top:13px;padding:12px;border:1px solid var(--accent-border);border-radius:9px;background:var(--accent-soft)}.ready-state>span{width:28px;height:28px;display:grid;place-items:center;border-radius:50%;background:rgba(34,197,94,.16);color:#9ee8b9}.ready-state div{display:grid;gap:2px}.ready-state strong{font-size:11px}.ready-state small{color:var(--muted);font-size:9px}
  .repair-plan,.manual-plan{display:grid;gap:8px;margin-top:12px;padding:11px;border:1px solid var(--border-soft);border-radius:9px;background:var(--bg-elevated)}.repair-plan>strong,.manual-plan>strong{font-size:10px}.repair-row{display:grid;grid-template-columns:auto minmax(0,1fr);gap:9px;align-items:start}.repair-row div{display:grid;gap:2px}.repair-row b{font-size:10px}.repair-row small{color:var(--muted);font-size:9px;line-height:1.45}.repair-dot{width:7px;height:7px;margin-top:4px;border-radius:50%;background:var(--accent)}.manual-dot{width:18px;height:18px;display:grid;place-items:center;border-radius:50%;background:var(--warning-bg);color:var(--warning);font-size:9px;font-weight:800}
  .technical-checks{margin-top:11px;border-top:1px solid var(--border-soft);padding-top:9px}.technical-checks summary{color:var(--muted);font-size:9px;cursor:pointer}.technical-checks p{display:grid;grid-template-columns:130px 1fr;gap:8px;margin:8px 0;font-size:9px}.technical-checks span{color:var(--muted)}
  @media(max-width:760px){.health-heading{align-items:flex-start;flex-direction:column}.technical-checks p{grid-template-columns:1fr}}
</style>
