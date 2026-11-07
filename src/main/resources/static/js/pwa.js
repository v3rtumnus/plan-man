// Registers the service worker that makes plan-man installable as a home-screen app.
if ('serviceWorker' in navigator) {
    window.addEventListener('load', function () {
        navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(function (error) {
            console.warn('Service worker registration failed', error);
        });
    });
}
