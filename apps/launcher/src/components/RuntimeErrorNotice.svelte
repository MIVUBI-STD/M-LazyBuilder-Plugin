<script lang="ts">
  import type { RuntimeErrorPresentation } from '../app/runtimeErrorPresentation';

  export let error: RuntimeErrorPresentation | null = null;
  export let onAction: (() => Promise<void> | void) | undefined = undefined;
</script>

{#if error}
  <div class="runtime-error" role="alert">
    <div class="error-copy">
      <strong>{error.message}</strong>
      {#if error.details}<span>{error.details}</span>{/if}
      {#if error.correlationId}<small>Reference: {error.correlationId}</small>{/if}
    </div>
    {#if error.recoverable && error.action}
      {#if onAction}
        <button onclick={() => onAction?.()}>{error.action}</button>
      {:else}
        <div class="error-guidance"><b>Next step</b><span>{error.action}</span></div>
      {/if}
    {/if}
  </div>
{/if}

<style>
  .runtime-error{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:12px;align-items:start;margin-top:11px;padding:11px 12px;border:1px solid #62343a;border-radius:8px;background:var(--danger-bg);color:#ffd9dc}
  .error-copy{display:grid;gap:3px;min-width:0}.error-copy strong{font-size:10px}.error-copy span{color:#f3b9bd;font-size:9px;line-height:1.45}.error-copy small{color:#ca8f95;font-size:8px;overflow-wrap:anywhere}
  .runtime-error button{min-height:30px;padding:6px 9px;border:1px solid #7a4349;border-radius:7px;background:rgba(255,255,255,.04);color:#ffd9dc;font-size:9px;font-weight:700;cursor:pointer}
  .error-guidance{display:grid;gap:2px;min-width:120px;max-width:210px;padding-left:10px;border-left:1px solid #6b3940}.error-guidance b{font-size:8px;text-transform:uppercase;color:#ca8f95}.error-guidance span{font-size:9px;color:#ffd9dc}
  @media(max-width:760px){.runtime-error{grid-template-columns:1fr}.error-guidance{max-width:none;padding:8px 0 0;border-left:0;border-top:1px solid #6b3940}}
</style>
