<script lang="ts">
  import Dashboard from './Dashboard.svelte';
  import Worlds from './Worlds.svelte';
  import Plugins from './Plugins.svelte';
  import Settings from './Settings.svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import { dialogFocus } from '../app/dialogFocus';
  import { RuntimeError } from '../app/bridge/runtimeApi';
  import type {
    DiagnosticSummary,
    RuntimeUpdateStatus,
    WorkspaceDuplicateEstimate,
    WorkspaceEntry,
    WorkspaceProvisioningStatus
  } from '../app/bridge/runtimeApi';

  type ActiveServerPage = 'Overview' | 'Worlds' | 'Plugins' | 'Settings';
  type ManagementMode = 'active-actions' | 'duplicate' | 'remove' | 'delete-review' | 'delete-confirm' | null;

  let {
    server,
    page,
    initialProvisioning = null,
    onChanged,
    onWorkspaceUnavailable
  }: {
    server: WorkspaceEntry;
    page: ActiveServerPage;
    initialProvisioning?: WorkspaceProvisioningStatus | null;
    onChanged: () => Promise<void> | void;
    onWorkspaceUnavailable: (message: string) => Promise<void> | void;
  } = $props();

  let provisioning = $state<WorkspaceProvisioningStatus | null>(initialProvisioning);
  let runtimeUpdates = $state<RuntimeUpdateStatus | null>(null);
  let diagnostics = $state<DiagnosticSummary | null>(null);
  let surfaceError = $state('');
  let provisioningServer = $state(false);
  let acceptingEula = $state(false);
  let updatingPaper = $state(false);
  let loadedWorkspaceId = $state('');

  let managementMode = $state<ManagementMode>(null);
  let managementBusy = $state(false);
  let managementError = $state('');
  let duplicateName = $state('');
  let duplicateParent = $state('');
  let duplicateEstimate = $state<WorkspaceDuplicateEstimate | null>(null);
  let estimateLoading = $state(false);
  let deleteTypedName = $state('');

  $effect(() => {
    if (server.id === loadedWorkspaceId) return;
    loadedWorkspaceId = server.id;
    provisioning = initialProvisioning;
    runtimeUpdates = null;
    diagnostics = null;
    surfaceError = '';
    void hydrate();
  });

  function friendlyError(error: unknown) {
    if (error instanceof RuntimeError && error.code === 'SERVER_BUSY') return 'Stop this server before changing, duplicating, removing, or deleting it.';
    if (error instanceof Error && error.message.trim()) return error.message.trim();
    const message = String(error ?? '').replace(/^Error:\s*/i, '').trim();
    return message || 'Something went wrong. Try again.';
  }

  function shouldLocateWorkspace(error: unknown) {
    return error instanceof RuntimeError && (error.action === 'LOCATE_WORKSPACE' || error.code === 'WORKSPACE_UNAVAILABLE');
  }

  async function handleSurfaceError(error: unknown) {
    const message = friendlyError(error);
    if (shouldLocateWorkspace(error)) {
      await onWorkspaceUnavailable(message);
      return;
    }
    surfaceError = message;
  }

  async function handleManagementError(error: unknown) {
    const message = friendlyError(error);
    if (shouldLocateWorkspace(error)) {
      resetManagement();
      await onWorkspaceUnavailable(message);
      return;
    }
    managementError = message;
  }

  async function hydrate() {
    try {
      if (!provisioning) provisioning = await runtimeProduct.workspace.provisioningStatus();
      diagnostics = await runtimeProduct.diagnostics.summary().catch(() => null);
      runtimeUpdates = provisioning?.ready
        ? await runtimeProduct.workspace.runtimeUpdateStatus().catch(() => null)
        : null;
    } catch (error) {
      await handleSurfaceError(error);
    }
  }

  function setupProgress() {
    if (!provisioning) return 0;
    const steps = [
      provisioning.workspaceCreated,
      provisioning.javaReady,
      provisioning.paperReady,
      provisioning.coreModulesReady,
      provisioning.configReady,
      provisioning.eulaAccepted
    ];
    return Math.round((steps.filter(Boolean).length / steps.length) * 100);
  }

  function setupHeadline() {
    if (!provisioning) return 'Preparing server';
    if (!provisioning.javaReady || !provisioning.paperReady || !provisioning.coreModulesReady || !provisioning.configReady) return 'Finish preparing this server';
    if (!provisioning.eulaAccepted) return 'Accept the Minecraft EULA';
    return 'Server setup complete';
  }

  function platformLabel() {
    const platform = diagnostics?.serverPlatform;
    const minecraft = diagnostics?.minecraftVersion;
    if (!platform && !minecraft) return 'Server';
    const platformName = platform ? platform.charAt(0).toUpperCase() + platform.slice(1) : 'Server';
    return minecraft ? `${platformName} ${minecraft}` : platformName;
  }

  function displayLocation(path: string) {
    if (!path) return '';
    const normalized = path.replace(/[\\/]+$/, '');
    return normalized.split(/[\\/]/).pop() || normalized;
  }

  function parentLocation(path: string) {
    const normalized = path.replace(/[\\/]+$/, '');
    const index = Math.max(normalized.lastIndexOf('\\'), normalized.lastIndexOf('/'));
    return index > 0 ? normalized.slice(0, index) : '';
  }

  function formatBytes(bytes?: number | null) {
    if (bytes == null || !Number.isFinite(bytes)) return 'Unknown';
    if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
    return `${Math.ceil(bytes / 1024 ** 2)} MB`;
  }

  async function prepareServer() {
    provisioningServer = true;
    surfaceError = '';
    try {
      const result = await runtimeProduct.workspace.provision();
      provisioning = result.status;
      diagnostics = await runtimeProduct.diagnostics.summary().catch(() => diagnostics);
      runtimeUpdates = provisioning.ready ? await runtimeProduct.workspace.runtimeUpdateStatus().catch(() => null) : null;
      await onChanged();
    } catch (error) {
      if (shouldLocateWorkspace(error)) {
        await handleSurfaceError(error);
      } else {
        surfaceError = friendlyError(error);
        provisioning = await runtimeProduct.workspace.provisioningStatus().catch(() => provisioning);
      }
    } finally {
      provisioningServer = false;
    }
  }

  async function acceptEula() {
    acceptingEula = true;
    surfaceError = '';
    try {
      provisioning = await runtimeProduct.workspace.acceptEula();
      if (provisioning.ready) runtimeUpdates = await runtimeProduct.workspace.runtimeUpdateStatus().catch(() => null);
      await onChanged();
    } catch (error) {
      await handleSurfaceError(error);
    } finally {
      acceptingEula = false;
    }
  }

  async function updatePaper() {
    updatingPaper = true;
    surfaceError = '';
    try {
      runtimeUpdates = await runtimeProduct.workspace.updatePaper();
      diagnostics = await runtimeProduct.diagnostics.summary().catch(() => diagnostics);
    } catch (error) {
      await handleSurfaceError(error);
    } finally {
      updatingPaper = false;
    }
  }

  function openManagement() {
    managementMode = 'active-actions';
    managementError = '';
  }

  function resetManagement() {
    managementMode = null;
    managementError = '';
    duplicateEstimate = null;
    deleteTypedName = '';
  }

  function closeManagement() {
    if (managementBusy) return;
    resetManagement();
  }

  async function openServerFolder() {
    managementError = '';
    try { await runtimeProduct.workspace.openFolder(server.id); }
    catch (error) { await handleManagementError(error); }
  }

  async function refreshDuplicateEstimate() {
    duplicateEstimate = null;
    if (!duplicateParent.trim()) return;
    estimateLoading = true;
    managementError = '';
    try { duplicateEstimate = await runtimeProduct.workspace.duplicateEstimate(server.id, duplicateParent); }
    catch (error) { await handleManagementError(error); }
    finally { estimateLoading = false; }
  }

  function beginDuplicate() {
    managementMode = 'duplicate';
    managementError = '';
    duplicateName = `${server.name} Copy`;
    duplicateParent = parentLocation(server.path);
    duplicateEstimate = null;
    void refreshDuplicateEstimate();
  }

  async function chooseDuplicateLocation() {
    try {
      const selected = await runtimeProduct.workspace.pickParent();
      if (selected) {
        duplicateParent = selected;
        await refreshDuplicateEstimate();
      }
    } catch (error) { await handleManagementError(error); }
  }

  async function duplicateServer() {
    if (!duplicateName.trim() || !duplicateParent.trim()) return;
    managementBusy = true;
    managementError = '';
    try {
      await runtimeProduct.workspace.duplicate(server.id, duplicateParent, duplicateName.trim());
      resetManagement();
      await onChanged();
    } catch (error) { await handleManagementError(error); }
    finally { managementBusy = false; }
  }

  async function removeServerFromLibrary() {
    managementBusy = true;
    managementError = '';
    try {
      await runtimeProduct.workspace.removeFromLibrary(server.id);
      resetManagement();
      await onChanged();
    } catch (error) { await handleManagementError(error); }
    finally { managementBusy = false; }
  }

  async function deleteServer() {
    if (deleteTypedName !== server.name) return;
    managementBusy = true;
    managementError = '';
    try {
      await runtimeProduct.workspace.delete(server.id, deleteTypedName);
      resetManagement();
      await onChanged();
    } catch (error) { await handleManagementError(error); }
    finally { managementBusy = false; }
  }
