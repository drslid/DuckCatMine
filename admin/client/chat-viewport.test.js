import test from 'node:test';
import assert from 'node:assert/strict';
import { chatViewport } from './src/chat-viewport.ts';

test('chat follows the visible keyboard viewport and releases its listeners', (t) => {
  const saved = Object.fromEntries(
    [
      'window',
      'document',
      'ResizeObserver',
      'requestAnimationFrame',
      'cancelAnimationFrame',
    ].map((key) => [key, globalThis[key]]),
  );
  t.after(() => {
    for (const [key, value] of Object.entries(saved)) {
      if (value === undefined) delete globalThis[key];
      else globalThis[key] = value;
    }
  });
  let queued = null,
    disconnected = false;
  const viewport = Object.assign(new EventTarget(), {
    height: 844,
    offsetTop: 0,
    scale: 1,
  });
  const win = Object.assign(new EventTarget(), {
    innerHeight: 844,
    visualViewport: viewport,
  });
  const input = { matches: (selector) => selector === '.chat-compose input' };
  const doc = { activeElement: null };
  const properties = new Map();
  const node = Object.assign(new EventTarget(), {
    dataset: {},
    contains: (value) => value === input,
    querySelector: () => ({ getBoundingClientRect: () => ({ height: 98 }) }),
    style: {
      getPropertyValue: (key) => properties.get(key),
      setProperty: (key, value) => properties.set(key, value),
    },
  });
  Object.assign(globalThis, {
    window: win,
    document: doc,
    ResizeObserver: class {
      observe() {}
      disconnect() {
        disconnected = true;
      }
    },
    requestAnimationFrame: (callback) => {
      queued = callback;
      return 1;
    },
    cancelAnimationFrame: () => {
      queued = null;
    },
  });
  const flush = () => {
    const callback = queued;
    queued = null;
    callback?.();
  };
  const value = (name) => Number.parseFloat(properties.get(name));
  const action = chatViewport(node);
  flush();
  assert.equal(value('--chat-visual-height'), 844);
  assert.equal(
    value('--chat-nav-height'),
    98,
    'navigation includes the iPhone safe area',
  );
  assert.equal(node.dataset.chatKeyboard, 'false');

  doc.activeElement = input;
  Object.assign(viewport, { height: 480, offsetTop: 24 });
  node.dispatchEvent(new Event('focusin'));
  viewport.dispatchEvent(new Event('resize'));
  flush();
  assert.equal(
    value('--chat-visual-height') + value('--chat-visual-top'),
    504,
    'chat ends at the visible viewport bottom',
  );
  assert.equal(
    win.innerHeight - value('--chat-keyboard-inset'),
    504,
    'drawer and navigation lift above the keyboard',
  );
  assert.equal(node.dataset.chatKeyboard, 'true');

  Object.assign(viewport, { height: 430, offsetTop: 0 });
  win.innerHeight = 430;
  win.dispatchEvent(new Event('resize'));
  flush();
  assert.equal(
    value('--chat-visual-height'),
    430,
    'browsers resizing both viewports also shrink the chat',
  );
  assert.equal(value('--chat-keyboard-inset'), 0, 'no double keyboard offset');
  assert.equal(node.dataset.chatKeyboard, 'true');

  viewport.scale = 2;
  viewport.height = 215;
  viewport.dispatchEvent(new Event('resize'));
  flush();
  assert.equal(
    value('--chat-visual-height'),
    430,
    'deliberate pinch zoom is not overridden',
  );
  viewport.scale = 1;
  viewport.height = 844;
  win.innerHeight = 844;
  doc.activeElement = null;
  node.dispatchEvent(new Event('focusout'));
  viewport.dispatchEvent(new Event('resize'));
  flush();
  assert.equal(value('--chat-keyboard-inset'), 0);
  assert.equal(node.dataset.chatKeyboard, 'false');

  action.destroy();
  viewport.dispatchEvent(new Event('resize'));
  node.dispatchEvent(new Event('focusin'));
  assert.equal(queued, null);
  assert(disconnected);
});
