package at.v3rtumnus.planman.service.notification;

import at.v3rtumnus.planman.dao.PushSubscriptionRepository;
import at.v3rtumnus.planman.dto.notification.NotificationAction;
import at.v3rtumnus.planman.entity.notification.PushSubscription;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends Web Push messages to a user's subscribed devices. The content is end-to-end encrypted;
 * the push service (Google's for Chrome) only relays it, so pushes arrive without the VPN.
 */
@Service
@Slf4j
public class WebPushService {

    /** Time a push service keeps an undelivered message (phone offline). */
    static final Duration TTL = Duration.ofDays(1);
    /** Below the 4096-byte record minus overhead; long messages are cut. */
    static final int MAX_BODY_CHARS = 1000;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PushSubscriptionRepository subscriptions;
    private final HttpClient http;
    private final Clock clock;
    private final Counter sent;
    private final Counter failed;
    private final String publicKey;
    private final PrivateKey privateKey;
    private final String subject;

    @Autowired
    public WebPushService(PushSubscriptionRepository subscriptions, Clock clock, MeterRegistry meters,
                          @Value("${planman.push.vapid-public-key:}") String publicKey,
                          @Value("${planman.push.vapid-private-key:}") String privateKey,
                          @Value("${planman.push.subject:https://plan-man.altenburger.io}") String subject) {
        this(subscriptions, clock, meters, publicKey, privateKey, subject,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    WebPushService(PushSubscriptionRepository subscriptions, Clock clock, MeterRegistry meters,
                   String publicKey, String privateKey, String subject, HttpClient http) {
        this.subscriptions = subscriptions;
        this.clock = clock;
        this.http = http;
        this.subject = subject;
        this.sent = Counter.builder("planman.push.sent").description("Web Push messages accepted by the push service")
                .register(meters);
        this.failed = Counter.builder("planman.push.failed").description("Web Push messages that could not be delivered")
                .register(meters);
        if (publicKey.isBlank() || privateKey.isBlank()) {
            log.info("Web Push off: no VAPID key pair configured");
            this.publicKey = null;
            this.privateKey = null;
        } else {
            PrivateKey key = WebPushCrypto.privateKey(privateKey);
            if (!WebPushCrypto.keysMatch(publicKey, key)) {
                throw new IllegalStateException("VAPID public and private key do not belong together");
            }
            this.publicKey = publicKey;
            this.privateKey = key;
        }
    }

    public boolean isEnabled() {
        return privateKey != null;
    }

    /** The application server key the browser subscribes with (null when push is off). */
    public String publicKey() {
        return publicKey;
    }

    /** A push as the service worker receives it. */
    public record PushMessage(Long id, String title, String body, String url, List<NotificationAction> actions,
                              boolean urgent) {
    }

    /** Sends to every device of the user. Returns how many push services accepted the message. */
    public int send(String username, PushMessage message) {
        if (!isEnabled()) {
            return 0;
        }
        byte[] payload = payload(message);
        int delivered = 0;
        for (PushSubscription subscription : subscriptions.findByUserUsernameOrderByCreatedAtDesc(username)) {
            if (sendTo(subscription, payload, message.urgent())) {
                delivered++;
            }
        }
        return delivered;
    }

    boolean sendTo(PushSubscription subscription, byte[] payload, boolean urgent) {
        try {
            URI endpoint = URI.create(subscription.getEndpoint());
            String audience = endpoint.getScheme() + "://" + endpoint.getAuthority();
            long expires = clock.instant().plus(Duration.ofHours(12)).getEpochSecond();
            byte[] body = WebPushCrypto.encrypt(payload, subscription.getP256dh(), subscription.getAuth());

            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(15))
                    .header("TTL", String.valueOf(TTL.toSeconds()))
                    .header("Urgency", urgent ? "high" : "normal")
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Authorization", WebPushCrypto.vapidAuthorization(audience, subject, expires, publicKey, privateKey))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status >= 200 && status < 300) {
                subscription.setLastSuccessAt(LocalDateTime.now(clock));
                subscriptions.save(subscription);
                sent.increment();
                return true;
            }
            failed.increment();
            if (status == 404 || status == 410) {
                // The browser dropped the subscription (app uninstalled, permission revoked).
                log.info("Push subscription {} ({}) is gone ({}), deleting it", subscription.getId(),
                        subscription.getDeviceLabel(), status);
                subscriptions.delete(subscription);
            } else {
                recordFailure(subscription, "HTTP " + status + ": " + response.body());
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failed.increment();
            recordFailure(subscription, e.toString());
            return false;
        } catch (Exception e) {
            failed.increment();
            recordFailure(subscription, e.toString());
            return false;
        }
    }

    private void recordFailure(PushSubscription subscription, String error) {
        log.warn("Push to subscription {} ({}) failed: {}", subscription.getId(), subscription.getDeviceLabel(), error);
        subscription.setLastFailureAt(LocalDateTime.now(clock));
        subscription.setLastError(error.length() > 500 ? error.substring(0, 500) : error);
        subscriptions.save(subscription);
    }

    static byte[] payload(PushMessage message) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", message.id());
        json.put("title", message.title());
        String body = message.body() == null ? "" : message.body();
        json.put("body", body.length() > MAX_BODY_CHARS ? body.substring(0, MAX_BODY_CHARS - 1) + "…" : body);
        json.put("url", message.url());
        json.put("actions", message.actions() == null ? List.of() : message.actions().stream()
                .map(a -> Map.of("action", a.key(), "title", a.label()))
                .toList());
        return JSON.writeValueAsString(json).getBytes(StandardCharsets.UTF_8);
    }
}
