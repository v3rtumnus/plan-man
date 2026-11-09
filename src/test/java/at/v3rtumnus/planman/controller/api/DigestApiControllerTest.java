package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.notification.DigestDTO;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import at.v3rtumnus.planman.service.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** n8n calls without a login, so the endpoint is open in Spring Security and guarded by the token. */
@WebMvcTest(DigestApiController.class)
@TestPropertySource(properties = "planman.digest.token=s3cret")
class DigestApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private PlanManUserDetailsService userDetailsService;
    @MockitoBean
    private DataSource dataSource;
    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void validTokenWithoutLoginGetsTheDigest() throws Exception {
        when(notificationService.digest()).thenReturn(new DigestDTO(LocalDateTime.of(2026, 11, 9, 20, 0),
                Map.of("michael", new DigestDTO.UserDigest(1, List.of(new DigestDTO.DigestNotification(3L,
                        "Budget knapp", "Noch 240 €", "WARNING", "/expenses", LocalDateTime.of(2026, 11, 9, 19, 5)))))));

        mockMvc.perform(get("/api/digest").header("Authorization", "Bearer s3cret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.michael.unread").value(1))
                .andExpect(jsonPath("$.users.michael.notifications[0].title").value("Budget knapp"));
    }

    @Test
    void wrongOrMissingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/digest").header("Authorization", "Bearer nope")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/digest").header("Authorization", "Basic s3cret")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/digest")).andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationService);
    }

    @Test
    void emptyTokenTurnsTheEndpointOff() {
        DigestApiController off = new DigestApiController(notificationService, "");

        assertThat(off.digest("Bearer ").getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(notificationService);
    }
}
