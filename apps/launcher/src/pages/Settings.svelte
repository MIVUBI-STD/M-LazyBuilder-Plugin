<script lang="ts">
  import { onMount } from 'svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import type { ClientIntegrationStatus, ServerResourceProfile } from '../app/bridge/runtimeApi';

  const emptyProfile: ServerResourceProfile = {
    totalMemoryMb: 0, reservedSystemMemoryMb: 0, safeMaxMemoryMb: 1024, logicalProcessors: 1,
    currentMaxMemoryMb: 1024, currentMinMemoryMb: 1024, currentPreset: 'Custom',
    performance: { name: 'Performance', maxMemoryMb: 1024, minMemoryMb: 1024 },
    boost: { name: 'Boost', maxMemoryMb: 1024, minMemoryMb: 1024 }, warning: ''
  };

  let profile: ServerResourceProfile = emptyProfile;
  let ramMb = 1024;
  let preset = 'Custom';
  let savedRamMb = 1024;
  let savedPreset = 'Custom';
  let loaded = false;
  let busy = false;
  let error = '';
  let message = '';

  let clientStatus: ClientIntegrationStatus | null = null;
  let clientBusy = false;
  let clientError = '';
  let clientMessage = '';
  let selectedClientPath = '';

  const gb = (mb: number) => mb / 1024;
  const selectedLabel = () => preset === 'Custom' ? 'Custom' : preset;
  const dirty = () => loaded && (ramMb !== savedRamMb || preset !== savedPreset);

  function syncFromProfile(next: ServerResourceProfile) {
    profile = next;
    ramMb = Math.min(next.currentMaxMemoryMb, next.safeMaxMemoryMb);
    preset = next.currentPreset;
    savedRamMb = ramMb;
    savedPreset = preset;
    loaded = true;
  }

  function syncClientState(next: ClientIntegrationStatus) {
    clientStatus = next;
    selectedClientPath = next.selectedProfile?.path ?? '';
  }

  async function load() {
    error = '';
    try { syncFromProfile(await runtimeProduct.server.resources()); }
    catch (e) { loaded = false; error = friendlyError(e); }
  }

  async function loadClient() {
    clientError = '';
    clientMessage = '';
    try { syncClientState(await runtimeProduct.client.status()); }
    catch (e) { clientStatus = null; clientError = friendlyError(e); }
  }

  async function selectClientProfile() {
    if (!selectedClientPath || clientBusy) return;
    clientBusy = true; clientError = ''; clientMessage = '';
    try {
      syncClientState(await runtimeProduct.client.selectProfile(selectedClientPath));
      clientMessage = 'Modrinth profile selected. LazyBuilder will always revalidate this exact folder before Sync Client.';
    } catch (e) { clientError = friendlyError(e); }
    finally { clientBusy = false; }
  }

  async function pickClientProfile() {
    if (clientBusy) return;
    clientBusy = true; clientError = ''; clientMessage = '';
    try {
      const next = await runtimeProduct.client.pickProfile();
      syncClientState(next);
      if (next.selectedProfile) {
        clientMessage = 'Modrinth profile location saved. LazyBuilder derived the mods folder from this profile automatically.';
      }
    } catch (e) { clientError = friendlyError(e); }
    finally { clientBusy = false; }
  }

  async function syncClient() {
    if (clientBusy) return;
    clientBusy = true; clientError = ''; clientMessage = '';
    try {
      syncClientState(await runtimeProduct.client.sync());
      clientMessage = clientStatus?.ready ? 'LazyBuilder client is ready.' : clientStatus?.message ?? '';
    } catch (e) { clientError = friendlyError(e); }
    finally { clientBusy = false; }
  }

  function selectPreset(name: 'Performance' | 'Boost') {
    if (!loaded) return;
    const selected = name === 'Performance' ? profile.performance : profile.boost;
    ramMb = selected.maxMemoryMb;
    preset = name;
    message = '';
  }

  function markCustom() { if (loaded) { preset = 'Custom'; message = ''; } }

  async function save() {
    if (busy || !dirty()) return;
    busy = true; error = ''; message = '';
    try {
      const next = await runtimeProduct.server.saveResources({ maxMemoryMb: ramMb, preset });
      syncFromProfile(next);
      message = 'Saved. Memory changes apply the next time the server starts.';
    } catch (e) { error = friendlyError(e); }
    finally { busy = false; }
  }

  function friendlyError(value: unknown) {
    return String(value).replace(/^Error:\s*/i, '').trim() || 'Something went wrong. Try again.';
  }

  onMount(() => { void load(); void loadClient(); });
</script>

