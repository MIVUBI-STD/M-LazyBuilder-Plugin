<script lang="ts">
  import { onMount } from 'svelte';
  import RuntimeErrorNotice from '../components/RuntimeErrorNotice.svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import { presentRuntimeError } from '../app/runtimeErrorPresentation';
  import type { RuntimeErrorPresentation } from '../app/runtimeErrorPresentation';
  import type { DiagnosticSummary, LauncherSettings, StartupReport } from '../app/bridge/runtimeApi';

  let saved: LauncherSettings | null = null;
  let draft: LauncherSettings | null = null;
  let diagnostics: DiagnosticSummary | null = null;
  let startup: StartupReport | null = null;
  let loading = true;
  let saving = false;
  let exportingSupport = false;
  let error: RuntimeErrorPresentation | null = null;
  let message = '';

  function changed() {
    if (!saved || !draft) return false;
    return saved.rememberLastServer !== draft.rememberLastServer
      || saved.confirmCloseWhileServerRunning !== draft.confirmCloseWhileServerRunning;
  }

  function startupLabel() {
    if (!startup) return 'Unavailable';
    if (!startup.ready) return 'Needs attention';
    if (startup.degraded) return 'Recovered with notices';
    return 'Healthy';
  }

  async function loadSupportContext() {
    const [nextDiagnostics, nextStartup] = await Promise.all([
      runtimeProduct.diagnostics.summary().catch(() => null),
      runtimeProduct.startup.status().catch(() => null)
    ]);
    diagnostics = nextDiagnostics;
    startup = nextStartup;
  }

  async function load() {
    loading = true;
    error = null;
    try {
      const settings = await runtimeProduct.settings.get();
      saved = { ...settings };
      draft = { ...settings };
      await loadSupportContext();
    } catch (value) {
      error = presentRuntimeError(value, 'Could not load Launcher settings.');
    } finally {
      loading = false;
    }
  }

  async function save() {
    if (!draft || saving || !changed()) return;
    saving = true;
    error = null;
    message = '';
    try {
      const next = await runtimeProduct.settings.save(draft);
      saved = { ...next };
      draft = { ...next };
      message = 'Launcher preferences saved.';
    } catch (value) {
      error = presentRuntimeError(value, 'Could not save Launcher settings.');
    } finally {
      saving = false;
    }
  }

  async function exportSupportBundle() {
    if (exportingSupport) return;
    exportingSupport = true;
    error = null;
    message = '';
    try {
      const path = await runtimeProduct.diagnostics.exportSupportBundle();
      if (path) message = `Support bundle exported to ${path}`;
    } catch (value) {
      error = presentRuntimeError(value, 'Could not export the support bundle.');
    } finally {
      exportingSupport = false;
    }
  }

  function reset() {
    if (!saved || saving) return;
    draft = { ...saved };
    message = '';
    error = null;
  }

  onMount(() => void load());
</script>

