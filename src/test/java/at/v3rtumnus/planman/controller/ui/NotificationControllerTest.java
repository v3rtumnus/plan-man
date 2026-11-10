package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.notification.NotificationAction;
import at.v3rtumnus.planman.dto.notification.NotificationDTO;
import at.v3rtumnus.planman.dto.notification.NotificationHeaderDTO;
import at.v3rtumnus.planman.dto.notification.NotificationRuleDTO;
import at.v3rtumnus.planman.dto.notification.PushSubscriptionDTO;
import at.v3rtumnus.planman.entity.notification.NotificationSeverity;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import at.v3rtumnus.planman.service.notification.NotificationRuleService;
import at.v3rtumnus.planman.service.notification.NotificationService;
import at.v3rtumnus.planman.service.notification.PushSubscriptionService;
import at.v3rtumnus.planman.service.notification.WebPushService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@WithMockUser(username = "michael")
class NotificationControllerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 9, 20, 15);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private NotificationRuleService ruleService;
    @MockitoBean
    private PushSubscriptionService subscriptionService;
    @MockitoBean
    private WebPushService webPush;
    @MockitoBean
    private PlanManUserDetailsService userDetailsService;
    @MockitoBean
    private DataSource dataSource;
    @MockitoBean
    private CacheManager cacheManager;

    private static NotificationDTO unread() {
        return new NotificationDTO(7L, "Fußball heute?", "Hast du gespielt?", NotificationSeverity.INFO, "/health",
                List.of(new NotificationAction("played", "Gespielt"), new NotificationAction("cancelled", "Fällt aus")),
                AT, false);
    }

    @Test
    void overview_rendersInboxBellAndDevices() throws Exception {
        NotificationDTO read = new NotificationDTO(6L, "Test-Push", "Push funktioniert.", NotificationSeverity.WARNING,
                "/notifications", List.of(), AT.minusDays(1), true);
        when(notificationService.inbox("michael")).thenReturn(List.of(unread(), read));
        when(notificationService.header("michael")).thenReturn(new NotificationHeaderDTO(1, List.of(unread()), 1));
        when(subscriptionService.devices("michael")).thenReturn(List.of(new PushSubscriptionDTO(3L, "Android · Chrome (App)",
                "https://fcm.googleapis.com/fcm/send/x", AT, AT, AT, "HTTP 500: boom")));
        when(webPush.publicKey()).thenReturn("BPUBLIC");

        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(view().name("notifications/overview"))
                .andExpect(content().string(containsString("data-notification-action=\"played\"")))
                .andExpect(content().string(containsString("Fällt aus")))
                .andExpect(content().string(containsString("Alle gelesen")))
                .andExpect(content().string(containsString("id=\"notification-badge\"")))
                .andExpect(content().string(containsString("1 neue Benachrichtigung")))
                .andExpect(content().string(containsString("data-vapid-key=\"BPUBLIC\"")))
                .andExpect(content().string(containsString("Android · Chrome (App)")))
                .andExpect(content().string(containsString("Fehler 09.11. 20:15: HTTP 500: boom")))
                .andExpect(content().string(not(containsString("Push aus"))));
    }

    @Test
    void overview_emptyWithPushOff() throws Exception {
        when(notificationService.inbox("michael")).thenReturn(List.of());
        when(notificationService.header("michael")).thenReturn(NotificationHeaderDTO.empty());
        when(subscriptionService.devices("michael")).thenReturn(List.of());
        when(webPush.publicKey()).thenReturn(null);

        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Keine Benachrichtigungen.")))
                .andExpect(content().string(containsString("Keine neuen Benachrichtigungen")))
                .andExpect(content().string(containsString("Push ist am Server nicht eingerichtet.")))
                .andExpect(content().string(containsString("Push aus")))
                .andExpect(content().string(not(containsString("Alle gelesen"))))
                .andExpect(content().string(not(containsString("notification-badge"))));
    }

    @Test
    void rules_rendersThresholdsAndCounts() throws Exception {
        when(notificationService.header("michael")).thenReturn(NotificationHeaderDTO.empty());
        when(ruleService.list()).thenReturn(List.of(new NotificationRuleDTO(4L, "Budget knapp", "BUDGET_NEAR_LIMIT", true,
                "Alle", List.of(new NotificationRuleDTO.Parameter("thresholdAmount", "Schwelle (€)", new java.math.BigDecimal("300.00"))),
                2, 1)));

        mockMvc.perform(get("/notifications/rules"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Budget knapp")))
                .andExpect(content().string(containsString("Schwelle (€)")))
                .andExpect(content().string(containsString("value=\"300\"")))
                .andExpect(content().string(containsString("Letzte 30 Tage: 2 gemeldet, 1 gelesen")))
                .andExpect(content().string(containsString("data-rule-id=\"4\"")));
    }
}
