package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.health.HealthDigest;
import at.v3rtumnus.planman.dto.notification.DigestDTO;
import at.v3rtumnus.planman.service.DepotDigestService;
import at.v3rtumnus.planman.service.health.HealthDigestService;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private HealthDigestService healthDigestService;
    @MockitoBean
    private DepotDigestService depotDigestService;
    @MockitoBean
    private PlanManUserDetailsService userDetailsService;
    @MockitoBean
    private DataSource dataSource;
    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void validTokenWithoutLoginGetsTheDigest() throws Exception {
        DigestDTO digest = new DigestDTO(LocalDateTime.of(2026, 11, 9, 20, 0),
                Map.of("michael", new DigestDTO.UserDigest(1, List.of(new DigestDTO.DigestNotification(3L,
                        "Budget knapp", "Noch 240 €", "WARNING", "/expenses", LocalDateTime.of(2026, 11, 9, 19, 5))))));
        when(notificationService.digest()).thenReturn(digest);
        HealthDigest health = new HealthDigest(LocalDate.of(2026, 11, 8), false, 4, 1850, 2250, new BigDecimal("142"), 160,
                new BigDecimal("82.4"), null, new BigDecimal("77.0"), "Gestern 1.850 / 2.250 kcal.", null);
        when(healthDigestService.withHealth(digest)).thenReturn(new DigestDTO(digest.generatedAt(),
                Map.of("michael", new DigestDTO.UserDigest(1, digest.users().get("michael").notifications(), health))));
        when(depotDigestService.week()).thenReturn(Optional.of(new DigestDTO.DepotWeek(LocalDate.of(2026, 11, 3),
                LocalDate.of(2026, 11, 10), new BigDecimal("154058.05"), new BigDecimal("155661.57"), BigDecimal.ZERO,
                new BigDecimal("1603.52"), new BigDecimal("1.0"), "Depot seit 3. Nov.: +1,0 %")));

        mockMvc.perform(get("/api/digest").header("Authorization", "Bearer s3cret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.michael.unread").value(1))
                .andExpect(jsonPath("$.users.michael.notifications[0].title").value("Budget knapp"))
                .andExpect(jsonPath("$.users.michael.health.line").value("Gestern 1.850 / 2.250 kcal."))
                .andExpect(jsonPath("$.users.michael.health.date").value("2026-11-08"))
                .andExpect(jsonPath("$.depot.line").value("Depot seit 3. Nov.: +1,0 %"))
                .andExpect(jsonPath("$.depot.from").value("2026-11-03"));
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
        DigestApiController off = new DigestApiController(notificationService, healthDigestService, depotDigestService, "");

        assertThat(off.digest("Bearer ").getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(notificationService);
    }
}
