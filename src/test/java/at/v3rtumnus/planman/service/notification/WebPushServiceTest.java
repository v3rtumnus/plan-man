package at.v3rtumnus.planman.service.notification;

import at.v3rtumnus.planman.dao.PushSubscriptionRepository;
import at.v3rtumnus.planman.dto.notification.NotificationAction;
import at.v3rtumnus.planman.entity.notification.PushSubscription;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@WireMockTest
class WebPushServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T19:00:00Z"), ZoneId.of("Europe/Vienna"));

    private final PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final PushTestKeys server = PushTestKeys.generate();
    private final PushTestKeys browser = PushTestKeys.generate();
    private WebPushService service;

    @BeforeEach
    void setUp() {
        service = new WebPushService(repository, CLOCK, meters, server.publicB64, server.privateB64,
                "https://plan-man.example", HttpClient.newHttpClient());
    }

    private PushSubscription subscription(WireMockRuntimeInfo wm, String path) {
        PushSubscription s = new PushSubscription();
        s.setId(7L);
        s.setDeviceLabel("Android · Chrome");
        s.setEndpoint(wm.getHttpBaseUrl() + path);
        s.setP256dh(browser.publicB64);
        s.setAuth(WebPushCrypto.b64(new byte[16]));
        return s;
    }

    private static WebPushService.PushMessage message(boolean urgent) {
        return new WebPushService.PushMessage(42L, "Test-Push", "Hallo", "/notifications",
                List.of(new NotificationAction("read", "Gelesen")), urgent);
    }

    @Test
    void send_postsEncryptedMessageWithVapidAndRecordsSuccess(WireMockRuntimeInfo wm) {
        stubFor(post("/push/ok").willReturn(aResponse().withStatus(201)));
        PushSubscription subscription = subscription(wm, "/push/ok");
        when(repository.findByUserUsernameOrderByCreatedAtDesc("michael")).thenReturn(List.of(subscription));

        int delivered = service.send("michael", message(true));

        assertThat(delivered).isEqualTo(1);
        verify(postRequestedFor(urlEqualTo("/push/ok"))
                .withHeader("Content-Encoding", equalTo("aes128gcm"))
                .withHeader("TTL", equalTo("86400"))
                .withHeader("Urgency", equalTo("high"))
                .withHeader("Authorization", matching("vapid t=[\\w-]+\\.[\\w-]+\\.[\\w-]+, k=" + server.publicB64)));
        assertThat(subscription.getLastSuccessAt()).isEqualTo(LocalDateTime.now(CLOCK));
        verify(repository).save(subscription);
        assertThat(meters.counter("planman.push.sent").count()).isEqualTo(1.0);
    }

    @Test
    void send_normalUrgencyForNonUrgentMessages(WireMockRuntimeInfo wm) {
        stubFor(post("/push/ok").willReturn(aResponse().withStatus(201)));
        when(repository.findByUserUsernameOrderByCreatedAtDesc("michael")).thenReturn(List.of(subscription(wm, "/push/ok")));

        service.send("michael", message(false));

        verify(postRequestedFor(urlEqualTo("/push/ok")).withHeader("Urgency", equalTo("normal")));
    }

    @Test
    void send_deletesSubscriptionThePushServiceNoLongerKnows(WireMockRuntimeInfo wm) {
        stubFor(post("/push/gone").willReturn(aResponse().withStatus(410)));
        PushSubscription subscription = subscription(wm, "/push/gone");
        when(repository.findByUserUsernameOrderByCreatedAtDesc("michael")).thenReturn(List.of(subscription));

        assertThat(service.send("michael", message(false))).isZero();

        verify(repository).delete(subscription);
        assertThat(meters.counter("planman.push.failed").count()).isEqualTo(1.0);
    }

    @Test
    void send_recordsOtherErrorsOnTheSubscription(WireMockRuntimeInfo wm) {
        stubFor(post("/push/broken").willReturn(aResponse().withStatus(500).withBody("boom")));
        PushSubscription subscription = subscription(wm, "/push/broken");
        when(repository.findByUserUsernameOrderByCreatedAtDesc("michael")).thenReturn(List.of(subscription));

        assertThat(service.send("michael", message(false))).isZero();

        assertThat(subscription.getLastError()).isEqualTo("HTTP 500: boom");
        assertThat(subscription.getLastFailureAt()).isEqualTo(LocalDateTime.now(CLOCK));
        verify(repository, never()).delete(any());
    }

    @Test
    void send_recordsConnectionErrors() {
        PushSubscription subscription = new PushSubscription();
        subscription.setEndpoint("http://localhost:1/push");
        subscription.setP256dh(browser.publicB64);
        subscription.setAuth(WebPushCrypto.b64(new byte[16]));
        when(repository.findByUserUsernameOrderByCreatedAtDesc("michael")).thenReturn(List.of(subscription));

        assertThat(service.send("michael", message(false))).isZero();

        assertThat(subscription.getLastError()).isNotBlank();
        assertThat(meters.counter("planman.push.failed").count()).isEqualTo(1.0);
    }

    @Test
    void disabledWithoutKeys() {
        WebPushService off = new WebPushService(repository, CLOCK, meters, "", "", "https://x", HttpClient.newHttpClient());

        assertThat(off.isEnabled()).isFalse();
        assertThat(off.publicKey()).isNull();
        assertThat(off.send("michael", message(true))).isZero();
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMismatchedKeyPair() {
        PushTestKeys other = PushTestKeys.generate();

        assertThatThrownBy(() -> new WebPushService(repository, CLOCK, meters, other.publicB64, server.privateB64,
                "https://x", HttpClient.newHttpClient())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void springConstructorBuildsItsOwnClient() {
        WebPushService fromSpring = new WebPushService(repository, CLOCK, meters, server.publicB64, server.privateB64,
                "https://x");

        assertThat(fromSpring.isEnabled()).isTrue();
        assertThat(fromSpring.publicKey()).isEqualTo(server.publicB64);
    }

    @Test
    void payload_cutsLongBodiesAndMapsActions() {
        String longBody = "x".repeat(WebPushService.MAX_BODY_CHARS + 50);

        String json = new String(WebPushService.payload(new WebPushService.PushMessage(1L, "T", longBody, null,
                List.of(new NotificationAction("played", "Gespielt")), false)), StandardCharsets.UTF_8);

        assertThat(json).contains("\"action\":\"played\"").contains("\"title\":\"Gespielt\"").contains("…");
        assertThat(json.length()).isLessThan(WebPushService.MAX_BODY_CHARS + 200);
        assertThat(new String(WebPushService.payload(new WebPushService.PushMessage(1L, "T", null, null, null, false)),
                StandardCharsets.UTF_8)).contains("\"body\":\"\"").contains("\"actions\":[]");
    }
}
