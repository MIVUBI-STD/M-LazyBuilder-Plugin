const MENU_SELECTOR = 'details.menu, details.row-menu';
const ACTION_SELECTOR = 'button:not(:disabled), a[href], [role="menuitem"]:not([aria-disabled="true"])';

function containingMenu(target: EventTarget | null): HTMLDetailsElement | null {
  return target instanceof Element ? target.closest<HTMLDetailsElement>(MENU_SELECTOR) : null;
}

function actions(menu: HTMLDetailsElement): HTMLElement[] {
  return Array.from(menu.querySelectorAll<HTMLElement>(ACTION_SELECTOR)).filter((item) => item.offsetParent !== null);
}

function focusAction(menu: HTMLDetailsElement, index: number) {
  const items = actions(menu);
  if (!items.length) return;
  items[Math.max(0, Math.min(index, items.length - 1))]?.focus();
}

function summaryFor(menu: HTMLDetailsElement): HTMLElement | null {
  return menu.querySelector<HTMLElement>(':scope > summary');
}

export function installDisclosureAccessibility() {
  const onKeyDown = (event: KeyboardEvent) => {
    const menu = containingMenu(event.target);
    if (!menu) return;
    const summary = summaryFor(menu);
    const target = event.target as HTMLElement | null;

    if (target === summary) {
      if (event.key === 'ArrowDown') {
        event.preventDefault();
        menu.open = true;
        requestAnimationFrame(() => focusAction(menu, 0));
      } else if (event.key === 'ArrowUp') {
        event.preventDefault();
        menu.open = true;
        requestAnimationFrame(() => focusAction(menu, actions(menu).length - 1));
      }
      return;
    }

    const items = actions(menu);
    const index = target ? items.indexOf(target) : -1;
    if (event.key === 'Escape') {
      event.preventDefault();
      menu.open = false;
      summary?.focus();
    } else if (index >= 0 && event.key === 'ArrowDown') {
      event.preventDefault();
      items[(index + 1) % items.length]?.focus();
    } else if (index >= 0 && event.key === 'ArrowUp') {
      event.preventDefault();
      items[(index - 1 + items.length) % items.length]?.focus();
    } else if (index >= 0 && event.key === 'Home') {
      event.preventDefault();
      items[0]?.focus();
    } else if (index >= 0 && event.key === 'End') {
      event.preventDefault();
      items[items.length - 1]?.focus();
    }
  };

  const onPointerDown = (event: PointerEvent) => {
    const targetMenu = containingMenu(event.target);
    document.querySelectorAll<HTMLDetailsElement>(`${MENU_SELECTOR}[open]`).forEach((menu) => {
      if (menu !== targetMenu && !menu.contains(event.target as Node)) menu.open = false;
    });
  };

  document.addEventListener('keydown', onKeyDown);
  document.addEventListener('pointerdown', onPointerDown);
  return () => {
    document.removeEventListener('keydown', onKeyDown);
    document.removeEventListener('pointerdown', onPointerDown);
  };
}
