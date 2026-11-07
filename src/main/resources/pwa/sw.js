// Service worker for the installed app. It does not cache pages: every request goes to the
// network as usual, so the app always shows current data and login redirects still work.

self.addEventListener('install', () => {
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(self.clients.claim());
});
