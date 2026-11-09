package at.v3rtumnus.planman.service.notification;

import at.v3rtumnus.planman.dao.PushSubscriptionRepository;
import at.v3rtumnus.planman.dao.UserProfileRepository;
import at.v3rtumnus.planman.dto.notification.PushSubscriptionDTO;
import at.v3rtumnus.planman.dto.notification.PushSubscriptionRequest;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.notification.PushSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PushSubscriptionServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T19:00:00Z"), ZoneId.of("Europe/Vienna"));
    private static final String FCM = "https://fcm.googleapis.com/fcm/send/abc:def";

    private final PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
    private final UserProfileRepository users = mock(UserProfileRepository.class);
    private final PushSubscriptionService service = new PushSubscriptionService(repository, users, CLOCK);
    private final PushTestKeys browser = PushTestKeys.generate();
    private final UserProfile michael = new UserProfile();

    @BeforeEach
    void setUp() {
        michael.setUsername("michael");
        when(users.findByUsername("michael")).thenReturn(Optional.of(michael));
        when(repository.save(any(PushSubscription.class))).thenAnswer(inv -> {
            PushSubscription s = inv.getArgument(0);
            s.setId(5L);
            return s;
        });
    }

    private PushSubscriptionRequest request(String endpoint, String label) {
        return new PushSubscriptionRequest(endpoint,
                new PushSubscriptionRequest.Keys(browser.publicB64, WebPushCrypto.b64(new byte[16])), label);
    }

    @Test
    void subscribe_storesNewDevice() {
        when(repository.findByEndpoint(FCM)).thenReturn(Optional.empty());

        PushSubscriptionDTO dto = service.subscribe("michael", request(FCM, " Android · Chrome (App) "));

        assertThat(dto.id()).isEqualTo(5L);
        assertThat(dto.deviceLabel()).isEqualTo("Android · Chrome (App)");
        assertThat(dto.createdAt()).isEqualTo(LocalDateTime.now(CLOCK));
    }

    @Test
    void subscribe_refreshesKnownEndpointAndClearsError() {
        PushSubscription existing = new PushSubscription();
        existing.setEndpoint(FCM);
        existing.setLastError("HTTP 500");
        existing.setCreatedAt(LocalDateTime.of(2026, 11, 1, 8, 0));
        when(repository.findByEndpoint(FCM)).thenReturn(Optional.of(existing));

        PushSubscriptionDTO dto = service.subscribe("michael", request(FCM, null));

        assertThat(existing.getUser()).isSameAs(michael);
        assertThat(existing.getLastError()).isNull();
        assertThat(dto.deviceLabel()).isEqualTo("Gerät");
        assertThat(dto.createdAt()).isEqualTo(LocalDateTime.of(2026, 11, 1, 8, 0));
    }

    @Test
    void subscribe_cutsLongLabels() {
        when(repository.findByEndpoint(FCM)).thenReturn(Optional.empty());

        assertThat(service.subscribe("michael", request(FCM, "x".repeat(300))).deviceLabel()).hasSize(255);
    }

    @Test
    void validate_acceptsTheBrowsersPushServices() {
        for (String endpoint : List.of(FCM, "https://updates.push.services.mozilla.com/wpush/v2/x",
                "https://wns2-par02p.notify.windows.com/w/?token=x", "https://web.push.apple.com/abc")) {
            PushSubscriptionService.validate(request(endpoint, "x"));
        }
    }

    @Test
    void validate_rejectsOtherHostsAndPlainHttp() {
        for (String endpoint : List.of("https://grafana:3000/api", "http://fcm.googleapis.com/fcm/send/x",
                "https://evilfcm.googleapis.com.attacker.io/x", "https://notfcm.googleapis.com.evil/x", "::nonsense")) {
            assertThatThrownBy(() -> PushSubscriptionService.validate(request(endpoint, "x")))
                    .as(endpoint).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void validate_rejectsIncompleteOrInvalidKeys() {
        assertThatThrownBy(() -> PushSubscriptionService.validate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushSubscriptionService.validate(new PushSubscriptionRequest(FCM, null, "x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushSubscriptionService.validate(new PushSubscriptionRequest(FCM,
                new PushSubscriptionRequest.Keys("AAAA", WebPushCrypto.b64(new byte[16])), "x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushSubscriptionService.validate(new PushSubscriptionRequest(FCM,
                new PushSubscriptionRequest.Keys(browser.publicB64, WebPushCrypto.b64(new byte[8])), "x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushSubscriptionService.validate(request(FCM + "x".repeat(500), "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void devices_listsTheUsersSubscriptions() {
        PushSubscription s = new PushSubscription();
        s.setId(3L);
        s.setDeviceLabel("Android · Chrome");
        s.setEndpoint(FCM);
        when(repository.findByUserUsernameOrderByCreatedAtDesc("michael")).thenReturn(List.of(s));

        assertThat(service.devices("michael")).extracting(PushSubscriptionDTO::id).containsExactly(3L);
    }

    @Test
    void unsubscribe_onlyOwnDevices() {
        PushSubscription own = new PushSubscription();
        own.setUser(michael);
        UserProfile aga = new UserProfile();
        aga.setUsername("Aga");
        PushSubscription foreign = new PushSubscription();
        foreign.setUser(aga);
        when(repository.findById(1L)).thenReturn(Optional.of(own));
        when(repository.findById(2L)).thenReturn(Optional.of(foreign));
        when(repository.findById(3L)).thenReturn(Optional.empty());

        service.unsubscribe(1L, "michael");

        verify(repository).delete(own);
        assertThatThrownBy(() -> service.unsubscribe(2L, "michael")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.unsubscribe(3L, "michael")).isInstanceOf(NoSuchElementException.class);
    }
}
