'use strict';

// Only navigations are handled. Credentials, chat and game data are never cached.
self.addEventListener('fetch', (event) => {
  if (event.request.mode !== 'navigate') return;
  event.respondWith(fetch(event.request).catch(() => new Response(
    '<!doctype html><html lang="fr"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#101a1d"><title>DuckCatMine — Hors ligne</title><body style="margin:0;min-height:100vh;display:grid;place-items:center;background:#101a1d;color:#ecf5f2;font:16px system-ui"><main style="padding:28px;text-align:center"><h1>Connexion indisponible</h1><p>Reconnectez votre téléphone puis réessayez.</p><a href="/" style="display:inline-block;padding:12px 18px;border-radius:10px;background:#c4f57a;color:#101a1d;font-weight:700;text-decoration:none">Réessayer</a></main></body></html>',
    { status: 503, headers: { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store', 'X-Robots-Tag': 'noindex, nofollow' } }
  )));
});

self.addEventListener('push', (event) => {
  let message = {};
  try { message = event.data?.json() || {}; } catch {}
  event.waitUntil((async () => {
    await self.registration.showNotification(String(message.title || 'DuckCatMine').slice(0, 80), {
      body: String(message.body || 'Nouveau message dans le chat.').slice(0, 180),
      icon: '/app-icon-192.png', badge: '/favicon-32.png',
      tag: String(message.id || Date.now()), data: { url: '/#chat' },
    });
    if (self.navigator.setAppBadge) await self.navigator.setAppBadge(1).catch(() => {});
  })());
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    const existing = windows.find((client) => new URL(client.url).origin === self.location.origin);
    if (existing) {
      existing.postMessage({ type: 'open-chat' });
      return existing.focus();
    }
    return self.clients.openWindow('/#chat');
  })());
});
