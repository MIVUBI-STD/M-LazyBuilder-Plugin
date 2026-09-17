<script lang="ts">
  import type { StartupReport, StartupStep } from '../app/bridge/runtimeApi';

  export let report: StartupReport | null = null;

  function warningSteps(): StartupStep[] {
    return report?.steps.filter((step) => step.state === 'WARNING') ?? [];
  }

  function readySteps(): StartupStep[] {
    return report?.steps.filter((step) => step.state === 'READY') ?? [];
  }

  function formatDuration() {
    if (!report) return '';
    const seconds = Math.max(0, report.completedAtUnixSeconds - report.startedAtUnixSeconds);
    return seconds < 1 ? 'Completed in under a second' : `Completed in ${seconds}s`;
  }
</script>

{#if report}
  <section
    class:attention={!report.ready}
    class:recovered={report.ready && report.degraded}
    class:healthy={report.ready && !report.degraded}
    class="recovery-center"
    aria-labelledby="startup-recovery-heading"
  >
    <header class="recovery-heading">
      <div class="recovery-status" aria-hidden="true">{report.ready ? report.degraded ? '!' : '✓' : '!'}</div>
      <div class="recovery-copy">
        <span class="eyebrow">Startup recovery</span>
        <h2 id="startup-recovery-heading">
          {report.ready ? report.degraded ? 'LazyBuilder recovered startup issues' : 'Startup checks passed' : 'LazyBuilder needs attention'}
        </h2>
        <p>
          {report.ready
            ? report.degraded
              ? 'Recovery checks completed before normal use. Review what LazyBuilder found and recovered.'
              : 'Application data, server state, and recovery checks completed without warnings.'
            : 'One or more startup checks could not be completed safely. Review the items below before changing affected server files.'}
        </p>
      </div>
      <div class="recovery-meta">
        {#if warningSteps().length > 0}<span>{warningSteps().length} notice{warningSteps().length === 1 ? '' : 's'}</span>{/if}
        <small>{formatDuration()}</small>
      </div>
    </header>

    {#if warningSteps().length > 0}
      <div class="attention-list" aria-label={report.ready ? 'Recovered startup notices' : 'Startup items needing review'}>
        {#each warningSteps() as step (step.key)}
          <article>
            <span class="step-mark" aria-hidden="true">!</span>
            <div><strong>{step.summary}</strong><p>{step.details}</p></div>
          </article>
        {/each}
      </div>
    {/if}

    {#if readySteps().length > 0}
      <details class="recovery-details">
        <summary>{readySteps().length} completed startup check{readySteps().length === 1 ? '' : 's'}</summary>
        <div class="completed-list">
          {#each readySteps() as step (step.key)}
            <article><span aria-hidden="true">✓</span><div><strong>{step.summary}</strong><small>{step.details}</small></div></article>
          {/each}
        </div>
      </details>
    {/if}
  </section>
{/if}

<style>
  .recovery-center{display:grid;gap:13px;padding:16px;border:1px solid var(--border);border-radius:12px;background:var(--surface);box-shadow:var(--shadow-card)}
  .recovery-center.attention{border-color:#6d3d42;background:linear-gradient(180deg,rgba(127,29,29,.13),var(--surface))}.recovery-center.recovered{border-color:#665625;background:linear-gradient(180deg,rgba(120,83,18,.12),var(--surface))}.recovery-center.healthy{border-color:var(--accent-border);background:linear-gradient(180deg,var(--accent-soft),var(--surface))}
  .recovery-heading{display:grid;grid-template-columns:auto minmax(0,1fr) auto;gap:12px;align-items:start}.recovery-status{width:32px;height:32px;display:grid;place-items:center;border-radius:50%;background:var(--surface-3);font-weight:800}.attention .recovery-status{background:var(--danger-bg);color:#ffb5bb}.recovered .recovery-status{background:var(--warning-bg);color:#f4d77e}.healthy .recovery-status{background:var(--accent-soft);color:#9ee8b9}.recovery-copy{min-width:0}.eyebrow{display:block;margin-bottom:2px;color:var(--muted-2);font-size:8px;font-weight:800;text-transform:uppercase;letter-spacing:.06em}.recovery-copy h2{margin:0;font-size:15px}.recovery-copy p{margin:4px 0 0;max-width:680px;color:var(--muted);font-size:11px;line-height:1.5}.recovery-meta{display:grid;justify-items:end;gap:3px}.recovery-meta span{padding:4px 7px;border-radius:999px;background:var(--surface-3);font-size:9px}.recovery-meta small{color:var(--muted-2);font-size:8px;white-space:nowrap}
  .attention-list{display:grid;gap:7px}.attention-list article{display:grid;grid-template-columns:auto minmax(0,1fr);gap:9px;padding:10px 11px;border:1px solid var(--border-soft);border-radius:8px;background:var(--bg-elevated)}.step-mark{width:19px;height:19px;display:grid;place-items:center;border-radius:50%;background:var(--warning-bg);color:var(--warning);font-size:10px;font-weight:800}.attention-list strong{font-size:10px}.attention-list p{margin:3px 0 0;color:var(--muted);font-size:10px;line-height:1.45}
  .recovery-details{border-top:1px solid var(--border-soft);padding-top:9px}.recovery-details summary{width:max-content;color:var(--muted);font-size:9px;cursor:pointer}.completed-list{display:grid;gap:5px;margin-top:8px}.completed-list article{display:grid;grid-template-columns:auto minmax(0,1fr);gap:8px;padding:7px 8px;border-radius:7px;background:var(--bg-elevated)}.completed-list>article>span{color:#9ee8b9;font-size:10px}.completed-list article div{display:grid;gap:1px}.completed-list strong{font-size:9px}.completed-list small{color:var(--muted);font-size:8px;line-height:1.4}
  @media(max-width:760px){.recovery-heading{grid-template-columns:auto minmax(0,1fr)}.recovery-meta{grid-column:2;justify-items:start}}
</style>
