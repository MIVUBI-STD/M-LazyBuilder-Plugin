<script lang="ts">
  import { onDestroy, onMount, tick } from 'svelte';
  import RuntimeErrorNotice from './RuntimeErrorNotice.svelte';
  import { runtimeProduct } from '../app/bridge/runtimeProductFacade';
  import { dialogFocus } from '../app/dialogFocus';
  import { presentRuntimeError } from '../app/runtimeErrorPresentation';
  import type { RuntimeErrorPresentation } from '../app/runtimeErrorPresentation';
  import type { ServerLogTail, ServerSnapshot } from '../app/bridge/runtimeApi';

  export let open = false;
  export let workspaceId: string | undefined = undefined;
  export let serverName = 'Server';
  export let onClose: () => void = () => {};

  let snapshot: ServerSnapshot = { state: 'Offline', health: 'Offline', cpuLoadPercent: 0, usedMemoryBytes: 0, maxMemoryBytes: 0, pid: null, logPath: '' };
  let logTail: ServerLogTail = { path: '', content: '', truncated: false };
  let command = '';
  let commandBusy = false;
  let refreshBusy = false;
  let error: RuntimeErrorPresentation | null = null;
  let notice = '';
  let outputElement: HTMLDivElement | null = null;
  let commandInput: HTMLInputElement | null = null;
  let pollTimer: number | null = null;
  let lastOpen = false;

  const POLL_MS = 1500;

  function nearBottom() {
    if (!outputElement) return true;
    return outputElement.scrollHeight - outputElement.scrollTop - outputElement.clientHeight < 56;
  }

  async function refreshConsole(forceFollow = false) {
    if (refreshBusy || !open) return;
    refreshBusy = true;
    const followOutput = forceFollow || nearBottom();
    try {
      snapshot = await runtimeProduct.server.snapshot(workspaceId);
      logTail = await runtimeProduct.server.logTail(snapshot.logPath || '', workspaceId);
      error = null;
      await tick();
      if (followOutput && outputElement) outputElement.scrollTop = outputElement.scrollHeight;
    } catch (value) {
      error = presentRuntimeError(value, 'Could not refresh server console output.');
    } finally {
      refreshBusy = false;
    }
  }

  async function sendCommand() {
    const nextCommand = command.trim();
    if (commandBusy || snapshot.state !== 'Online' || !nextCommand) return;
    commandBusy = true;
    error = null;
    notice = '';
    try {
      await runtimeProduct.server.command(nextCommand, workspaceId);
      command = '';
      notice = 'Command sent.';
      await refreshConsole(true);
      await tick();
      commandInput?.focus();
    } catch (value) {
      error = presentRuntimeError(value, 'Could not send this server command.');
    } finally {
      commandBusy = false;
    }
  }

  function clearPolling() {
    if (pollTimer !== null) window.clearTimeout(pollTimer);
    pollTimer = null;
  }

  function schedulePolling() {
    clearPolling();
    if (!open || document.hidden) return;
    pollTimer = window.setTimeout(async () => {
      pollTimer = null;
      if (!open || document.hidden) return;
      await refreshConsole();
      schedulePolling();
    }, POLL_MS);
  }

  function closeConsole() {
    if (commandBusy) return;
    onClose();
  }

  onMount(() => {
    const handleVisibility = () => {
      if (document.hidden) {
        clearPolling();
        return;
      }
      if (open) void refreshConsole().finally(schedulePolling);
    };
    document.addEventListener('visibilitychange', handleVisibility);
    return () => document.removeEventListener('visibilitychange', handleVisibility);
  });

  onDestroy(clearPolling);

  $: if (open !== lastOpen) {
    lastOpen = open;
    if (open) {
      void refreshConsole(true).finally(async () => {
        schedulePolling();
        await tick();
        commandInput?.focus();
      });
    } else {
      clearPolling();
      command = '';
      error = null;
      notice = '';
    }
  }
</script>

