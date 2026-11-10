package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.notification.NotificationDTO;
import at.v3rtumnus.planman.dto.notification.NotificationHeaderDTO;
import at.v3rtumnus.planman.dto.notification.NotificationRuleDTO;
import at.v3rtumnus.planman.dto.notification.NotificationRuleUpdate;
import at.v3rtumnus.planman.entity.notification.NotificationSeverity;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import at.v3rtumnus.planman.service.notification.NotificationRuleService;
import at.v3rtumnus.planman.service.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationApiController.class)
@WithMockUser(username = "michael")
class NotificationApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private NotificationRuleService ruleService;
    @MockitoBean
    private PlanManUserDetailsService userDetailsService;
    @MockitoBean
    private DataSource dataSource;
    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void list_returnsTheUsersInbox() throws Exception {
        when(notificationService.inbox("michael")).thenReturn(List.of(new NotificationDTO(1L, "Budget knapp", "Noch 240 €",
                NotificationSeverity.WARNING, "/expenses", List.of(), LocalDateTime.of(2026, 11, 9, 20, 0), false)));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Budget knapp"))
                .andExpect(jsonPath("$[0].severity").value("WARNING"));
    }

    @Test
    void header_returnsCounts() throws Exception {
        when(notificationService.header("michael")).thenReturn(new NotificationHeaderDTO(2, List.of(), 1));

        mockMvc.perform(get("/api/notifications/header"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.pushDevices").value(1));
    }

    @Test
    void markRead_markAll_delete_action() throws Exception {
        mockMvc.perform(post("/api/notifications/1/read")).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/notifications/read-all")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/notifications/1")).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/notifications/1/actions/played")).andExpect(status().isNoContent());

        verify(notificationService).markRead(1L, "michael");
        verify(notificationService).markAllRead("michael");
        verify(notificationService).delete(1L, "michael");
        verify(notificationService).performAction(1L, "played", "michael");
    }

    @Test
    void errorsMapToStatusCodes() throws Exception {
        doThrow(new NoSuchElementException("x")).when(notificationService).markRead(1L, "michael");
        doThrow(new AccessDeniedException("x")).when(notificationService).markRead(2L, "michael");
        doThrow(new IllegalArgumentException("x")).when(notificationService).performAction(3L, "nope", "michael");
        doThrow(new IllegalStateException("x")).when(notificationService).performAction(4L, "played", "michael");

        mockMvc.perform(post("/api/notifications/1/read")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/notifications/2/read")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/notifications/3/actions/nope")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/notifications/4/actions/played")).andExpect(status().isConflict());
    }

    @Test
    void rules_listAndUpdate() throws Exception {
        when(ruleService.list()).thenReturn(List.of(new NotificationRuleDTO(4L, "Budget knapp", "BUDGET_NEAR_LIMIT", true,
                "Alle", List.of(), 0, 0)));

        mockMvc.perform(get("/api/notifications/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Budget knapp"));

        mockMvc.perform(put("/api/notifications/rules/4").contentType("application/json")
                        .content("{\"enabled\":false,\"parameters\":{\"thresholdAmount\":250}}"))
                .andExpect(status().isNoContent());
        verify(ruleService).update(4L, new NotificationRuleUpdate(false, java.util.Map.of("thresholdAmount", new java.math.BigDecimal("250"))));
    }

    @Test
    void rules_badParameterIs400() throws Exception {
        doThrow(new IllegalArgumentException("Unknown parameter")).when(ruleService).update(eq(4L), any());

        mockMvc.perform(put("/api/notifications/rules/4").contentType("application/json")
                        .content("{\"enabled\":true,\"parameters\":{\"x\":1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown parameter"));
    }
}
