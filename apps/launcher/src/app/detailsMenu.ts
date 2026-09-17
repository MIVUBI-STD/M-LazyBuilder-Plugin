const ITEM_SELECTOR = 'button:not([disabled]), [role="menuitem"]:not([aria-disabled="true"])';

function items(node: HTMLDetailsElement): HTMLElement[] {
  return Array.from(node.querySelectorAll<HTMLElement>(ITEM_SELECTOR))
    .filter((item) => item.closest('details') === node);
}

export function detailsMenu(node: HTMLDetailsElement) {
  const summary = node.querySelector<HTMLElement>(':scope > summary');
  const popover = node.querySelector<HTMLElement>(':scope > .menu-popover');
  node.dataset.desktopMenu = 'true';
  summary?.setAttribute('aria-haspopup', 'menu');
  popover?.setAttribute('role', 'menu');

  const syncExpanded = () => summary?.setAttribute('aria-expanded', node.open ? 'true' : 'false');
  const close = (restoreFocus = false) => {
    if (!node.open) return;
    node.open = false;
    syncExpanded();
    if (restoreFocus) summary?.focus();
  };

  const focusItem = (index: number) => {
    const available = items(node);
    if (!available.length) return;
    available[(index + available.length) % available.length]?.focus();
  };

  const handleToggle = () => {
    syncExpanded();
    if (!node.open) return;
    document.querySelectorAll<HTMLDetailsElement>('details[data-desktop-menu="true"][open]').forEach((other) => {
      if (other !== node) other.open = false;
    });
  };

  const handleDocumentPointer = (event: PointerEvent) => {
    if (node.open && event.target instanceof Node && !node.contains(event.target)) close(false);
  };

  const handleKeyDown = (event: KeyboardEvent) => {
    const available = items(node);
    const activeIndex = document.activeElement instanceof HTMLElement ? available.indexOf(document.activeElement) : -1;

    if (event.key === 'Escape' && node.open) {
      event.preventDefault();
      close(true);
      return;
    }
    if (event.key === 'ArrowDown') {
      if (!node.open) node.open = true;
      event.preventDefault();
      focusItem(activeIndex >= 0 ? activeIndex + 1 : 0);
      return;
    }
    if (event.key === 'ArrowUp') {
      if (!node.open) node.open = true;
      event.preventDefault();
      focusItem(activeIndex >= 0 ? activeIndex - 1 : available.length - 1);
      return;
    }
    if (event.key === 'Home' && node.open && available.length) {
      event.preventDefault();
      focusItem(0);
      return;
    }
    if (event.key === 'End' && node.open && available.length) {
      event.preventDefault();
      focusItem(available.length - 1);
    }
  };

  const handleClick = (event: MouseEvent) => {
    const target = event.target instanceof Element ? event.target.closest('button,[role="menuitem"]') : null;
    if (target && target.closest('details') === node) queueMicrotask(() => close(false));
  };

  node.addEventListener('toggle', handleToggle);
  node.addEventListener('keydown', handleKeyDown);
  node.addEventListener('click', handleClick);
  document.addEventListener('pointerdown', handleDocumentPointer);
  syncExpanded();

  return {
    destroy() {
      node.removeEventListener('toggle', handleToggle);
      node.removeEventListener('keydown', handleKeyDown);
      node.removeEventListener('click', handleClick);
      document.removeEventListener('pointerdown', handleDocumentPointer);
      delete node.dataset.desktopMenu;
    }
  };
}