{#if open}
  <div class="console-backdrop" role="presentation" onclick={(event) => event.currentTarget === event.target && closeConsole()}>
    <div
      use:dialogFocus={{ onEscape: closeConsole, initialFocusSelector: '.command-row input:not([disabled])', escapeDisabled: commandBusy }}
      class="console-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="server-console-title"
    >
      <header>
        <div>
          <div class="title-row">
            <h2 id="server-console-title">{serverName} console</h2>
            <span class:online={snapshot.state === 'Online'} class="state-chip">{snapshot.state}</span>
          </div>
          <p>Paper console output and direct server commands.</p>
        </div>
        <button class="icon-button" aria-label="Close server console" disabled={commandBusy} onclick={closeConsole}>×</button>
      </header>

      <RuntimeErrorNotice {error} />
      {#if notice}<div class="console-notice success" aria-live="polite">{notice}</div>{/if}
      {#if snapshot.state === 'Detached'}
        <div class="console-notice warning">Console input is unavailable because this Paper process is detached from the current launcher session. Stop it and start it from LazyBuilder to restore console control.</div>
      {:else if snapshot.state !== 'Online'}
        <div class="console-notice warning">Start the server and wait until it is Running before sending commands.</div>
      {/if}

      <div class="output-shell">
        <div class="output-meta">
          <span>{logTail.path ? logTail.path.split(/[\\/]/).pop() : 'Server output'}</span>
          <span>{logTail.truncated ? 'Recent 300 lines' : 'Live tail'}</span>
        </div>
        <div bind:this={outputElement} class="output-scroll" role="textbox" aria-readonly="true" tabindex="0" aria-label="Server console output">
          <pre>{logTail.content || 'No server output is available yet.'}</pre>
        </div>
      </div>

      <form class="command-row" onsubmit={(event) => { event.preventDefault(); void sendCommand(); }}>
        <span class="prompt" aria-hidden="true">›</span>
        <input
          bind:this={commandInput}
          bind:value={command}
          aria-label="Server command"
          autocomplete="off"
          spellcheck="false"
          maxlength="4096"
          placeholder={snapshot.state === 'Online' ? 'Enter command, e.g. list' : 'Console unavailable'}
          disabled={snapshot.state !== 'Online' || commandBusy}
        />
        <button class="send" type="submit" disabled={snapshot.state !== 'Online' || commandBusy || !command.trim()}>{commandBusy ? 'Sending…' : 'Send'}</button>
      </form>

      <footer>
        <p>Use LazyBuilder's Stop/Restart controls for server lifecycle actions.</p>
        <button class="secondary" disabled={refreshBusy} onclick={() => void refreshConsole()}>{refreshBusy ? 'Refreshing…' : 'Refresh output'}</button>
      </footer>
    </div>
  </div>
{/if}

<style>
  .console-backdrop{position:fixed;z-index:110;inset:0;display:grid;place-items:center;padding:24px;background:rgba(4,6,8,.76)}
  .console-dialog{width:min(860px,100%);height:min(650px,calc(100vh - 48px));display:grid;grid-template-rows:auto auto minmax(0,1fr) auto auto;gap:10px;padding:18px;border:1px solid var(--border);border-radius:14px;background:var(--surface);box-shadow:var(--shadow-popover)}
  header,footer,.title-row,.command-row,.output-meta{display:flex;align-items:center}header,footer{justify-content:space-between;gap:16px}h2{margin:0;font-size:18px}.title-row{gap:9px}header p,footer p{margin:3px 0 0;color:var(--muted);font-size:10px}.state-chip{padding:3px 7px;border:1px solid var(--border);border-radius:999px;color:var(--muted);font-size:8px;font-weight:800;text-transform:uppercase}.state-chip.online{border-color:var(--accent-border);background:var(--accent-soft);color:var(--accent)}
  .icon-button{width:32px;height:32px;display:grid;place-items:center;border:0;border-radius:8px;background:transparent;color:var(--muted);font-size:20px;cursor:pointer}.icon-button:disabled{opacity:.5;cursor:default}
  .console-notice{padding:9px 11px;border:1px solid var(--border-soft);border-radius:8px;font-size:10px}.console-notice.warning{border-color:#5f5125;background:var(--warning-bg)}.console-notice.success{border-color:var(--accent-border);background:var(--accent-soft)}
  .output-shell{min-height:0;display:grid;grid-template-rows:auto minmax(0,1fr);overflow:hidden;border:1px solid var(--border-soft);border-radius:10px;background:#0b0d0f}.output-meta{justify-content:space-between;padding:8px 11px;border-bottom:1px solid #22272b;color:#7f8a91;font-size:9px}.output-scroll{min-height:0;overflow:auto;outline:none}.output-scroll pre{margin:0;padding:12px;color:#c9d1d6;font:11px/1.55 ui-monospace,SFMono-Regular,Consolas,monospace;white-space:pre-wrap}.output-scroll:focus-visible{box-shadow:inset 0 0 0 1px var(--accent)}
  .command-row{min-height:42px;padding:4px;border:1px solid var(--border);border-radius:9px;background:var(--bg-elevated)}.prompt{padding-left:9px;color:var(--accent);font:700 16px ui-monospace,SFMono-Regular,Consolas,monospace}.command-row input{min-width:0;flex:1;padding:8px 9px;border:0;outline:0;background:transparent;color:var(--text);font:11px ui-monospace,SFMono-Regular,Consolas,monospace}.command-row input::placeholder{color:var(--muted-2)}.send,.secondary{min-height:32px;border-radius:7px;padding:7px 11px;font-weight:700;cursor:pointer}.send{border:1px solid var(--accent);background:var(--accent);color:var(--accent-ink)}.secondary{border:1px solid var(--border);background:var(--surface-2);color:var(--text)}button:disabled,input:disabled{opacity:.5;cursor:default}
  @media(max-width:760px){.console-backdrop{padding:12px}.console-dialog{height:calc(100vh - 24px);padding:14px}footer{align-items:flex-start;flex-direction:column}.secondary{width:100%}}
</style>
