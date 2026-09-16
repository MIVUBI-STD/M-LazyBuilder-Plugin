const ACTIVE_LIST_SELECTOR = '.operation-list';
const HISTORY_ROW_SELECTOR = '.history-row';
const ANNOUNCER_ID = 'lazybuilder-activity-announcer';
const MAX_SEEN = 100;

function ensureAnnouncer(): HTMLElement {
  const existing = document.getElementById(ANNOUNCER_ID);
  if (existing) return existing;

  const region = document.createElement('div');
  region.id = ANNOUNCER_ID;
  region.setAttribute('role', 'status');
  region.setAttribute('aria-live', 'polite');
  region.setAttribute('aria-atomic', 'true');
  Object.assign(region.style, {
    position: 'fixed',
    width: '1px',
    height: '1px',
    padding: '0',
    margin: '-1px',
    overflow: 'hidden',
    clip: 'rect(0, 0, 0, 0)',
    whiteSpace: 'nowrap',
    border: '0'
  });
  document.body.appendChild(region);
  return region;
}

function quietProgressLists(root: ParentNode = document) {
  root.querySelectorAll<HTMLElement>(ACTIVE_LIST_SELECTOR).forEach((list) => {
    // Progress changes every few seconds while an operation is active. Announcing the
    // whole list on every refresh is noisy and can make keyboard/screen-reader use
    // impractical. Terminal transitions are announced separately below.
    list.setAttribute('aria-live', 'off');
    list.setAttribute('aria-atomic', 'false');
  });
}

function historyAnnouncement(row: Element): string {
  const title = row.querySelector('.history-title strong')?.textContent?.trim() ?? '';
  const state = row.querySelector('.history-title span')?.textContent?.trim() ?? '';
  const status = row.querySelector('.history-status')?.textContent?.trim() ?? '';
  return [title, state, status].filter(Boolean).join('. ');
}

export function installActivityAccessibility(): () => void {
  if (import.meta.env.MODE === 'visual-preview') return () => {};

  const announcer = ensureAnnouncer();
  const seen = new Set<string>();

  const remember = (value: string) => {
    if (!value) return false;
    if (seen.has(value)) return false;
    seen.add(value);
    if (seen.size > MAX_SEEN) {
      const oldest = seen.values().next().value as string | undefined;
      if (oldest) seen.delete(oldest);
    }
    return true;
  };

  // Existing history is context, not a new event. Seed it without announcing.
  quietProgressLists();
  document.querySelectorAll(HISTORY_ROW_SELECTOR).forEach((row) => remember(historyAnnouncement(row)));

  const observer = new MutationObserver((records) => {
    quietProgressLists();
    for (const record of records) {
      for (const node of record.addedNodes) {
        if (!(node instanceof Element)) continue;
        const rows = node.matches(HISTORY_ROW_SELECTOR)
          ? [node]
          : Array.from(node.querySelectorAll(HISTORY_ROW_SELECTOR));
        for (const row of rows) {
          const message = historyAnnouncement(row);
          if (!remember(message)) continue;
          announcer.textContent = '';
          queueMicrotask(() => { announcer.textContent = message; });
        }
      }
    }
  });

  observer.observe(document.body, { childList: true, subtree: true });
  return () => {
    observer.disconnect();
    announcer.remove();
    seen.clear();
  };
}
