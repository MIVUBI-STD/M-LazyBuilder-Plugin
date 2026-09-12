<script lang="ts">
  import Dashboard from './pages/Dashboard.svelte';
  import Worlds from './pages/Worlds.svelte';
  import Plugins from './pages/Plugins.svelte';
  import Settings from './pages/Settings.svelte';

  type Page = 'Dashboard' | 'Worlds' | 'Plugins' | 'Settings';
  let page: Page = 'Dashboard';

  const pages: Page[] = ['Dashboard', 'Worlds', 'Plugins', 'Settings'];
</script>

<div class="shell">
  <aside class="sidebar">
    <h2>LazyBuilder</h2>
    <nav>
      {#each pages as item}
        <button class:active={page === item} onclick={() => (page = item)}>{item}</button>
      {/each}
    </nav>
  </aside>

  <main class="content">
    {#if page === 'Dashboard'}
      <Dashboard />
    {:else if page === 'Worlds'}
      <Worlds />
    {:else if page === 'Plugins'}
      <Plugins />
    {:else}
      <Settings />
    {/if}
  </main>
</div>
