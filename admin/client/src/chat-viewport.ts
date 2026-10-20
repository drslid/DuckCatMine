/** Keep mobile chat inside the visible area, including iOS's keyboard viewport. */
export function chatViewport(node: HTMLElement) {
  const viewport = window.visualViewport;
  const nav = node.querySelector<HTMLElement>('.mobile-nav');
  let pending = 0;
  const set = (name: string, value: number) => {
    const pixels = `${Math.round(value)}px`;
    if (node.style.getPropertyValue(name) !== pixels)
      node.style.setProperty(name, pixels);
  };
  function update() {
    pending = 0;
    // Do not fight a deliberate pinch zoom. Chat inputs use a 16px font on mobile.
    if (viewport && Math.abs(viewport.scale - 1) > 0.02) return;
    const height = viewport?.height || window.innerHeight;
    const top = viewport?.offsetTop || 0;
    const typing =
      node.contains(document.activeElement) &&
      document.activeElement?.matches('.chat-compose input');
    const inset = typing ? Math.max(0, window.innerHeight - height - top) : 0;
    set('--chat-visual-height', height);
    set('--chat-visual-top', top);
    set('--chat-keyboard-inset', inset);
    if (nav) set('--chat-nav-height', nav.getBoundingClientRect().height);
    const keyboard = String(!!typing && (inset > 120 || height < 480));
    if (node.dataset.chatKeyboard !== keyboard) node.dataset.chatKeyboard = keyboard;
  }
  function schedule() {
    if (!pending) pending = requestAnimationFrame(update);
  }
  const observer = new ResizeObserver(schedule);
  if (nav) observer.observe(nav);
  viewport?.addEventListener('resize', schedule);
  viewport?.addEventListener('scroll', schedule);
  window.addEventListener('resize', schedule);
  node.addEventListener('focusin', schedule);
  node.addEventListener('focusout', schedule);
  schedule();
  return {
    destroy() {
      cancelAnimationFrame(pending);
      observer.disconnect();
      viewport?.removeEventListener('resize', schedule);
      viewport?.removeEventListener('scroll', schedule);
      window.removeEventListener('resize', schedule);
      node.removeEventListener('focusin', schedule);
      node.removeEventListener('focusout', schedule);
    },
  };
}
