const FOCUSABLE = [
  'button:not([disabled])',
  'a[href]',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])'
].join(',');

function visibleFocusable(dialog: HTMLElement) {
  return Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((element) => {
    const style = window.getComputedStyle(element);
    return style.visibility !== 'hidden' && style.display !== 'none';
  });
}

function labelCloseButton(dialog: HTMLElement) {
  const button = dialog.querySelector<HTMLElement>('.icon-button');
  if (button && !button.getAttribute('aria-label')) button.setAttribute('aria-label', 'Close dialog');
}

function closeButton(dialog: HTMLElement) {
  return dialog.querySelector<HTMLButtonElement>('.icon-button:not([disabled])');
}

export function installModalAccessibility(): () => void {
  if (import.meta.env.MODE === 'visual-preview') return () => {};

  let activeDialog: HTMLElement | null = null;
  let restoreFocus: HTMLElement | null = null;

  const activate = (dialog: HTMLElement) => {
    if (activeDialog === dialog) return;
    restoreFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    activeDialog = dialog;
    labelCloseButton(dialog);
    if (!dialog.hasAttribute('tabindex')) dialog.setAttribute('tabindex', '-1');
    queueMicrotask(() => {
      const focusable = visibleFocusable(dialog);
      (focusable[0] ?? dialog).focus();
    });
  };

  const deactivate = () => {
    if (!activeDialog) return;
    activeDialog = null;
    const target = restoreFocus;
    restoreFocus = null;
    queueMicrotask(() => {
      if (target?.isConnected) target.focus();
    });
  };

  const reconcile = () => {
    const dialogs = Array.from(document.querySelectorAll<HTMLElement>('[role="dialog"][aria-modal="true"]'));
    const next = dialogs.at(-1) ?? null;
    if (next) activate(next);
    else deactivate();
  };

  const onKeyDown = (event: KeyboardEvent) => {
    const dialog = activeDialog;
    if (!dialog) return;

    if (event.key === 'Escape') {
      const close = closeButton(dialog);
      if (close) {
        event.preventDefault();
        event.stopPropagation();
        close.click();
      }
      return;
    }

    if (event.key !== 'Tab') return;
    const focusable = visibleFocusable(dialog);
    if (focusable.length === 0) {
      event.preventDefault();
      dialog.focus();
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const current = document.activeElement;
    if (event.shiftKey && (current === first || !dialog.contains(current))) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && (current === last || !dialog.contains(current))) {
      event.preventDefault();
      first.focus();
    }
  };

  const observer = new MutationObserver(reconcile);
  observer.observe(document.body, { childList: true, subtree: true });
  document.addEventListener('keydown', onKeyDown, true);
  reconcile();

  return () => {
    observer.disconnect();
    document.removeEventListener('keydown', onKeyDown, true);
    activeDialog = null;
    restoreFocus = null;
  };
}
