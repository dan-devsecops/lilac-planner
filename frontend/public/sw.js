// Service worker for Web Push notifications. Registered from src/push.js.
// Kept separate from notifications.js's in-page alarm poller, which remains
// the fallback when push isn't supported/permitted.

self.addEventListener('push', (event) => {
  let data = {};
  if (event.data) {
    try {
      data = event.data.json();
    } catch {
      data = { body: event.data.text() };
    }
  }
  const title = data.title || '🌸 Lilac Planner reminder';
  const options = {
    body: data.body || '',
    icon: data.icon || '/favicon.svg',
    tag: data.tag,
    data: data.data || {},
    requireInteraction: true,
  };
  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    (async () => {
      const allClients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
      for (const client of allClients) {
        if ('focus' in client) return client.focus();
      }
      if (self.clients.openWindow) return self.clients.openWindow('/');
      return undefined;
    })(),
  );
});
