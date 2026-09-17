const MENU_ITEM_SELECTOR = 'button:not([disabled]), [role="menuitem"]:not([aria-disabled="true"])';

function menuItems(node: HTMLDetailsElement): HTMLElement[] {
  return Array.from(node.querySelectorAll<HTMLElement>(MENU_ITEM_SELECTOR))
    .filter((item) => item.closest('details') === node);
}

export function menuBehavior(node: HTMLDetailsElement) {
  const summary = node.querySelector<HTMLElement>('summary');

  const close = (restoreFocus = false) => {
    if (!node.open) return;
    node.open = false;
    if (restoreFocus) summary?.focus();
  };

  const handleDocumentPointerDown = (event: PointerEvent) => {
    if (node.open && event.target instanceof Node && !node.contains(event.target)) close(false);
  };

  const handleClick = (event: MouseEvent) => {
    if (!node.open || !(event.target instanceof Element)) return;
    const item = event.target.closest(MENU_ITEM_SELECTOR);
    if (item && node.contains(item)) close(false);
  };

  const handleKeyDown = (event: KeyboardEvent) => {
    if (!node.open) return;
    const items = menuItems(node);
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      close(true);
      return;
    }
    if (items.length === 0) return;

    const activeIndex = items.indexOf(document.activeElement as HTMLElement);
    let nextIndex: number | null = null;
    if (event.key === 'ArrowDown') nextIndex = activeIndex < 0 ? 0 : (activeIndex + 1) % items.length;
    else if (event.key === 'ArrowUp') nextIndex = activeIndex < 0 ? items.length - 1 : (activeIndex - 1 + items.length) % items.length;
    else if (event.key === 'Home') nextIndex = 0;
    else if (event.key === 'End') nextIndex = items.length - 1;

    if (nextIndex !== null) {
      event.preventDefault();
      items[nextIndex].focus();
    }
  };

  const handleToggle = () => {
    if (!node.open) return;
    queueMicrotask(() => menuItems(node)[0]?.focus());
  };

  document.addEventListener('pointerdown', handleDocumentPointerDown, true);
  node.addEventListener('click', handleClick);
  node.addEventListener('keydown', handleKeyDown);
  node.addEventListener('toggle', handleToggle);

  return {
    destroy() {
      document.removeEventListener('pointerdown', handleDocumentPointerDown, true);
      node.removeEventListener('click', handleClick);
      node.removeEventListener('keydown', handleKeyDown);
      node.removeEventListener('toggle', handleToggle);
    }
  };
}
