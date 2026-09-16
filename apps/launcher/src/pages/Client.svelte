<script lang="ts">
  import { onMount } from 'svelte';
  import RuntimeErrorNotice from '../components/RuntimeErrorNotice.svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import { presentRuntimeError } from '../app/runtimeErrorPresentation';
  import type { RuntimeErrorPresentation } from '../app/runtimeErrorPresentation';
  import type { ClientIntegrationStatus } from '../app/bridge/runtimeApi';

  let status: ClientIntegrationStatus | null = null;
  let busy = false;
  let error: RuntimeErrorPresentation | null = null;
  let message = '';
  let selectedPath = '';

  function apply(next: ClientIntegrationStatus) {
    status = next;
    selectedPath = next.selectedProfile?.path ?? '';
  }

  async function refresh() {
    if (busy) return;
    busy = true; error = null; message = '';
    try { apply(await runtimeProduct.client.status()); }
    catch (e) { status = null; error = presentRuntimeError(e, 'Client setup failed.'); }
    finally { busy = false; }
  }

  async function selectDetected() {
    if (!selectedPath || busy) return;
    busy = true; error = null; message = '';
    try {
      apply(await runtimeProduct.client.selectProfile(selectedPath));
      message = 'Profile saved. LazyBuilder will revalidate this exact folder before every sync.';
    } catch (e) { error = presentRuntimeError(e, 'Could not select this Modrinth profile.'); }
    finally { busy = false; }
  }

  async function pickProfile() {
    if (busy) return;
    busy = true; error = null; message = '';
    try {
      const next = await runtimeProduct.client.pickProfile();
      apply(next);
      if (next.selectedProfile) message = 'Profile location saved. The mods folder was derived automatically.';
    } catch (e) { error = presentRuntimeError(e, 'Could not select a Modrinth profile.'); }
    finally { busy = false; }
  }

  async function syncClient() {
    if (busy) return;
    busy = true; error = null; message = '';
    try {
      apply(await runtimeProduct.client.sync());
      message = status?.ready ? 'LazyBuilder client is ready.' : status?.message ?? '';
    } catch (e) { error = presentRuntimeError(e, 'Could not synchronize the LazyBuilder client components.'); }
    finally { busy = false; }
  }

  onMount(() => void refresh());
</script>

