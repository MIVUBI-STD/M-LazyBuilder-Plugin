export type DialogFocusOptions = {
  onEscape?: () => void;
  initialFocusSelector?: string;
  escapeDisabled?: boolean;
};

const FOCUSABLE_SELECTOR = [
  'button:not([disabled])',
  '[href]',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])'
].join(',');

function focusableElements(node: HTMLElement): HTMLElement[] {
  return Array.from(node.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR))
    .filter((element) => !element.hasAttribute('hidden') && element.getAttribute('aria-hidden') !== 'true');
}

export function dialogFocus(node: HTMLElement, initialOptions: DialogFocusOptions = {}) {
  let options = initialOptions;
  const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;

  const focusInitial = () => {
    const preferred = options.initialFocusSelector
      ? node.querySelector<HTMLElement>(options.initialFocusSelector)
      : null;
    const target = preferred ?? focusableElements(node)[0] ?? node;
    if (target === node && !node.hasAttribute('tabindex')) node.tabIndex = -1;
    target.focus();
  };

  const handleKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape' && !options.escapeDisabled && options.onEscape) {
      event.preventDefault();
      event.stopPropagation();
      options.onEscape();
      return;
    }
    if (event.key !== 'Tab') return;

    const focusable = focusableElements(node);
    if (focusable.length === 0) {
      event.preventDefault();
      node.focus();
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = document.activeElement;
    if (event.shiftKey && (active === first || !node.contains(active))) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  };

  node.addEventListener('keydown', handleKeyDown);
  queueMicrotask(focusInitial);

  return {
    update(nextOptions: DialogFocusOptions = {}) {
      options = nextOptions;
    },
    destroy() {
      node.removeEventListener('keydown', handleKeyDown);
      if (previousFocus?.isConnected) previousFocus.focus();
    }
  };
}
