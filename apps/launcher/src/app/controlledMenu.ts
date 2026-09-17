export type ControlledMenuOptions = {
  open: boolean;
  onOpen: () => void;
  onClose: () => void;
};

const ITEM_SELECTOR = '[role="menu"] button:not([disabled]), [role="menu"] [role="menuitem"]:not([aria-disabled="true"])';

function menuItems(node: HTMLElement): HTMLElement[] {
  return Array.from(node.querySelectorAll<HTMLElement>(ITEM_SELECTOR));
}

export function controlledMenu(node: HTMLElement, initialOptions: ControlledMenuOptions) {
  let options = initialOptions;
  const trigger = node.querySelector<HTMLElement>('[aria-haspopup="menu"], .server-menu-button');

  const sync = () => {
    trigger?.setAttribute('aria-haspopup', 'menu');
    trigger?.setAttribute('aria-expanded', options.open ? 'true' : 'false');
  };

  const close = (restoreFocus = false) => {
    if (!options.open) return;
    options.onClose();
    if (restoreFocus) queueMicrotask(() => trigger?.focus());
  };

  const focusItem = (index: number) => {
    queueMicrotask(() => {
      const available = menuItems(node);
      if (!available.length) return;
      available[(index + available.length) % available.length]?.focus();
    });
  };

  const openAndFocus = (last = false) => {
    if (!options.open) options.onOpen();
    queueMicrotask(() => {
      const available = menuItems(node);
      if (!available.length) return;
      (last ? available[available.length - 1] : available[0])?.focus();
    });
  };

  const handleDocumentPointer = (event: PointerEvent) => {
    if (options.open && event.target instanceof Node && !node.contains(event.target)) close(false);
  };

  const handleKeyDown = (event: KeyboardEvent) => {
    const available = menuItems(node);
    const active = document.activeElement instanceof HTMLElement ? available.indexOf(document.activeElement) : -1;
    const onTrigger = event.target === trigger;

    if (event.key === 'Escape' && options.open) {
      event.preventDefault();
      close(true);
      return;
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (onTrigger || !options.open) openAndFocus(false);
      else focusItem(active >= 0 ? active + 1 : 0);
      return;
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (onTrigger || !options.open) openAndFocus(true);
      else focusItem(active >= 0 ? active - 1 : available.length - 1);
      return;
    }
    if (event.key === 'Home' && options.open && available.length) {
      event.preventDefault();
      focusItem(0);
      return;
    }
    if (event.key === 'End' && options.open && available.length) {
      event.preventDefault();
      focusItem(available.length - 1);
    }
  };

  const handleClick = (event: MouseEvent) => {
    const target = event.target instanceof Element ? event.target.closest('[role="menu"] button,[role="menuitem"]') : null;
    if (target && node.contains(target)) queueMicrotask(() => close(false));
  };

  node.addEventListener('keydown', handleKeyDown);
  node.addEventListener('click', handleClick);
  document.addEventListener('pointerdown', handleDocumentPointer);
  sync();

  return {
    update(nextOptions: ControlledMenuOptions) {
      options = nextOptions;
      sync();
    },
    destroy() {
      node.removeEventListener('keydown', handleKeyDown);
      node.removeEventListener('click', handleClick);
      document.removeEventListener('pointerdown', handleDocumentPointer);
    }
  };
}
