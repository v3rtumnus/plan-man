package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.notification.PushSubscriptionDTO;
import at.v3rtumnus.planman.dto.notification.PushSubscriptionRequest;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import at.v3rtumnus.planman.service.notification.NotificationService;
import at.v3rtumnus.planman.service.notification.PushSubscriptionService;
import at.v3rtumnus.planman.service.notification.WebPushService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PushApiController.class)
@WithMockUser(username = "michael")
class PushApiControllerTest {

    private static final String BODY = """
            {"endpoint":"https://fcm.googleapis.com/fcm/send/x","expirationTime":null,
             "keys":{"p256dh":"BAAA","auth":"AAAA"},"label":"Android · Chrome"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PushSubscriptionService subscriptionService;
    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private WebPushService webPush;
    @MockitoBean
    private PlanManUserDetailsService userDetailsService;
    @MockitoBean
    private DataSource dataSource;
    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void subscribe_storesTheBrowsersSubscription() throws Exception {
        when(webPush.isEnabled()).thenReturn(true);
        when(subscriptionService.subscribe(eq("michael"), any())).thenReturn(new PushSubscriptionDTO(5L,
                "Android · Chrome", "https://fcm.googleapis.com/fcm/send/x", LocalDateTime.of(2026, 11, 9, 20, 0),
                null, null, null));

        mockMvc.perform(post("/api/push/subscriptions").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));

        verify(subscriptionService).subscribe("michael", new PushSubscriptionRequest(
                "https://fcm.googleapis.com/fcm/send/x", new PushSubscriptionRequest.Keys("BAAA", "AAAA"), "Android · Chrome"));
    }

    @Test
    void subscribe_unavailableWhenPushIsOff() throws Exception {
        when(webPush.isEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/push/subscriptions").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(subscriptionService);
    }

    @Test
    void subscribe_invalidEndpointIsBadRequest() throws Exception {
        when(webPush.isEnabled()).thenReturn(true);
        when(subscriptionService.subscribe(eq("michael"), any())).thenThrow(new IllegalArgumentException("unknown host"));

        mockMvc.perform(post("/api/push/subscriptions").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unknown host"));
    }

    @Test
    void devices_unsubscribe_test() throws Exception {
        when(subscriptionService.devices("michael")).thenReturn(List.of());
        when(notificationService.sendTest("michael")).thenReturn(1);

        mockMvc.perform(get("/api/push/subscriptions")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mockMvc.perform(delete("/api/push/subscriptions/5")).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/push/test")).andExpect(status().isOk()).andExpect(jsonPath("$.delivered").value(1));

        verify(subscriptionService).unsubscribe(5L, "michael");
    }
}