<section class="client-page">
  <header class="page-head">
    <div><h2>Minecraft Client</h2><p>Connect LazyBuilder to the Modrinth profile you already use.</p></div>
    <button class="secondary" disabled={busy} onclick={refresh}>{busy ? 'Checking…' : 'Refresh'}</button>
  </header>

  <RuntimeErrorNotice {error} />
  {#if message}<div class="notice success" aria-live="polite">{message}</div>{/if}

  {#if !status}
    <section class="card"><strong>Checking Modrinth…</strong></section>
  {:else}
    <section class="card">
      <div class="card-head">
        <div><span class="eyebrow">Minecraft launcher</span><strong>Modrinth App</strong><small>Modrinth remains the owner of profiles, modpacks, Fabric and game launching.</small></div>
        <span class:ready={status.ready} class="status-pill">{status.ready ? 'Ready' : 'Setup required'}</span>
      </div>

      <div class="profile-controls">
        <label>
          <span>Detected profiles</span>
          <select bind:value={selectedPath} disabled={busy || status.profiles.length === 0} onchange={selectDetected}>
            <option value="">Choose profile…</option>
            {#each status.profiles as item}
              <option value={item.path}>{item.name} — {item.gameVersion ?? 'Unknown'} / {item.loader ?? 'Unknown'}{item.compatible ? '' : item.gameVersion && item.loader ? ' (incompatible)' : ' (needs verification)'}</option>
            {/each}
          </select>
        </label>
        <button class="secondary" disabled={busy} onclick={pickProfile}>{status.selectedProfile ? 'Change profile…' : 'Select profile…'}</button>
      </div>

      <p class="status-copy">{status.message}</p>

      {#if status.selectedProfile}
        <div class="path-panel">
          <div><span>Profile</span><strong>{status.selectedProfile.name}</strong></div>
          <div><span>Profile location</span><code>{status.selectedProfile.path}</code></div>
          <div><span>Mods location</span><code>{status.selectedProfile.modsPath}</code></div>
          <div><span>Modrinth data root</span><code>{status.selectedProfile.modrinthRoot}</code></div>
        </div>
        <div class="meta">
          <span>Minecraft {status.selectedProfile.gameVersion ?? 'Unknown'}</span>
          <span>{status.selectedProfile.loader ?? 'Unknown loader'}</span>
          <span>Verified from: {status.selectedProfile.verification}</span>
        </div>
      {/if}

      <div class="components">
        {#each status.mods as item}
          <div class="component"><div><strong>{item.displayName}</strong><small>{item.targetFile}</small></div><span class:ok={item.state === 'Installed'}>{item.state}</span></div>
        {/each}
      </div>

      <footer>
        <p>Select the exact Modrinth profile. LazyBuilder derives <code>&lt;profile&gt;\mods</code> and only manages its three own JAR prefixes.</p>
        <button class="primary" disabled={busy || !status.selectedProfile?.compatible} onclick={syncClient}>{busy ? 'Working…' : 'Sync Client'}</button>
      </footer>
    </section>
  {/if}
</section>

<style>
  .client-page{width:min(920px,100%)}.page-head{display:flex;align-items:flex-end;justify-content:space-between;gap:16px;margin-bottom:18px}.page-head h2{margin:0;font-size:18px}.page-head p{margin:4px 0 0;color:var(--muted);font-size:12px}
  .notice{display:grid;gap:3px;margin-bottom:12px;padding:10px 12px;border-radius:8px;font-size:11px}.notice.success{border:1px solid var(--accent-border);background:var(--accent-soft);color:#a8e5b8}
  .card{border:1px solid var(--border-soft);border-radius:10px;background:var(--surface);padding:14px}.card-head{display:flex;align-items:center;justify-content:space-between;gap:18px}.card-head>div{display:grid;gap:2px}.card-head strong{font-size:13px}.card-head small,.eyebrow{font-size:10px;color:var(--muted)}.status-pill{padding:4px 7px;border-radius:999px;background:var(--warning-bg);color:#ebd9aa;font-size:9px;font-weight:700}.status-pill.ready{background:var(--accent-soft);color:#a8e5b8}
  .profile-controls{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:end;gap:8px;margin-top:14px}label{display:grid;gap:5px}label span{font-size:10px;color:var(--muted)}select{min-height:36px;border:1px solid var(--border-soft);border-radius:7px;background:var(--surface-2);color:var(--text);padding:0 9px}.secondary,.primary{min-height:36px;border-radius:7px;padding:7px 11px;font-weight:650;cursor:pointer}.secondary{border:1px solid var(--border-soft);background:var(--surface-2);color:var(--text)}.primary{border:1px solid var(--accent);background:var(--accent);color:var(--accent-ink)}button:disabled{opacity:.5;cursor:default}.status-copy{margin:10px 0 0;color:var(--muted);font-size:10px;line-height:1.45}
  .path-panel{display:grid;gap:7px;margin-top:13px;padding:10px;border:1px solid var(--border-soft);border-radius:8px;background:var(--surface-2)}.path-panel>div{display:grid;grid-template-columns:120px minmax(0,1fr);gap:10px}.path-panel span{color:var(--muted);font-size:9px}.path-panel strong,.path-panel code{font-size:9px;overflow-wrap:anywhere}.meta{display:flex;flex-wrap:wrap;gap:7px;margin-top:8px}.meta span{padding:3px 6px;border-radius:5px;background:var(--surface-2);color:var(--muted);font-size:9px}
  .components{margin-top:13px;border-top:1px solid var(--border-soft)}.component{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 0;border-bottom:1px solid var(--border-soft)}.component>div{display:grid;gap:1px}.component strong{font-size:11px}.component small{font-size:9px;color:var(--muted)}.component>span{font-size:10px;color:#ebd9aa}.component>span.ok{color:#a8e5b8}footer{display:flex;align-items:flex-end;justify-content:space-between;gap:16px;padding-top:12px}footer p{margin:0;max-width:600px;color:var(--muted-2);font-size:9px;line-height:1.5}code{font-family:ui-monospace,SFMono-Regular,Consolas,monospace}
  @media(max-width:760px){.page-head,footer{align-items:stretch;flex-direction:column}.profile-controls{grid-template-columns:1fr}.path-panel>div{grid-template-columns:1fr}}
</style>
