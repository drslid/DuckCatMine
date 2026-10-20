export function modal(node: HTMLElement, options: { close: () => void }) {
  const previous = document.activeElement as HTMLElement;
  const focusable = () =>
    [
      ...node.querySelectorAll<HTMLElement>(
        'button:not(:disabled),input:not(:disabled),select:not(:disabled),a[href],[tabindex="0"]',
      ),
    ].filter((el) => el.getClientRects().length);
  const frame = requestAnimationFrame(() => (focusable()[0] || node).focus());
  const key = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      options.close();
    }
    if (event.key !== 'Tab') return;
    const list = focusable(),
      first = list[0],
      last = list.at(-1);
    if (!first) {
      event.preventDefault();
      node.focus();
    } else if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };
  node.addEventListener('keydown', key);
  return {
    destroy() {
      cancelAnimationFrame(frame);
      node.removeEventListener('keydown', key);
      previous?.focus();
    },
  };
}