<section class="settings-page">
  <header class="page-head">
    <div><h2>Settings</h2><p>Configure this server and the Minecraft client you use with LazyBuilder.</p></div>
  </header>

  <section class="settings-group client-group">
    <div class="group-copy">
      <h3>Minecraft Client</h3>
      <p>Modrinth remains your Minecraft launcher. LazyBuilder only installs and repairs its own three Fabric mods.</p>
    </div>
    <div class="group-content">
      {#if clientError}<div class="notice error" role="alert"><strong>Client Setup unavailable</strong><span>{clientError}</span></div>{/if}
      {#if clientMessage}<div class="notice success" aria-live="polite">{clientMessage}</div>{/if}

      {#if !clientStatus}
        <div class="client-card"><strong>Checking Modrinth…</strong></div>
      {:else}
        <div class="client-card">
          <div class="client-head">
            <div><span class="eyebrow">Launcher</span><strong>Modrinth App</strong><small>LazyBuilder does not replace or launch Minecraft.</small></div>
            <span class:ready={clientStatus.ready} class="status-pill">{clientStatus.ready ? 'Ready' : 'Setup required'}</span>
          </div>

          <div class="profile-controls">
            <label class="profile-picker">
              <span>Detected profiles</span>
              <select bind:value={selectedClientPath} disabled={clientBusy || clientStatus.profiles.length === 0} onchange={selectClientProfile}>
                <option value="">Choose profile…</option>
                {#each clientStatus.profiles as item}
                  <option value={item.path}>{item.name} — {item.gameVersion ?? 'Unknown'} / {item.loader ?? 'Unknown'}{item.compatible ? '' : item.gameVersion && item.loader ? ' (incompatible)' : ' (needs verification)'}</option>
                {/each}
              </select>
            </label>
            <button class="secondary" disabled={clientBusy} onclick={pickClientProfile}>{clientStatus.selectedProfile ? 'Change profile…' : 'Select profile…'}</button>
            <button class="secondary compact" disabled={clientBusy} onclick={loadClient}>Refresh</button>
          </div>

          <p class="client-status-copy">{clientStatus.message}</p>

          {#if clientStatus.selectedProfile}
            <div class="path-panel">
              <div><span>Profile</span><strong>{clientStatus.selectedProfile.name}</strong></div>
              <div><span>Profile location</span><code>{clientStatus.selectedProfile.path}</code></div>
              <div><span>Mods location</span><code>{clientStatus.selectedProfile.modsPath}</code></div>
              <div><span>Modrinth data root</span><code>{clientStatus.selectedProfile.modrinthRoot}</code></div>
            </div>

            <div class="profile-meta">
              <span>Minecraft {clientStatus.selectedProfile.gameVersion ?? 'Unknown'}</span>
              <span>{clientStatus.selectedProfile.loader ?? 'Unknown loader'}</span>
              <span>Verified from: {clientStatus.selectedProfile.verification}</span>
            </div>
          {/if}

          <div class="component-list">
            {#each clientStatus.mods as item}
              <div class="component-row"><div><strong>{item.displayName}</strong><small>{item.targetFile}</small></div><span class:ok={item.state === 'Installed'}>{item.state}</span></div>
            {/each}
          </div>

          <div class="client-footer">
            <p>Choose the exact Modrinth profile folder, not its mods folder. LazyBuilder derives <code>&lt;profile&gt;\mods</code> itself and never modifies third-party mod files.</p>
            <button class="primary" disabled={clientBusy || !clientStatus.selectedProfile?.compatible} onclick={syncClient}>{clientBusy ? 'Working…' : 'Sync Client'}</button>
          </div>
        </div>
      {/if}
    </div>
  </section>

  {#if error}<div class="notice error" role="alert"><strong>Settings unavailable</strong><span>{error}</span></div>{/if}
  {#if message}<div class="notice success" aria-live="polite">{message}</div>{/if}

  <section class="settings-group">
    <div class="group-copy"><h3>Performance</h3><p>Use a preset unless this server has a specific memory requirement.</p></div>
    <div class="group-content">
      <div class="preset-list">
        <button class="preset-row" class:selected={preset === 'Performance'} disabled={busy || !loaded} onclick={() => selectPreset('Performance')}>
          <span class="radio-dot"></span><span class="preset-copy"><strong>Performance</strong><small>Recommended for everyday building</small></span><span class="preset-value">{gb(profile.performance.maxMemoryMb).toFixed(1)} GB</span>
        </button>
        <button class="preset-row" class:selected={preset === 'Boost'} disabled={busy || !loaded} onclick={() => selectPreset('Boost')}>
          <span class="radio-dot"></span><span class="preset-copy"><strong>Boost</strong><small>For imports, world generation and heavier build operations</small></span><span class="preset-value">{gb(profile.boost.maxMemoryMb).toFixed(1)} GB</span>
        </button>
      </div>
      <p class="managed-copy">CPU is managed automatically so Paper can use spare capacity without taking responsiveness away from Minecraft.</p>
    </div>
  </section>

  <section class="settings-group">
    <div class="group-copy"><h3>Advanced</h3><p>Manual memory control is optional.</p></div>
    <details class="advanced" open={preset === 'Custom'}>
      <summary><span><strong>Custom memory</strong><small>Set a different server memory limit.</small></span><span>{preset === 'Custom' ? `${gb(ramMb).toFixed(1)} GB` : 'Configure'}</span></summary>
      <div class="advanced-body">
        <div class="memory-head"><div><span>Maximum memory</span><strong>{gb(ramMb).toFixed(1)} GB</strong></div><small>Safe maximum: {gb(profile.safeMaxMemoryMb).toFixed(1)} GB</small></div>
        <input aria-label="Maximum server RAM allocation" type="range" min="1024" max={Math.max(1024, profile.safeMaxMemoryMb)} step="256" bind:value={ramMb} oninput={markCustom} disabled={busy || !loaded} />
        <div class="range-labels"><span>1 GB</span><span>{gb(profile.safeMaxMemoryMb).toFixed(1)} GB</span></div>
        <p>LazyBuilder keeps memory available for Windows, Minecraft and other applications.</p>
      </div>
    </details>
  </section>

  {#if profile.warning}<div class="warning-card"><strong>Memory warning</strong><p>{profile.warning}</p></div>{/if}

  <footer class="settings-footer">
    <div><strong>{selectedLabel()}</strong><span>{gb(ramMb).toFixed(1)} GB maximum</span></div>
    <button class="primary" disabled={busy || !dirty()} onclick={save}>{busy ? 'Saving…' : dirty() ? 'Save changes' : 'Saved'}</button>
  </footer>
</section>

<style>
  .settings-page{width:min(920px,100%)}.page-head{margin-bottom:18px}.page-head h2{margin:0;font-size:18px}.page-head p{margin:4px 0 0;color:var(--muted);font-size:12px}
  .notice{display:grid;gap:3px;margin-bottom:12px;padding:10px 12px;border-radius:8px;font-size:11px}.notice.error{border:1px solid #713940;background:var(--danger-bg);color:#ffdadd}.notice.success{border:1px solid var(--accent-border);background:var(--accent-soft);color:#a8e5b8}
  .settings-group{display:grid;grid-template-columns:180px minmax(0,1fr);gap:28px;padding:20px 0;border-top:1px solid var(--border-soft)}.settings-group:first-of-type{padding-top:0;border-top:0}.group-copy h3{margin:0;font-size:12px}.group-copy p{margin:5px 0 0;color:var(--muted);font-size:11px;line-height:1.45}.group-content{min-width:0}
  .client-card{border:1px solid var(--border-soft);border-radius:10px;background:var(--surface);padding:13px}.client-head{display:flex;align-items:center;justify-content:space-between;gap:18px}.client-head>div{display:grid;gap:2px}.client-head strong{font-size:12px}.client-head small,.eyebrow{font-size:10px;color:var(--muted)}.status-pill{padding:4px 7px;border-radius:999px;background:var(--warning-bg);color:#ebd9aa;font-size:9px;font-weight:700}.status-pill.ready{background:var(--accent-soft);color:#a8e5b8}
  .profile-controls{display:grid;grid-template-columns:minmax(0,1fr) auto auto;align-items:end;gap:8px;margin-top:12px}.profile-picker{display:grid;gap:5px}.profile-picker>span{font-size:10px;color:var(--muted)}.profile-picker select{min-height:34px;border:1px solid var(--border-soft);border-radius:7px;background:var(--surface-2);color:var(--text);padding:0 9px}.secondary{min-height:34px;border:1px solid var(--border-soft);border-radius:7px;padding:7px 10px;background:var(--surface-2);color:var(--text);cursor:pointer;white-space:nowrap}.secondary:hover:not(:disabled){background:var(--surface-3)}.secondary.compact{padding-inline:8px}.client-status-copy{margin:9px 0 0;color:var(--muted);font-size:10px;line-height:1.45}
  .path-panel{display:grid;gap:7px;margin-top:12px;padding:10px;border:1px solid var(--border-soft);border-radius:8px;background:var(--surface-2)}.path-panel>div{display:grid;grid-template-columns:110px minmax(0,1fr);gap:10px;align-items:start}.path-panel span{color:var(--muted);font-size:9px}.path-panel strong,.path-panel code{min-width:0;color:var(--text-soft);font-size:9px;overflow-wrap:anywhere}.path-panel code{font-family:ui-monospace,SFMono-Regular,Consolas,monospace}
  .profile-meta{display:flex;flex-wrap:wrap;gap:8px;margin-top:7px}.profile-meta span{font-size:9px;color:var(--muted);padding:3px 6px;border-radius:5px;background:var(--surface-2)}.component-list{margin-top:12px;border-top:1px solid var(--border-soft)}.component-row{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:9px 0;border-bottom:1px solid var(--border-soft)}.component-row>div{display:grid;gap:1px}.component-row strong{font-size:11px}.component-row small{font-size:9px;color:var(--muted)}.component-row>span{font-size:10px;color:#ebd9aa}.component-row>span.ok{color:#a8e5b8}.client-footer{display:flex;align-items:flex-end;justify-content:space-between;gap:16px;padding-top:11px}.client-footer p{margin:0;max-width:500px;font-size:9px;line-height:1.45;color:var(--muted-2)}.client-footer code{font-family:ui-monospace,SFMono-Regular,Consolas,monospace}
  .preset-list{overflow:hidden;border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}.preset-row{width:100%;min-height:66px;display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:11px;padding:11px 13px;border:0;border-bottom:1px solid var(--border-soft);background:transparent;color:var(--text);text-align:left;cursor:pointer}.preset-row:last-child{border-bottom:0}.preset-row:hover:not(:disabled){background:var(--surface-2)}.preset-row.selected{background:var(--accent-soft)}.radio-dot{width:15px;height:15px;position:relative;border:2px solid #66717b;border-radius:50%}.preset-row.selected .radio-dot{border-color:var(--accent)}.preset-row.selected .radio-dot::after{content:'';position:absolute;inset:3px;border-radius:50%;background:var(--accent)}.preset-copy{display:grid;gap:2px}.preset-copy strong{font-size:12px}.preset-copy small{color:var(--muted);font-size:10px}.preset-value{color:var(--text-soft);font-size:11px;font-weight:750}.managed-copy{margin:8px 2px 0;color:var(--muted-2);font-size:10px;line-height:1.45}
  .advanced{border:1px solid var(--border-soft);border-radius:10px;background:var(--surface)}.advanced summary{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:12px 13px;list-style:none;cursor:pointer}.advanced summary::-webkit-details-marker{display:none}.advanced summary>span:first-child{display:grid;gap:2px}.advanced summary strong{font-size:11px}.advanced summary small,.advanced summary>span:last-child{color:var(--muted);font-size:10px}.advanced-body{padding:13px;border-top:1px solid var(--border-soft)}.memory-head{display:flex;justify-content:space-between;align-items:flex-end;gap:18px}.memory-head>div{display:grid;gap:2px}.memory-head span,.memory-head small{color:var(--muted);font-size:10px}.memory-head strong{font-size:18px}input[type='range']{width:100%;min-height:auto;margin:16px 0 4px;padding:0;border:0;background:transparent;box-shadow:none;accent-color:var(--accent)}.range-labels{display:flex;justify-content:space-between;color:var(--muted-2);font-size:9px}.advanced-body p{margin:9px 0 0;color:var(--muted-2);font-size:10px}
  .warning-card{margin-top:12px;padding:10px 12px;border:1px solid #6b5730;border-radius:8px;background:var(--warning-bg);color:#ebd9aa;font-size:11px}.warning-card p{margin:4px 0 0}.settings-footer{display:flex;align-items:center;justify-content:flex-end;gap:14px;margin-top:20px;padding-top:16px;border-top:1px solid var(--border-soft)}.settings-footer>div{display:grid;gap:1px;text-align:right}.settings-footer strong{font-size:10px}.settings-footer span{color:var(--muted);font-size:9px}.primary{min-height:var(--control-height);border:1px solid var(--accent);border-radius:8px;padding:8px 13px;background:var(--accent);color:var(--accent-ink);font-weight:650;cursor:pointer}.primary:hover:not(:disabled){background:var(--accent-hover)}button:disabled{opacity:.5;cursor:default}
  @media(max-width:760px){.settings-group{grid-template-columns:1fr;gap:10px}.profile-controls{grid-template-columns:1fr}.settings-footer,.client-footer{align-items:stretch;flex-direction:column}.settings-footer>div{text-align:left}.memory-head{align-items:flex-start;flex-direction:column}.path-panel>div{grid-template-columns:1fr;gap:2px}}
</style>
