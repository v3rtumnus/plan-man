// Service worker for the installed app. It does not cache pages: every request goes to the
// network as usual, so the app always shows current data and login redirects still work.
// It shows plan-man's Web Push messages and sends their button taps back, and it receives
// photos shared to the app ("Teilen → Plan-Man", health H6).

// Shown on /notifications; raise it with every change to this file.
const VERSION = 3;
const SHARE_PATH = '/health/meals/share';
const SHARE_CACHE = 'plan-man-share';

self.addEventListener('message', (event) => {
    if (event.data === 'version' && event.ports[0]) {
        event.ports[0].postMessage(VERSION);
    }
});

self.addEventListener('install', () => {
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(self.clients.claim());
});

// Share target: keep the shared photo in a cache and open meal capture, which picks it up.
// Handled here, so the shared POST never has to pass Authelia (its cookie may not be sent
// with a share). Without this worker the POST reaches the server, which opens capture.
self.addEventListener('fetch', (event) => {
    const url = new URL(event.request.url);
    if (event.request.method !== 'POST' || url.origin !== self.location.origin || url.pathname !== SHARE_PATH) {
        return;
    }
    event.respondWith((async () => {
        let text = '';
        try {
            const form = await event.request.formData();
            const photo = form.get('photo');
            text = String(form.get('text') || form.get('title') || '').trim();
            const cache = await caches.open(SHARE_CACHE);
            await cache.delete('/shared-photo');
            if (photo && typeof photo !== 'string') {
                await cache.put('/shared-photo', new Response(photo, { headers: { 'Content-Type': photo.type || 'image/jpeg' } }));
            }
        } catch (e) {
            // Nothing usable: capture opens empty.
        }
        const target = '/health/meals/new?shared=1' + (text && !/^https?:/.test(text) ? '&text=' + encodeURIComponent(text.slice(0, 500)) : '');
        return Response.redirect(target, 303);
    })());
});

self.addEventListener('push', (event) => {
    let data;
    try {
        data = event.data ? event.data.json() : {};
    } catch (e) {
        data = { title: 'Plan-Man', body: event.data ? event.data.text() : '' };
    }
    const options = {
        body: data.body || '',
        icon: '/resources/img/pwa/icon-192.png',
        lang: 'de',
        data: { id: data.id || null, url: data.url || '/notifications' },
        actions: (data.actions || []).slice(0, 2)
    };
    if (data.id) {
        options.tag = 'notification-' + data.id;
    }
    event.waitUntil(self.registration.showNotification(data.title || 'Plan-Man', options));
});

self.addEventListener('notificationclick', (event) => {
    const notification = event.notification;
    const id = notification.data && notification.data.id;
    const url = (notification.data && notification.data.url) || '/notifications';
    notification.close();

    if (event.action && id) {
        // Button: try it straight away. Without a live login (redirect to Authelia) open the
        // notification in the app instead, where the button can be tapped after logging in.
        const action = encodeURIComponent(event.action);
        event.waitUntil(post('/api/notifications/' + id + '/actions/' + action).then((ok) => {
            if (!ok) {
                return openPage('/notifications?id=' + id + '&action=' + action);
            }
        }));
        return;
    }
    event.waitUntil((id ? post('/api/notifications/' + id + '/read') : Promise.resolve(false))
        .then(() => openPage(url)));
});

// The browser renewed the subscription (rare): register the new one. Needs a live login;
// otherwise the device shows up as "Push aus" and push is turned on again on the page.
self.addEventListener('pushsubscriptionchange', (event) => {
    const renewed = event.newSubscription
        ? Promise.resolve(event.newSubscription)
        : self.registration.pushManager.subscribe(event.oldSubscription.options);
    event.waitUntil(renewed.then((subscription) => fetch('/api/push/subscriptions', {
        method: 'POST',
        credentials: 'same-origin',
        redirect: 'manual',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(subscription.toJSON())
    })).catch(() => undefined));
});

function post(path) {
    return fetch(path, { method: 'POST', credentials: 'same-origin', redirect: 'manual' })
        .then((response) => response.ok)
        .catch(() => false);
}

function openPage(url) {
    return self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
        for (const client of windows) {
            if (client.url.startsWith(self.location.origin) && 'navigate' in client) {
                return client.navigate(url)
                    .then((navigated) => (navigated || client).focus())
                    .catch(() => self.clients.openWindow(url));
            }
        }
        return self.clients.openWindow(url);
    });
}
