package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.health.GoalForm;
import at.v3rtumnus.planman.dto.health.GoalProgress;
import at.v3rtumnus.planman.dto.health.MeasurementStatus;
import at.v3rtumnus.planman.dto.health.WeightTrend;
import at.v3rtumnus.planman.entity.health.*;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import at.v3rtumnus.planman.service.health.HealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
@WithMockUser
class HealthControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthService healthService;
    @MockitoBean
    private PlanManUserDetailsService userDetailsService;
    @MockitoBean
    private DataSource dataSource;
    @MockitoBean
    private CacheManager cacheManager;

    private HealthGoal goal;

    @BeforeEach
    void setUp() {
        goal = new HealthGoal();
        goal.setTitle("Fett verlieren + Kraft");
        goal.setValidFrom(TODAY.minusDays(7));
        goal.setKcalTarget(2000);
        goal.setProteinTargetG(160);
        goal.setFootballBonusKcal(250);
        goal.setFreeleticsBonusKcal(100);
        goal.setStepsTarget(8000);
        goal.setStartWeightKg(new BigDecimal("83.0"));
        goal.setTargetWeightKg(new BigDecimal("77.0"));
        goal.setTargetDate(LocalDate.of(2027, 2, 28));
        goal.setPushUpsTarget(25);

        HealthDay day = new HealthDay();
        day.setDate(TODAY);
        day.setKcalTarget(2000);
        day.setProteinTargetG(160);
        day.setStepsTarget(8000);

        WeightTrend trend = new WeightTrend(new BigDecimal("82.6"), new BigDecimal("-0.40"),
                new BigDecimal("82.4"), TODAY);

        when(healthService.today()).thenReturn(TODAY);
        when(healthService.currentGoal()).thenReturn(Optional.of(goal));
        when(healthService.goalHistory()).thenReturn(List.of(goal));
        when(healthService.weightTrend()).thenReturn(trend);
        when(healthService.goalProgress(any(), any())).thenReturn(Optional.of(
                new GoalProgress(new BigDecimal("82.6"), 7, new BigDecimal("-0.36"), new BigDecimal("-0.40"))));
        when(healthService.day(TODAY)).thenReturn(day);
        when(healthService.activePause()).thenReturn(Optional.empty());
        when(healthService.status(eq(MeasurementType.WAIST), any())).thenReturn(
                new MeasurementStatus(MeasurementType.WAIST, new BigDecimal("96.0"), new BigDecimal("95.0"),
                        TODAY.minusDays(20), true, null));
        when(healthService.status(eq(MeasurementType.PUSH_UPS), any())).thenReturn(
                new MeasurementStatus(MeasurementType.PUSH_UPS, null, new BigDecimal("12"), TODAY.minusDays(3), false, 25));
        when(healthService.goalFormForChange()).thenReturn(GoalForm.from(goal));
    }

    @Test
    void today_rendersTargetsTrendAndDueMeasurements() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(view().name("health/today"))
                .andExpect(content().string(containsString("2.000 kcal")))
                .andExpect(content().string(containsString("82,6 kg")))
                .andExpect(content().string(containsString("-0,4 kg / Woche")))
                .andExpect(content().string(containsString("Messen fällig")))
                .andExpect(content().string(containsString("bottom-nav")))
                .andExpect(content().string(containsString("manifest.webmanifest")));
    }

    @Test
    void today_withoutGoalAndWithPause_rendersHints() throws Exception {
        HealthPause pause = new HealthPause();
        pause.setStartDate(TODAY.minusDays(1));
        pause.setReason("krank");
        when(healthService.currentGoal()).thenReturn(Optional.empty());
        when(healthService.activePause()).thenReturn(Optional.of(pause));
        when(healthService.weightTrend()).thenReturn(WeightTrend.EMPTY);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Noch kein Ziel")))
                .andExpect(content().string(containsString("Pause aktiv")));
    }

    @Test
    void goal_rendersProgressMeasurementsAndHistory() throws Exception {
        mockMvc.perform(get("/health/goal"))
                .andExpect(status().isOk())
                .andExpect(view().name("health/goal"))
                .andExpect(model().attribute("editGoal", false))
                .andExpect(content().string(containsString("96,0 → 95,0 cm")))
                .andExpect(content().string(containsString("12 → Ziel 25")))
                .andExpect(content().string(containsString("Ziel ändern")));
    }

    @Test
    void goal_withoutGoal_opensForm() throws Exception {
        when(healthService.currentGoal()).thenReturn(Optional.empty());

        mockMvc.perform(get("/health/goal"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("editGoal", true));
    }

    @Test
    void changeGoal_valid_savesAndRedirects() throws Exception {
        mockMvc.perform(post("/health/goal")
                        .param("title", "Halten")
                        .param("kcalTarget", "2200")
                        .param("proteinTargetG", "150")
                        .param("footballBonusKcal", "250")
                        .param("freeleticsBonusKcal", "100")
                        .param("stepsTarget", "8000")
                        .param("startWeightKg", "77.0")
                        .param("targetWeightKg", "77.0")
                        .param("targetDate", "2027-06-30"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/health/goal"));

        verify(healthService).changeGoal(argThat(form -> form.getKcalTarget() == 2200 && "Halten".equals(form.getTitle())));
    }

    @Test
    void changeGoal_invalid_showsFormAgain() throws Exception {
        mockMvc.perform(post("/health/goal").param("title", "x").param("kcalTarget", "100"))
                .andExpect(status().isOk())
                .andExpect(view().name("health/goal"))
                .andExpect(model().attribute("editGoal", true))
                .andExpect(model().attributeExists("errorMessage"));

        verify(healthService, never()).changeGoal(any());
    }

    @Test
    void changeGoal_rejectedByService_showsMessage() throws Exception {
        when(healthService.changeGoal(any())).thenThrow(new IllegalArgumentException("Das Zieldatum muss in der Zukunft liegen"));

        mockMvc.perform(post("/health/goal")
                        .param("title", "Halten").param("kcalTarget", "2200").param("proteinTargetG", "150")
                        .param("footballBonusKcal", "250").param("freeleticsBonusKcal", "100")
                        .param("stepsTarget", "8000").param("startWeightKg", "77").param("targetWeightKg", "77")
                        .param("targetDate", "2026-01-01"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", "Das Zieldatum muss in der Zukunft liegen"));
    }

    @Test
    void pause_onAndOff() throws Exception {
        mockMvc.perform(post("/health/pause").param("active", "true").param("reason", "Urlaub"))
                .andExpect(redirectedUrl("/health/goal"));
        mockMvc.perform(post("/health/pause").param("active", "false"))
                .andExpect(redirectedUrl("/health/goal"));

        verify(healthService).startPause("Urlaub");
        verify(healthService).endPause();
    }

    @Test
    void measureForm_prefillsLatestValue() throws Exception {
        BodyMeasurement latest = new BodyMeasurement();
        latest.setMeasuredValue(new BigDecimal("95.0"));
        when(healthService.measurementOn(MeasurementType.WAIST, TODAY)).thenReturn(Optional.empty());
        when(healthService.latest(MeasurementType.WAIST)).thenReturn(Optional.of(latest));

        mockMvc.perform(get("/health/measure").param("type", "WAIST"))
                .andExpect(status().isOk())
                .andExpect(view().name("health/measure"))
                .andExpect(model().attribute("returnTo", "/health/goal"))
                .andExpect(content().string(containsString("Taille eintragen")))
                .andExpect(content().string(containsString("value=\"95.0\"")));
    }

    @Test
    void saveMeasurement_redirectsToAllowedPathOnly() throws Exception {
        mockMvc.perform(post("/health/measurements")
                        .param("type", "WEIGHT").param("date", "2026-11-10").param("value", "82.4")
                        .param("returnTo", "https://example.com"))
                .andExpect(redirectedUrl("/health"))
                .andExpect(flash().attribute("successMessage", "Gewicht gespeichert"));

        verify(healthService).saveMeasurement(MeasurementType.WEIGHT, TODAY, new BigDecimal("82.4"));
    }

    @Test
    void saveMeasurement_invalidOrRejected_showsError() throws Exception {
        mockMvc.perform(post("/health/measurements").param("type", "WEIGHT").param("returnTo", "/health/goal"))
                .andExpect(redirectedUrl("/health/goal"))
                .andExpect(flash().attributeExists("errorMessage"));

        when(healthService.saveMeasurement(any(), any(), any())).thenThrow(new IllegalArgumentException("zu viel"));
        mockMvc.perform(post("/health/measurements")
                        .param("type", "WEIGHT").param("date", "2026-11-10").param("value", "999"))
                .andExpect(flash().attribute("errorMessage", "zu viel"));
    }
}
