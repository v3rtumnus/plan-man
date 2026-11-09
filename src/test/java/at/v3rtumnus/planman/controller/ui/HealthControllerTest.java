package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.health.GoalForm;
import at.v3rtumnus.planman.dto.health.GoalProgress;
import at.v3rtumnus.planman.dto.health.MeasurementStatus;
import at.v3rtumnus.planman.dto.health.WeightTrend;
import at.v3rtumnus.planman.entity.health.*;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import at.v3rtumnus.planman.service.health.HealthService;
import at.v3rtumnus.planman.service.health.MealService;
import at.v3rtumnus.planman.service.health.TrainingService;
import at.v3rtumnus.planman.service.health.HealthHistoryService;
import at.v3rtumnus.planman.service.health.WeeklyReviewService;
import at.v3rtumnus.planman.dto.health.HealthChart;
import at.v3rtumnus.planman.dto.health.FootballState;
import at.v3rtumnus.planman.dto.health.NutritionDay;
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
import static org.hamcrest.Matchers.not;
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
    private MealService mealService;
    @MockitoBean
    private TrainingService trainingService;
    @MockitoBean
    private HealthHistoryService historyService;
    @MockitoBean
    private WeeklyReviewService reviewService;
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

        Meal lunch = new Meal();
        lunch.setId(4L);
        lunch.setSlot(MealSlot.LUNCH);
        lunch.setTitle("Putenschnitzel mit Erdäpfelsalat");
        lunch.setKcal(720);
        lunch.setProteinG(new BigDecimal("54.0"));
        when(mealService.nutritionDay(TODAY)).thenReturn(
                new NutritionDay(2000, 250, 160, 720, new BigDecimal("54.0"), List.of(lunch)));
        when(mealService.slotNow()).thenReturn(MealSlot.LUNCH);
        when(trainingService.footballState(TODAY)).thenReturn(FootballState.OPEN);
        when(trainingService.footballMinutes()).thenReturn(70);
        when(historyService.chart(any())).thenReturn(new HealthChart(
                List.of(new HealthChart.Point("2026-11-09", new BigDecimal("82.4"))),
                List.of(new HealthChart.Point("2026-11-09", new BigDecimal("82.6"))),
                List.of(), List.of(), new BigDecimal("77.0"), 25, "2026-11-03", TODAY.toString()));
    }

    @Test
    void today_pastDay_showsThatDayWithPagerAndTraining() throws Exception {
        LocalDate past = TODAY.minusDays(2);
        HealthDay day = new HealthDay();
        day.setDate(past);
        day.setKcalTarget(2000);
        day.setProteinTargetG(160);
        when(healthService.day(past)).thenReturn(day);
        when(mealService.nutritionDay(past)).thenReturn(new NutritionDay(2000, 0, 160, 0, BigDecimal.ZERO, List.of()));
        Workout workout = new Workout();
        workout.setId(8L);
        workout.setType(WorkoutType.FREELETICS);
        workout.setIntensity(Intensity.HARD);
        workout.setDurationMin(30);
        workout.setBonusKcal(330);
        workout.setDoneOn(past);
        when(trainingService.workoutsOn(past)).thenReturn(List.of(workout));

        mockMvc.perform(get("/health").param("date", past.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sonntag, 8. November")))
                .andExpect(content().string(containsString("zurück zu heute")))
                .andExpect(content().string(containsString("/health?date=" + past.minusDays(1))))
                .andExpect(content().string(containsString("/health?date=" + past.plusDays(1))))
                .andExpect(content().string(containsString("Freeletics · 30 min · hart")))
                .andExpect(content().string(containsString("/health/training?date=" + past)))
                .andExpect(content().string(containsString("Gewicht an diesem Tag")))
                .andExpect(content().string(not(containsString("Fußball heute?"))))
                .andExpect(content().string(not(containsString("Messen fällig"))));
        verify(trainingService, never()).footballState(past);
    }

    @Test
    void today_futureDate_showsToday() throws Exception {
        mockMvc.perform(get("/health").param("date", TODAY.plusDays(5).toString()))
                .andExpect(content().string(containsString("Fußball heute?")))
                .andExpect(content().string(not(containsString("zurück zu heute"))));
    }

    @Test
    void saveMeasurement_forPastDay_goesBackToThatDay() throws Exception {
        LocalDate past = TODAY.minusDays(1);
        mockMvc.perform(post("/health/measurements").param("type", "WEIGHT").param("date", past.toString())
                        .param("value", "82.1").param("returnTo", "/health"))
                .andExpect(redirectedUrl("/health?date=" + past));
    }

    @Test
    void goal_rendersChartData() throws Exception {
        mockMvc.perform(get("/health/goal"))
                .andExpect(content().string(containsString("id=\"chart-weight\"")))
                .andExpect(content().string(containsString("\"targetKg\":77.0")))
                .andExpect(content().string(containsString("health-charts.js")));
    }

    @Test
    void goal_fromReview_prefillsProposalAndSavingAcceptsIt() throws Exception {
        WeeklyReview review = new WeeklyReview();
        review.setId(12L);
        review.setWeekStart(LocalDate.of(2026, 11, 2));
        review.setProposedKcalTarget(1850);
        review.setProposedProteinTargetG(170);
        review.setProposedTargetDate(LocalDate.of(2027, 4, 30));
        when(reviewService.find("user", 12L)).thenReturn(Optional.of(review));

        mockMvc.perform(get("/health/goal").param("review", "12"))
                .andExpect(content().string(containsString("Vorschlag aus dem Ziel-Check")))
                .andExpect(content().string(containsString("value=\"1850\"")))
                .andExpect(content().string(containsString("value=\"170\"")))
                .andExpect(content().string(containsString("value=\"2027-04-30\"")))
                .andExpect(content().string(containsString("name=\"reviewId\" value=\"12\"")));

        mockMvc.perform(post("/health/goal").param("title", "Fett verlieren + Kraft").param("kcalTarget", "1850")
                        .param("proteinTargetG", "170").param("footballBonusKcal", "250").param("stepsTarget", "6000")
                        .param("startWeightKg", "83").param("targetWeightKg", "77").param("targetDate", "2027-04-30")
                        .param("pushUpsTarget", "25").param("reviewId", "12"))
                .andExpect(redirectedUrl("/health/goal"));
        verify(reviewService).decide("user", 12L, ReviewDecision.ACCEPTED);
    }

    @Test
    void goal_reviewWithoutProposal_isIgnored() throws Exception {
        WeeklyReview review = new WeeklyReview();
        review.setId(13L);
        review.setWeekStart(LocalDate.of(2026, 11, 2));
        when(reviewService.find("user", 13L)).thenReturn(Optional.of(review));

        mockMvc.perform(get("/health/goal").param("review", "13"))
                .andExpect(content().string(not(containsString("Vorschlag aus dem Ziel-Check"))));
    }

    @Test
    void today_rendersTargetsTrendAndDueMeasurements() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(view().name("health/today"))
                .andExpect(content().string(containsString("1.530 kcal")))
                .andExpect(content().string(containsString("720 / 2.250")))
                .andExpect(content().string(containsString("Tagesziel 2.000 + 250 kcal Training")))
                .andExpect(content().string(containsString("Fußball heute?")))
                .andExpect(content().string(containsString("Training eintragen")))
                .andExpect(content().string(containsString("54 / 160 g")))
                .andExpect(content().string(containsString("Putenschnitzel mit Erdäpfelsalat")))
                .andExpect(content().string(containsString("/health/meals/4/edit")))
                .andExpect(content().string(containsString("health-tabs")))
                .andExpect(content().string(containsString("Mahlzeit erfassen")))
                .andExpect(content().string(containsString("82,6 kg")))
                .andExpect(content().string(containsString("-0,4 kg / Woche")))
                .andExpect(content().string(containsString("Messen fällig")))
                .andExpect(content().string(containsString("bottom-nav")))
                .andExpect(content().string(containsString("manifest.webmanifest")));
    }

    @Test
    void today_footballPlayed_showsEntryWithChange() throws Exception {
        when(trainingService.footballState(TODAY)).thenReturn(FootballState.PLAYED);

        mockMvc.perform(get("/health"))
                .andExpect(content().string(containsString("Fußball 70 min eingetragen · +250 kcal")))
                .andExpect(content().string(containsString("value=\"reset\"")));
    }

    @Test
    void today_duringPause_noFootballQuestion_butAnswerStillShown() throws Exception {
        HealthPause pause = new HealthPause();
        pause.setStartDate(TODAY.minusDays(1));
        when(healthService.activePause()).thenReturn(Optional.of(pause));

        mockMvc.perform(get("/health"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Fußball heute?"))));

        when(trainingService.footballState(TODAY)).thenReturn(FootballState.PLAYED);
        mockMvc.perform(get("/health"))
                .andExpect(content().string(containsString("Fußball 70 min eingetragen")));
    }

    @Test
    void today_notFootballDay_noCard() throws Exception {
        when(trainingService.footballState(TODAY)).thenReturn(FootballState.NOT_ASKED);

        mockMvc.perform(get("/health"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Fußball heute?"))));
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
                        .param("footballBonusKcal", "250")
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