</script>

<header class="server-toolbar">
  <div class="server-toolbar-main">
    <div class="server-icon header-icon">{server.name.slice(0,1).toUpperCase()}</div>
    <div><h1>{server.name}</h1><div class="server-context-meta"><span>{platformLabel()}</span><span>{provisioning?.ready ? 'Ready' : 'Setup required'}</span></div></div>
  </div>
  <div class="top-actions">
    {#if runtimeUpdates?.paperUpdateAvailable}<button class="update-button" disabled={updatingPaper} onclick={updatePaper}>{updatingPaper ? 'Updating…' : 'Update Paper'}</button>{/if}
    <button class="secondary-button compact-action" aria-label="Manage current server" onclick={openManagement}>•••</button>
  </div>
</header>

<main class="content">
  {#if surfaceError}<div class="error-box workspace-error" role="alert">{surfaceError}</div>{/if}
  {#if provisioning && !provisioning.ready}
    <section class="setup-card">
      <div class="setup-main"><div class="setup-icon">{provisioningServer ? '…' : '✓'}</div><div class="setup-copy"><span class="setup-label">Server setup</span><h2>{setupHeadline()}</h2><p>{!provisioning.javaReady || !provisioning.paperReady || !provisioning.coreModulesReady || !provisioning.configReady ? 'LazyBuilder can prepare everything this server needs automatically.' : 'Accept the Minecraft EULA to finish setup.'}</p></div></div>
      <div class="setup-progress-row"><div class="setup-track"><span style={`width:${setupProgress()}%`}></span></div><strong>{setupProgress()}%</strong></div>
      <div class="setup-footer"><details class="setup-details"><summary>Setup details</summary><div class="setup-steps"><span class:done={provisioning.workspaceCreated}>Server folder</span><span class:done={provisioning.javaReady}>Java</span><span class:done={provisioning.paperReady}>Paper</span><span class:done={provisioning.coreModulesReady}>Components</span><span class:done={provisioning.configReady}>Configuration</span><span class:done={provisioning.eulaAccepted}>EULA</span></div></details>{#if !provisioning.javaReady || !provisioning.paperReady || !provisioning.coreModulesReady || !provisioning.configReady}<button class="primary-button" disabled={provisioningServer} onclick={prepareServer}>{provisioningServer ? 'Preparing…' : 'Prepare server'}</button>{:else if !provisioning.eulaAccepted}<button class="primary-button" disabled={acceptingEula} onclick={acceptEula}>{acceptingEula ? 'Saving…' : 'Accept EULA'}</button>{/if}</div>
    </section>
  {/if}
  {#if page === 'Overview'}<Dashboard serverName={server.name} {onWorkspaceUnavailable} />{:else if page === 'Worlds'}<Worlds />{:else if page === 'Plugins'}<Plugins />{:else}<Settings />{/if}
</main>

{#if managementMode === 'active-actions'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && closeManagement()}><div use:dialogFocus={{ onEscape: closeManagement, initialFocusSelector: '.action-list button' }} class="dialog action-dialog" role="dialog" aria-modal="true" aria-label={`Manage ${server.name}`}><div class="dialog-heading"><div><h2>Manage {server.name}</h2><p>Open its folder, duplicate it, remove it from the library, or delete it.</p></div><button class="icon-button" aria-label="Close server management" onclick={closeManagement}>×</button></div>{#if managementError}<div class="error-box" role="alert">{managementError}</div>{/if}<div class="action-list"><button onclick={openServerFolder}>Open folder</button><button onclick={beginDuplicate}>Duplicate server</button><button onclick={() => (managementMode = 'remove')}>Remove from library</button><button class="danger-action" onclick={() => (managementMode = 'delete-review')}>Delete server…</button></div></div></div>{/if}

{#if managementMode === 'duplicate'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !managementBusy && closeManagement()}><div use:dialogFocus={{ onEscape: closeManagement, initialFocusSelector: 'input:not([readonly])', escapeDisabled: managementBusy }} class="dialog" role="dialog" aria-modal="true" aria-label="Duplicate server"><div class="dialog-heading"><div><h2>Duplicate server</h2><p>Create a complete, independently usable copy of {server.name}.</p></div><button class="icon-button" aria-label="Close duplicate server dialog" disabled={managementBusy} onclick={closeManagement}>×</button></div>{#if managementError}<div class="error-box" role="alert">{managementError}</div>{/if}<label>New server name<input bind:value={duplicateName} disabled={managementBusy} /></label><label>Save in<div class="location-row"><input value={displayLocation(duplicateParent)} title={duplicateParent} readonly /><button class="secondary-button" disabled={managementBusy} onclick={chooseDuplicateLocation}>Browse</button></div></label><div class="included-box"><strong>Included in the copy</strong><span>Worlds, server configuration, plugins, plugin data, and LazyBuilder server settings.</span><small>Temporary files, cache, logs, and active runtime files are not copied.</small></div><div class="storage-row"><div><span>Server data</span><strong>{estimateLoading ? 'Calculating…' : formatBytes(duplicateEstimate?.sourceBytes)}</strong></div><div><span>Space needed</span><strong>{estimateLoading ? 'Calculating…' : formatBytes(duplicateEstimate?.requiredBytes)}</strong></div><div><span>Available</span><strong>{estimateLoading ? 'Calculating…' : formatBytes(duplicateEstimate?.availableBytes)}</strong></div></div><div class="dialog-actions"><button class="ghost-button" disabled={managementBusy} onclick={closeManagement}>Cancel</button><button class="primary-button" disabled={managementBusy || estimateLoading || !!managementError || !duplicateName.trim() || !duplicateParent.trim()} onclick={duplicateServer}>{managementBusy ? 'Duplicating…' : 'Duplicate server'}</button></div></div></div>{/if}

{#if managementMode === 'remove'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !managementBusy && closeManagement()}><div use:dialogFocus={{ onEscape: closeManagement, initialFocusSelector: '.ghost-button', escapeDisabled: managementBusy }} class="dialog" role="dialog" aria-modal="true" aria-label={`Remove ${server.name} from LazyBuilder`}><div class="dialog-heading"><div><h2>Remove {server.name} from LazyBuilder?</h2><p>This only removes the server from your LazyBuilder library.</p></div><button class="icon-button" aria-label="Close remove server dialog" disabled={managementBusy} onclick={closeManagement}>×</button></div>{#if managementError}<div class="error-box" role="alert">{managementError}</div>{/if}<div class="safe-notice"><strong>Your files will remain on this computer.</strong><span>{server.path}</span><p>You can add the server again later.</p></div><div class="dialog-actions"><button class="ghost-button" disabled={managementBusy} onclick={closeManagement}>Cancel</button><button class="secondary-button" disabled={managementBusy} onclick={removeServerFromLibrary}>{managementBusy ? 'Removing…' : 'Remove from library'}</button></div></div></div>{/if}

{#if managementMode === 'delete-review'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && closeManagement()}><div use:dialogFocus={{ onEscape: closeManagement, initialFocusSelector: '.ghost-button' }} class="dialog danger-dialog" role="dialog" aria-modal="true" aria-label={`Delete ${server.name}`}><div class="dialog-heading"><div><h2>Delete {server.name}?</h2><p>Review exactly what will be permanently removed.</p></div><button class="icon-button" aria-label="Close delete server review" onclick={closeManagement}>×</button></div><div class="danger-summary"><strong>This permanently deletes:</strong><ul><li>Worlds</li><li>Server configuration</li><li>Plugins and plugin data</li><li>LazyBuilder server metadata stored inside this server folder</li></ul><span class="path-copy">{server.path}</span><p>The server folder is removed from disk. This cannot be undone.</p></div><div class="dialog-actions"><button class="ghost-button" onclick={closeManagement}>Cancel</button><button class="danger-button" onclick={() => (managementMode = 'delete-confirm')}>Continue</button></div></div></div>{/if}

{#if managementMode === 'delete-confirm'}<div class="modal-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && !managementBusy && closeManagement()}><div use:dialogFocus={{ onEscape: closeManagement, initialFocusSelector: 'input:not([readonly])', escapeDisabled: managementBusy }} class="dialog danger-dialog" role="dialog" aria-modal="true" aria-label={`Confirm permanent deletion of ${server.name}`}><div class="dialog-heading"><div><h2>Confirm permanent deletion</h2><p>Type the server name exactly to confirm deleting its folder and all server data.</p></div><button class="icon-button" aria-label="Close permanent deletion confirmation" disabled={managementBusy} onclick={closeManagement}>×</button></div>{#if managementError}<div class="error-box" role="alert">{managementError}</div>{/if}<div class="typed-confirmation"><code>{server.name}</code><label>Server name<input bind:value={deleteTypedName} autocomplete="off" disabled={managementBusy} /></label></div><div class="dialog-actions"><button class="ghost-button" disabled={managementBusy} onclick={() => (managementMode = 'delete-review')}>Back</button><button class="danger-button" disabled={managementBusy || deleteTypedName !== server.name} onclick={deleteServer}>{managementBusy ? 'Deleting…' : 'Delete permanently'}</button></div></div></div>{/if}

<style>
  .server-toolbar{min-height:82px;display:flex;align-items:center;justify-content:space-between;gap:20px;padding:15px 30px;border-bottom:1px solid var(--border-soft);background:#111315}.server-toolbar h1{margin:0;font-size:23px}.server-toolbar-main{display:flex;align-items:center;gap:12px}.server-icon{width:44px;height:44px;display:grid;place-items:center;border-radius:10px;background:#252a2e;border:1px solid #3a4147;font-weight:800}.server-context-meta{display:flex;gap:8px;margin-top:4px;color:var(--muted);font-size:10px}.top-actions{display:flex;gap:8px}.content{width:min(1040px,calc(100% - 56px));margin:0 auto;padding:26px 0 48px;overflow:auto;min-height:0;flex:1}.primary-button,.secondary-button,.ghost-button,.icon-button,.update-button,.danger-button{min-height:36px;border-radius:8px;padding:8px 13px;font-weight:650;cursor:pointer}.primary-button{border:1px solid var(--accent);background:var(--accent);color:var(--accent-ink)}.secondary-button{border:1px solid var(--border);background:var(--surface-2);color:var(--text)}.ghost-button,.icon-button{border:0;background:transparent;color:var(--text-soft)}.icon-button{font-size:20px}.update-button{border:1px solid var(--accent-border);background:var(--accent-soft);color:#9ee8b9}.danger-button{border:1px solid #8e3840;background:#7a2d34;color:#fff}.compact-action{min-width:40px;padding-inline:10px}button:disabled{opacity:.5;cursor:default}.error-box{padding:11px 13px;border:1px solid #70343a;border-radius:8px;background:var(--danger-bg);color:#ffd9dc;font-size:12px}.workspace-error{margin-bottom:16px}.setup-card{display:grid;gap:14px;margin-bottom:18px;padding:17px;border:1px solid var(--border);border-radius:10px;background:var(--surface)}.setup-main{display:flex;gap:12px}.setup-icon{width:34px;height:34px;display:grid;place-items:center;border-radius:9px;background:var(--accent-soft);color:var(--accent)}.setup-copy h2{margin:2px 0 4px;font-size:16px}.setup-copy p{margin:0;color:var(--muted);font-size:11px}.setup-progress-row{display:grid;grid-template-columns:1fr auto;gap:10px;align-items:center}.setup-track{height:5px;background:var(--surface-3);border-radius:999px}.setup-track span{display:block;height:100%;background:var(--accent)}.setup-footer{display:flex;justify-content:space-between;align-items:center}.setup-steps{display:flex;gap:5px;flex-wrap:wrap;margin-top:8px}.setup-steps span{padding:3px 6px;border-radius:999px;background:var(--surface-2);font-size:9px}.setup-steps span.done{background:var(--accent-soft);color:#9ee8b9}.modal-backdrop{position:fixed;inset:0;z-index:20;display:grid;place-items:center;padding:24px;background:rgba(4,6,8,.72)}.dialog{width:min(500px,100%);display:grid;gap:17px;padding:21px;border:1px solid var(--border);border-radius:14px;background:var(--surface)}.action-dialog{width:min(420px,100%)}.danger-dialog{border-color:#6d3036}.dialog-heading{display:flex;justify-content:space-between;gap:18px}.dialog-heading h2{margin:0}.dialog-heading p{margin:5px 0 0;color:var(--muted);font-size:12px}label{display:grid;gap:7px;font-size:12px}.location-row{display:grid;grid-template-columns:1fr auto;gap:8px}label>input,.location-row input{min-height:36px;padding:8px 11px;border:1px solid var(--border-soft);border-radius:8px;background:var(--surface-2);color:var(--text)}.dialog-actions{display:flex;justify-content:flex-end;gap:8px}.action-list{display:grid;gap:5px}.action-list button{padding:11px 12px;border:1px solid var(--border-soft);border-radius:8px;background:var(--surface-2);color:var(--text);text-align:left;cursor:pointer}.action-list button:hover{background:var(--surface-3)}.action-list .danger-action{margin-top:7px;border-color:#6d3036;color:#ffabb1}.included-box,.safe-notice,.danger-summary,.typed-confirmation{display:grid;gap:8px;padding:13px;border:1px solid var(--border-soft);border-radius:9px;background:var(--bg-elevated)}.included-box span,.safe-notice span,.danger-summary .path-copy{color:var(--text-soft);font-size:11px;word-break:break-all}.included-box small,.safe-notice p,.danger-summary p{margin:0;color:var(--muted);font-size:10px}.storage-row{display:grid;grid-template-columns:repeat(3,1fr);gap:7px}.storage-row>div{display:grid;gap:4px;padding:10px;border:1px solid var(--border-soft);border-radius:8px}.storage-row span{color:var(--muted);font-size:9px}.storage-row strong{font-size:12px}.danger-summary ul{margin:2px 0 2px 18px;padding:0;color:var(--text-soft);font-size:11px}.typed-confirmation code{width:max-content;padding:5px 8px;border-radius:5px;background:var(--surface-3);color:#ffb7bd}@media(max-width:760px){.server-toolbar{padding:15px 18px}.content{width:calc(100% - 28px)}.storage-row{grid-template-columns:1fr}}
</style>