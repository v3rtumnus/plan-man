// Registers the service worker that makes plan-man installable and receives Web Push.
// update() on every load: Android Chrome otherwise kept an old worker without the push
// handler for a day after a deploy (2026-11-09).
if ('serviceWorker' in navigator) {
    window.addEventListener('load', function () {
        navigator.serviceWorker.register('/sw.js', { scope: '/' })
            .then(function (registration) { return registration.update(); })
            .catch(function (error) {
                console.warn('Service worker registration failed', error);
            });
    });
}