<section class="launcher-settings" aria-labelledby="launcher-settings-heading">
  <header class="page-head">
    <div><h2 id="launcher-settings-heading">Launcher settings</h2><p>Preferences that control how the LazyBuilder desktop app behaves.</p></div>
  </header>

  <RuntimeErrorNotice {error} />
  {#if message}<div class="notice success" aria-live="polite">{message}</div>{/if}

  {#if loading || !draft}
    <div class="loading-card" aria-live="polite">Loading Launcher settings…</div>
  {:else}
    <section class="settings-group">
      <div class="group-copy"><h3>Startup</h3><p>Choose whether LazyBuilder should reopen your most recently used server when the app starts.</p></div>
      <label class="setting-row">
        <div><strong>Remember last server</strong><span>Reopen the most recent available server without starting it.</span></div>
        <input type="checkbox" bind:checked={draft.rememberLastServer} disabled={saving} />
      </label>
    </section>

    <section class="settings-group">
      <div class="group-copy"><h3>Safety</h3><p>Protect active server sessions from accidental app shutdown.</p></div>
      <label class="setting-row">
        <div><strong>Confirm before closing while server is running</strong><span>Ask before closing LazyBuilder when a server is still running.</span></div>
        <input type="checkbox" bind:checked={draft.confirmCloseWhileServerRunning} disabled={saving} />
      </label>
    </section>

    <section class="settings-group">
      <div class="group-copy"><h3>Support</h3><p>Review the current runtime context and create a local troubleshooting package when you need help diagnosing a problem.</p></div>
      <div class="support-stack">
        <section class="support-overview" aria-label="LazyBuilder support context">
          <div><span>Launcher</span><strong>{diagnostics?.launcherVersion ? `v${diagnostics.launcherVersion}` : 'Version unavailable'}</strong></div>
          <div><span>Startup</span><strong>{startupLabel()}</strong></div>
          <div><span>Java</span><strong>{diagnostics?.javaVersion || 'Unavailable'}</strong></div>
          <div><span>Server</span><strong>{diagnostics?.workspaceName || 'No server open'}</strong></div>
          <div><span>Server state</span><strong>{diagnostics?.serverState || 'Unavailable'}</strong></div>
          <div><span>Paper</span><strong>{diagnostics?.paperBuild ? `Build ${diagnostics.paperBuild}` : diagnostics?.serverPlatform || 'Unavailable'}</strong></div>
        </section>

        {#if diagnostics?.launcherLogPath}
          <div class="support-path"><span>Launcher log</span><code title={diagnostics.launcherLogPath}>{diagnostics.launcherLogPath}</code></div>
        {/if}

        <div class="support-card">
          <div><strong>Create support package</strong><span>Includes Launcher information, recent activity, startup status, and logs. Worlds, plugin data, server configuration, sign-in data, and security keys are excluded.</span><small>Personal file paths are redacted where possible. Nothing is uploaded automatically.</small></div>
          <button class="secondary" disabled={exportingSupport} onclick={exportSupportBundle}>{exportingSupport ? 'Creating…' : 'Create ZIP'}</button>
        </div>
      </div>
    </section>

    <section class="future-group" aria-label="Update preferences status">
      <div><strong>App updates</strong><span>This build does not perform automatic update checks from Settings. The stable channel remains locked until signed in-app update verification is available.</span></div>
      <span class="planned-badge">Manual release updates</span>
    </section>

    <footer class="settings-footer">
      <button class="secondary" disabled={saving || !changed()} onclick={reset}>Reset</button>
      <button class="primary" disabled={saving || !changed()} onclick={save}>{saving ? 'Saving…' : changed() ? 'Save changes' : 'Saved'}</button>
    </footer>
  {/if}
</section>

<style>
  .launcher-settings{width:min(860px,100%)}.page-head{margin-bottom:18px}.page-head h2{margin:0;font-size:18px}.page-head p{margin:4px 0 0;color:var(--muted);font-size:12px}
  .notice,.loading-card{margin-bottom:12px;padding:11px 13px;border:1px solid var(--border-soft);border-radius:9px;background:var(--surface);font-size:11px}.notice.success{border-color:var(--accent-border);background:var(--accent-soft);color:#a8e5b8}.loading-card{color:var(--muted)}
  .settings-group{display:grid;grid-template-columns:190px minmax(0,1fr);gap:28px;padding:20px 0;border-top:1px solid var(--border-soft)}.settings-group:first-of-type{border-top:0}.group-copy h3{margin:0;font-size:12px}.group-copy p{margin:5px 0 0;color:var(--muted);font-size:11px;line-height:1.45}
  .setting-row,.support-card{display:flex;align-items:center;justify-content:space-between;gap:24px;padding:14px;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}.setting-row{cursor:pointer}.setting-row>div,.support-card>div{display:grid;gap:3px}.setting-row strong,.support-card strong{font-size:11px}.setting-row span,.support-card span{color:var(--muted);font-size:10px;line-height:1.4}.support-card small{color:var(--muted-2);font-size:9px;line-height:1.4}.setting-row input{width:18px;height:18px;flex:0 0 auto;accent-color:var(--accent)}
  .support-stack{display:grid;gap:9px}.support-overview{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));overflow:hidden;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}.support-overview>div{display:grid;gap:3px;padding:11px 12px;border-right:1px solid var(--border-soft);border-bottom:1px solid var(--border-soft)}.support-overview>div:nth-child(3n){border-right:0}.support-overview>div:nth-last-child(-n+3){border-bottom:0}.support-overview span,.support-path span{color:var(--muted-2);font-size:8px;text-transform:uppercase;letter-spacing:.04em}.support-overview strong{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:10px}.support-path{display:grid;gap:5px;padding:10px 12px;border:1px solid var(--border-soft);border-radius:9px;background:var(--bg-elevated)}.support-path code{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--text-soft);font:9px ui-monospace,SFMono-Regular,Consolas,monospace}
  .future-group{display:flex;align-items:center;justify-content:space-between;gap:18px;margin-top:12px;padding:13px 14px;border:1px dashed var(--border);border-radius:10px;background:var(--bg-elevated)}.future-group>div{display:grid;gap:3px}.future-group strong{font-size:11px}.future-group span{color:var(--muted);font-size:10px}.planned-badge{padding:4px 8px;border:1px solid var(--border);border-radius:999px;white-space:nowrap}
  .settings-footer{display:flex;justify-content:flex-end;gap:8px;margin-top:22px;padding-top:16px;border-top:1px solid var(--border-soft)}.primary,.secondary{min-height:36px;padding:8px 13px;border-radius:8px;font-weight:650;cursor:pointer}.primary{border:1px solid var(--accent);background:var(--accent);color:var(--accent-ink)}.secondary{border:1px solid var(--border);background:var(--surface-2);color:var(--text)}button:disabled{opacity:.5;cursor:default}
  @media(max-width:760px){.settings-group{grid-template-columns:1fr;gap:10px}.setting-row,.support-card{align-items:flex-start;flex-direction:column}.support-overview{grid-template-columns:repeat(2,minmax(0,1fr))}.support-overview>div,.support-overview>div:nth-child(3n),.support-overview>div:nth-last-child(-n+3){border-right:1px solid var(--border-soft);border-bottom:1px solid var(--border-soft)}.support-overview>div:nth-child(2n){border-right:0}.support-overview>div:nth-last-child(-n+2){border-bottom:0}.future-group{align-items:flex-start;flex-direction:column}.settings-footer{align-items:stretch;flex-direction:column-reverse}}
</style>
