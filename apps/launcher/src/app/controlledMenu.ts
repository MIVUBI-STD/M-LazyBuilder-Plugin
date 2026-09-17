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
  let pointerListening = false;
  const trigger = node.querySelector<HTMLElement>('.server-menu-button,[aria-haspopup="menu"]');

  const close = (restoreFocus = false) => {
    if (!options.open) return;
    options.onClose();
    if (restoreFocus) queueMicrotask(() => trigger?.focus());
  };

  const handlePointerDown = (event: PointerEvent) => {
    if (options.open && event.target instanceof Node && !node.contains(event.target)) close(false);
  };

  const syncPointerListener = () => {
    if (options.open && !pointerListening) {
      document.addEventListener('pointerdown', handlePointerDown, true);
      pointerListening = true;
    } else if (!options.open && pointerListening) {
      document.removeEventListener('pointerdown', handlePointerDown, true);
      pointerListening = false;
    }
  };

  const sync = () => {
    trigger?.setAttribute('aria-haspopup', 'menu');
    trigger?.setAttribute('aria-expanded', options.open ? 'true' : 'false');
    syncPointerListener();
  };

  const focusItem = (index: number) => {
    queueMicrotask(() => {
      const items = menuItems(node);
      if (!items.length) return;
      items[(index + items.length) % items.length]?.focus();
    });
  };

  const openAndFocus = (last = false) => {
    if (!options.open) options.onOpen();
    queueMicrotask(() => {
      const items = menuItems(node);
      if (!items.length) return;
      (last ? items[items.length - 1] : items[0])?.focus();
    });
  };

  const handleKeyDown = (event: KeyboardEvent) => {
    const items = menuItems(node);
    const activeIndex = document.activeElement instanceof HTMLElement ? items.indexOf(document.activeElement) : -1;
    const onTrigger = event.target === trigger;

    if (event.key === 'Escape' && options.open) {
      event.preventDefault();
      close(true);
      return;
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (onTrigger || !options.open) openAndFocus(false);
      else focusItem(activeIndex >= 0 ? activeIndex + 1 : 0);
      return;
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (onTrigger || !options.open) openAndFocus(true);
      else focusItem(activeIndex >= 0 ? activeIndex - 1 : items.length - 1);
      return;
    }
    if (event.key === 'Home' && options.open && items.length) {
      event.preventDefault();
      focusItem(0);
      return;
    }
    if (event.key === 'End' && options.open && items.length) {
      event.preventDefault();
      focusItem(items.length - 1);
    }
  };

  const handleClick = (event: MouseEvent) => {
    const item = event.target instanceof Element ? event.target.closest('[role="menu"] button,[role="menuitem"]') : null;
    if (item && node.contains(item)) queueMicrotask(() => close(false));
  };

  node.addEventListener('keydown', handleKeyDown);
  node.addEventListener('click', handleClick);
  sync();

  return {
    update(nextOptions: ControlledMenuOptions) {
      options = nextOptions;
      sync();
    },
    destroy() {
      node.removeEventListener('keydown', handleKeyDown);
      node.removeEventListener('click', handleClick);
      if (pointerListening) document.removeEventListener('pointerdown', handlePointerDown, true);
    }
  };
}
