package at.v3rtumnus.planman.service.notification;

import at.v3rtumnus.planman.dao.PushSubscriptionRepository;
import at.v3rtumnus.planman.dao.UserProfileRepository;
import at.v3rtumnus.planman.dto.notification.PushSubscriptionDTO;
import at.v3rtumnus.planman.dto.notification.PushSubscriptionRequest;
import at.v3rtumnus.planman.entity.notification.PushSubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

/** The devices a user has turned push on for. */
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    /**
     * plan-man posts to the endpoint the browser hands over, so only the browsers' push
     * services are accepted (no requests to arbitrary hosts from inside dockernet).
     */
    static final List<String> PUSH_SERVICE_HOSTS = List.of(
            "fcm.googleapis.com",          // Chrome, Edge on Android, Samsung Internet
            "push.services.mozilla.com",   // Firefox
            "notify.windows.com",          // Edge on Windows
            "push.apple.com");             // Safari

    private final PushSubscriptionRepository subscriptions;
    private final UserProfileRepository users;
    private final Clock clock;

    public List<PushSubscriptionDTO> devices(String username) {
        return subscriptions.findByUserUsernameOrderByCreatedAtDesc(username).stream()
                .map(s -> new PushSubscriptionDTO(s.getId(), s.getDeviceLabel(), s.getEndpoint(), s.getCreatedAt(),
                        s.getLastSuccessAt(), s.getLastFailureAt(), s.getLastError()))
                .toList();
    }

    /** Stores or refreshes the subscription; an endpoint seen before moves to this user. */
    @Transactional
    public PushSubscriptionDTO subscribe(String username, PushSubscriptionRequest request) {
        validate(request);
        PushSubscription subscription = subscriptions.findByEndpoint(request.endpoint()).orElseGet(() -> {
            PushSubscription created = new PushSubscription();
            created.setEndpoint(request.endpoint());
            created.setCreatedAt(LocalDateTime.now(clock));
            return created;
        });
        subscription.setUser(users.findByUsername(username).orElseThrow());
        subscription.setP256dh(request.keys().p256dh());
        subscription.setAuth(request.keys().auth());
        String label = request.label() == null || request.label().isBlank() ? "Gerät" : request.label().strip();
        subscription.setDeviceLabel(label.length() > 255 ? label.substring(0, 255) : label);
        subscription.setLastError(null);
        subscription = subscriptions.save(subscription);
        return new PushSubscriptionDTO(subscription.getId(), subscription.getDeviceLabel(), subscription.getEndpoint(),
                subscription.getCreatedAt(), subscription.getLastSuccessAt(), subscription.getLastFailureAt(), null);
    }

    @Transactional
    public void unsubscribe(Long id, String username) {
        PushSubscription subscription = subscriptions.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Push subscription not found: " + id));
        if (!subscription.getUser().getUsername().equals(username)) {
            throw new AccessDeniedException("Push subscription " + id + " does not belong to " + username);
        }
        subscriptions.delete(subscription);
    }

    static void validate(PushSubscriptionRequest request) {
        if (request == null || request.endpoint() == null || request.keys() == null
                || request.keys().p256dh() == null || request.keys().auth() == null) {
            throw new IllegalArgumentException("Incomplete push subscription");
        }
        if (request.endpoint().length() > 500) {
            throw new IllegalArgumentException("Push endpoint too long");
        }
        URI uri;
        try {
            uri = URI.create(request.endpoint());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid push endpoint");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean known = PUSH_SERVICE_HOSTS.stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h));
        if (!"https".equals(uri.getScheme()) || !known) {
            throw new IllegalArgumentException("Push endpoint is not a known push service: " + host);
        }
        try {
            WebPushCrypto.publicKey(WebPushCrypto.decode(request.keys().p256dh()));
            if (WebPushCrypto.decode(request.keys().auth()).length != 16) {
                throw new IllegalArgumentException("Push auth secret must be 16 bytes");
            }
        } catch (java.security.GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid push keys: " + e.getMessage());
        }
    }
}
