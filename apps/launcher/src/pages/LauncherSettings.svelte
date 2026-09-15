<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { LauncherSettings } from '../app/bridge/runtimeApi';

  let saved: LauncherSettings | null = null;
  let draft: LauncherSettings | null = null;
  let loading = true;
  let saving = false;
  let error = '';
  let message = '';

  function friendlyError(value: unknown) {
    return value instanceof Error && value.message.trim()
      ? value.message.trim()
      : String(value ?? '').replace(/^Error:\s*/i, '').trim() || 'Something went wrong. Try again.';
  }

  function changed() {
    if (!saved || !draft) return false;
    return saved.rememberLastServer !== draft.rememberLastServer
      || saved.confirmCloseWhileServerRunning !== draft.confirmCloseWhileServerRunning;
  }

  async function load() {
    loading = true;
    error = '';
    try {
      const settings = await runtimeProduct.settings.get();
      saved = { ...settings };
      draft = { ...settings };
    } catch (value) {
      error = friendlyError(value);
    } finally {
      loading = false;
    }
  }

  async function save() {
    if (!draft || saving || !changed()) return;
    saving = true;
    error = '';
    message = '';
    try {
      const next = await runtimeProduct.settings.save(draft);
      saved = { ...next };
      draft = { ...next };
      message = 'Launcher preferences saved.';
    } catch (value) {
      error = friendlyError(value);
    } finally {
      saving = false;
    }
  }

  function reset() {
    if (!saved || saving) return;
    draft = { ...saved };
    message = '';
    error = '';
  }

  onMount(() => void load());
</script>

<section class="launcher-settings" aria-labelledby="launcher-settings-heading">
  <header class="page-head">
    <div><h2 id="launcher-settings-heading">Launcher settings</h2><p>Preferences that control how the LazyBuilder desktop app behaves.</p></div>
  </header>

  {#if error}<div class="notice error" role="alert">{error}</div>{/if}
  {#if message}<div class="notice success" aria-live="polite">{message}</div>{/if}

  {#if loading || !draft}
    <div class="loading-card" aria-live="polite">Loading Launcher settings…</div>
  {:else}
    <section class="settings-group">
      <div class="group-copy"><h3>Startup</h3><p>Choose whether LazyBuilder should reopen your most recently used server workspace when the app starts.</p></div>
      <label class="setting-row">
        <div><strong>Remember last server</strong><span>Reopen the most recent available server without starting Paper.</span></div>
        <input type="checkbox" bind:checked={draft.rememberLastServer} disabled={saving} />
      </label>
    </section>

    <section class="settings-group">
      <div class="group-copy"><h3>Safety</h3><p>Protect active server sessions from accidental Launcher shutdown.</p></div>
      <label class="setting-row">
        <div><strong>Confirm before closing while server is running</strong><span>Ask before closing the desktop app when LazyBuilder is managing an active Paper process.</span></div>
        <input type="checkbox" bind:checked={draft.confirmCloseWhileServerRunning} disabled={saving} />
      </label>
    </section>

    <section class="future-group" aria-label="Update preferences status">
      <div><strong>Update preferences</strong><span>Automatic update checks and update channels will appear here when Launcher Self Update is implemented.</span></div>
      <span class="planned-badge">Planned</span>
    </section>

    <footer class="settings-footer">
      <button class="secondary" disabled={saving || !changed()} onclick={reset}>Reset</button>
      <button class="primary" disabled={saving || !changed()} onclick={save}>{saving ? 'Saving…' : changed() ? 'Save changes' : 'Saved'}</button>
    </footer>
  {/if}
</section>

<style>
  .launcher-settings{width:min(860px,100%)}.page-head{margin-bottom:18px}.page-head h2{margin:0;font-size:18px}.page-head p{margin:4px 0 0;color:var(--muted);font-size:12px}
  .notice,.loading-card{margin-bottom:12px;padding:11px 13px;border:1px solid var(--border-soft);border-radius:9px;background:var(--surface);font-size:11px}.notice.error{border-color:#713940;background:var(--danger-bg);color:#ffdadd}.notice.success{border-color:var(--accent-border);background:var(--accent-soft);color:#a8e5b8}.loading-card{color:var(--muted)}
  .settings-group{display:grid;grid-template-columns:190px minmax(0,1fr);gap:28px;padding:20px 0;border-top:1px solid var(--border-soft)}.settings-group:first-of-type{border-top:0}.group-copy h3{margin:0;font-size:12px}.group-copy p{margin:5px 0 0;color:var(--muted);font-size:11px;line-height:1.45}
  .setting-row{display:flex;align-items:center;justify-content:space-between;gap:24px;padding:14px;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface);cursor:pointer}.setting-row>div{display:grid;gap:3px}.setting-row strong{font-size:11px}.setting-row span{color:var(--muted);font-size:10px;line-height:1.4}.setting-row input{width:18px;height:18px;flex:0 0 auto;accent-color:var(--accent)}
  .future-group{display:flex;align-items:center;justify-content:space-between;gap:18px;margin-top:12px;padding:13px 14px;border:1px dashed var(--border);border-radius:10px;background:var(--bg-elevated)}.future-group>div{display:grid;gap:3px}.future-group strong{font-size:11px}.future-group span{color:var(--muted);font-size:10px}.planned-badge{padding:4px 8px;border:1px solid var(--border);border-radius:999px;white-space:nowrap}
  .settings-footer{display:flex;justify-content:flex-end;gap:8px;margin-top:22px;padding-top:16px;border-top:1px solid var(--border-soft)}.primary,.secondary{min-height:36px;padding:8px 13px;border-radius:8px;font-weight:650;cursor:pointer}.primary{border:1px solid var(--accent);background:var(--accent);color:var(--accent-ink)}.secondary{border:1px solid var(--border);background:var(--surface-2);color:var(--text)}button:disabled{opacity:.5;cursor:default}
  @media(max-width:760px){.settings-group{grid-template-columns:1fr;gap:10px}.setting-row{align-items:flex-start}.future-group{align-items:flex-start;flex-direction:column}.settings-footer{align-items:stretch;flex-direction:column-reverse}}
</style>
