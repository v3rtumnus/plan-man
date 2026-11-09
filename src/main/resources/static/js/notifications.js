// Notification buttons in the header bell and on /notifications, plus turning push on per device.
(function () {
    'use strict';

    function request(method, url, body) {
        const options = { method: method, credentials: 'same-origin' };
        if (body !== undefined) {
            options.headers = { 'Content-Type': 'application/json' };
            options.body = JSON.stringify(body);
        }
        return fetch(url, options).then(function (response) {
            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }
            return response;
        });
    }

    function onPage() {
        return window.location.pathname === '/notifications';
    }

    document.addEventListener('click', function (event) {
        const read = event.target.closest('[data-notification-read]');
        if (read) {
            event.preventDefault();
            event.stopPropagation();
            const id = read.getAttribute('data-notification-read');
            request('POST', '/api/notifications/' + id + '/read').then(function () {
                if (onPage()) {
                    window.location.reload();
                    return;
                }
                const item = document.getElementById('notification-item-' + id);
                if (item) {
                    const divider = item.nextElementSibling;
                    item.remove();
                    if (divider && divider.querySelector('.dropdown-divider')) {
                        divider.remove();
                    }
                }
                const badge = document.getElementById('notification-badge');
                if (badge) {
                    const left = parseInt(badge.textContent, 10) - 1;
                    if (left > 0) {
                        badge.textContent = left;
                    } else {
                        badge.remove();
                    }
                }
            }).catch(function (error) {
                alert('Konnte nicht als gelesen markieren: ' + error.message);
            });
            return;
        }

        const action = event.target.closest('[data-notification-action]');
        if (action) {
            action.disabled = true;
            const id = action.getAttribute('data-notification-id');
            const key = encodeURIComponent(action.getAttribute('data-notification-action'));
            request('POST', '/api/notifications/' + id + '/actions/' + key)
                .then(function () { window.location.href = '/notifications'; })
                .catch(function (error) {
                    action.disabled = false;
                    alert('Aktion fehlgeschlagen: ' + error.message);
                });
            return;
        }

        const remove = event.target.closest('[data-notification-delete]');
        if (remove) {
            request('DELETE', '/api/notifications/' + remove.getAttribute('data-notification-delete'))
                .then(function () { window.location.reload(); })
                .catch(function (error) { alert('Löschen fehlgeschlagen: ' + error.message); });
            return;
        }

        if (event.target.closest('#notifications-read-all')) {
            request('POST', '/api/notifications/read-all')
                .then(function () { window.location.reload(); })
                .catch(function (error) { alert('Fehlgeschlagen: ' + error.message); });
        }
    });

    // Opened from a push button without a live login: point at the button to tap now.
    function highlightTarget() {
        const params = new URLSearchParams(window.location.search);
        const id = params.get('id');
        if (!id) {
            return;
        }
        const row = document.getElementById('notification-' + id);
        if (!row) {
            return;
        }
        row.classList.add('is-target');
        row.scrollIntoView({ block: 'center' });
        const action = params.get('action');
        if (action) {
            const button = row.querySelector('[data-notification-action="' + CSS.escape(action) + '"]');
            if (button) {
                button.focus();
            }
        }
    }

    // ---- Push on this device ----

    function deviceLabel() {
        const ua = navigator.userAgent;
        const os = /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iOS' : /Windows/.test(ua) ? 'Windows'
            : /Mac OS/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : 'Gerät';
        const browser = /Edg\//.test(ua) ? 'Edge' : /Firefox\//.test(ua) ? 'Firefox' : /Chrome\//.test(ua) ? 'Chrome'
            : /Safari\//.test(ua) ? 'Safari' : 'Browser';
        const app = window.matchMedia('(display-mode: standalone)').matches ? ' (App)' : '';
        return os + ' · ' + browser + app;
    }

    function keyBytes(base64url) {
        const padded = (base64url + '='.repeat((4 - base64url.length % 4) % 4)).replace(/-/g, '+').replace(/_/g, '/');
        const raw = atob(padded);
        const bytes = new Uint8Array(raw.length);
        for (let i = 0; i < raw.length; i++) {
            bytes[i] = raw.charCodeAt(i);
        }
        return bytes;
    }

    function sameKey(subscription, key) {
        const current = subscription.options && subscription.options.applicationServerKey;
        if (!current) {
            return true;
        }
        const a = new Uint8Array(current);
        const b = keyBytes(key);
        return a.length === b.length && a.every(function (value, i) { return value === b[i]; });
    }

    function knownEndpoints() {
        return Array.prototype.map.call(document.querySelectorAll('[data-endpoint]'), function (el) {
            return el.getAttribute('data-endpoint');
        });
    }

    function initPush() {
        const card = document.getElementById('push-card');
        const status = document.getElementById('push-status');
        const enable = document.getElementById('push-enable');
        const test = document.getElementById('push-test');
        if (!card || !status) {
            return;
        }
        const key = card.getAttribute('data-vapid-key');

        function show(text, offerEnable) {
            status.textContent = text;
            enable.classList.toggle('d-none', !offerEnable);
        }

        if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) {
            show('Dieser Browser kann keine Push-Nachrichten empfangen. Auf dem iPhone geht es nur in der installierten App.', false);
        } else if (Notification.permission === 'denied') {
            show('Benachrichtigungen sind für Plan-Man blockiert. In den Website-Einstellungen des Browsers erlauben, dann neu laden.', false);
        } else {
            navigator.serviceWorker.register('/sw.js', { scope: '/' })
                .then(function () { return navigator.serviceWorker.ready; })
                .then(function (registration) { return registration.pushManager.getSubscription(); })
                .then(function (subscription) {
                    if (subscription && sameKey(subscription, key) && knownEndpoints().indexOf(subscription.endpoint) >= 0) {
                        show('Push ist auf diesem Gerät an.', false);
                    } else {
                        show('Push ist auf diesem Gerät aus.', true);
                    }
                })
                .catch(function (error) { show('Push-Status unbekannt: ' + error.message, true); });
        }

        enable.addEventListener('click', function () {
            enable.disabled = true;
            Notification.requestPermission().then(function (permission) {
                if (permission !== 'granted') {
                    throw new Error('Benachrichtigungen nicht erlaubt');
                }
                return navigator.serviceWorker.ready;
            }).then(function (registration) {
                return registration.pushManager.getSubscription().then(function (existing) {
                    if (existing && !sameKey(existing, key)) {
                        return existing.unsubscribe().then(function () { return null; });
                    }
                    return existing;
                }).then(function (existing) {
                    return existing || registration.pushManager.subscribe({
                        userVisibleOnly: true,
                        applicationServerKey: keyBytes(key)
                    });
                });
            }).then(function (subscription) {
                const body = subscription.toJSON();
                body.label = deviceLabel();
                return request('POST', '/api/push/subscriptions', body);
            }).then(function () {
                window.location.reload();
            }).catch(function (error) {
                enable.disabled = false;
                show('Einschalten fehlgeschlagen: ' + error.message, true);
            });
        });

        if (test) {
            test.addEventListener('click', function () {
                test.disabled = true;
                request('POST', '/api/push/test')
                    .then(function (response) { return response.json(); })
                    .then(function (result) {
                        status.textContent = result.delivered > 0
                            ? 'Test-Push an ' + result.delivered + (result.delivered === 1 ? ' Gerät' : ' Geräte') + ' gesendet.'
                            : 'Kein Gerät hat den Test-Push angenommen (siehe Fehler unten).';
                        setTimeout(function () { window.location.reload(); }, 4000);
                    })
                    .catch(function (error) {
                        test.disabled = false;
                        status.textContent = 'Test-Push fehlgeschlagen: ' + error.message;
                    });
            });
        }

        document.addEventListener('click', function (event) {
            const remove = event.target.closest('[data-push-remove]');
            if (!remove) {
                return;
            }
            const endpoint = remove.closest('[data-endpoint]').getAttribute('data-endpoint');
            request('DELETE', '/api/push/subscriptions/' + remove.getAttribute('data-push-remove'))
                .then(function () { return navigator.serviceWorker.ready; })
                .then(function (registration) { return registration.pushManager.getSubscription(); })
                .then(function (subscription) {
                    if (subscription && subscription.endpoint === endpoint) {
                        return subscription.unsubscribe();
                    }
                })
                .then(function () { window.location.reload(); })
                .catch(function (error) { alert('Entfernen fehlgeschlagen: ' + error.message); });
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (onPage()) {
            highlightTarget();
            initPush();
        }
    });
})();
