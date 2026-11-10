package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.*;
import at.v3rtumnus.planman.dto.health.GoalForm;
import at.v3rtumnus.planman.dto.health.GoalProgress;
import at.v3rtumnus.planman.dto.health.MeasurementStatus;
import at.v3rtumnus.planman.dto.health.WeightTrend;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.health.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class HealthServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");

    @MockitoBean
    private CacheManager cacheManager;

    @Autowired
    private TestEntityManager em;
    @Autowired
    private HealthGoalRepository goalRepository;
    @Autowired
    private HealthDayRepository dayRepository;
    @Autowired
    private HealthPauseRepository pauseRepository;
    @Autowired
    private BodyMeasurementRepository measurementRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;

    private LocalDate today = LocalDate.of(2026, 11, 10);

    @BeforeEach
    void setUp() {
        UserProfile user = new UserProfile();
        user.setUsername("michael");
        user.setPassword("pw");
        em.persist(user);
        UserProfile other = new UserProfile();
        other.setUsername("other");
        other.setPassword("pw");
        em.persist(other);
        loginAs("michael");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private HealthService service() {
        Clock clock = Clock.fixed(today.atTime(9, 0).atZone(ZONE).toInstant(), ZONE);
        return new HealthService(goalRepository, dayRepository, pauseRepository, measurementRepository,
                userProfileRepository, clock);
    }

    private GoalForm goalForm(int kcal) {
        GoalForm form = new GoalForm();
        form.setKcalTarget(kcal);
        form.setStartWeightKg(new BigDecimal("83.0"));
        form.setTargetWeightKg(new BigDecimal("77.0"));
        form.setTargetDate(LocalDate.of(2027, 2, 28));
        return form;
    }

    @Test
    void changeGoal_closesOldGoalAndKeepsHistory() {
        HealthService service = service();
        HealthGoal first = service.changeGoal(goalForm(2000));

        today = today.plusDays(14);
        HealthService later = service();
        HealthGoal second = later.changeGoal(goalForm(1900));

        assertThat(goalRepository.findById(first.getId()).orElseThrow().getValidTo()).isEqualTo(today);
        assertThat(later.currentGoal()).contains(second);
        assertThat(later.goalHistory()).extracting(HealthGoal::getKcalTarget).containsExactly(1900, 2000);
        assertThat(goalRepository.findGoalFor("michael", today.minusDays(1))).contains(first);
    }

    @Test
    void changeGoal_sameDayTwice_lastOneApplies() {
        HealthService service = service();
        service.changeGoal(goalForm(2000));
        HealthGoal second = service.changeGoal(goalForm(2100));

        assertThat(service.currentGoal()).contains(second);
        assertThat(service.day(today).getKcalTarget()).isEqualTo(2100);
    }

    @Test
    void changeGoal_rejectsTargetDateNotInFuture() {
        GoalForm form = goalForm(2000);
        form.setTargetDate(today);

        assertThatThrownBy(() -> service().changeGoal(form)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void day_keepsTargetsOfTheGoalInForceThatDay() {
        HealthService service = service();
        service.changeGoal(goalForm(2000));
        HealthDay first = service.day(today);

        today = today.plusDays(7);
        HealthService later = service();
        later.changeGoal(goalForm(1800));

        assertThat(dayRepository.findById(first.getId()).orElseThrow().getKcalTarget()).isEqualTo(2000);
        assertThat(later.day(today).getKcalTarget()).isEqualTo(1800);
        assertThat(later.day(today).getStepsTarget()).isEqualTo(8000);
    }

    @Test
    void dayForUser_worksWithoutLogin_andRejectsUnknownUsers() {
        service().changeGoal(goalForm(2000));

        assertThat(service().day("michael", today).getKcalTarget()).isEqualTo(2000);
        assertThat(service().day("michael", today).getId()).isEqualTo(service().day(today).getId());
        assertThatThrownBy(() -> service().day("nobody", today)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void day_withoutGoal_isFilledOnceAGoalExists() {
        HealthService service = service();
        assertThat(service.day(today).getKcalTarget()).isNull();

        service.changeGoal(goalForm(2000));

        assertThat(service.day(today).getKcalTarget()).isEqualTo(2000);
    }

    @Test
    void pause_startAndEndOnLaterDay_keepsPeriod() {
        HealthService service = service();
        service.startPause("krank");
        assertThat(service.activePause()).isPresent();
        assertThat(service.day(today).isPaused()).isTrue();

        today = today.plusDays(3);
        HealthService later = service();
        later.endPause();

        assertThat(later.activePause()).isEmpty();
        HealthPause pause = pauseRepository.findAll().getFirst();
        assertThat(pause.getEndDate()).isEqualTo(today.minusDays(1));
        assertThat(pause.getReason()).isEqualTo("krank");
        assertThat(later.day(today).isPaused()).isFalse();
    }

    @Test
    void pause_endedOnStartDay_isRemoved() {
        HealthService service = service();
        service.startPause(" ");
        service.startPause("ignored while active");
        service.endPause();

        assertThat(pauseRepository.findAll()).isEmpty();
    }

    @Test
    void saveMeasurement_replacesValueOfSameDay() {
        HealthService service = service();
        service.saveMeasurement(MeasurementType.WEIGHT, today, new BigDecimal("83.04"));
        service.saveMeasurement(MeasurementType.WEIGHT, today, new BigDecimal("82.8"));

        assertThat(measurementRepository.findAll()).hasSize(1);
        assertThat(service.measurementOn(MeasurementType.WEIGHT, today).orElseThrow().getMeasuredValue())
                .isEqualByComparingTo("82.8");
        assertThat(dayRepository.findByUserUsernameAndDate("michael", today)).isPresent();
    }

    @Test
    void saveMeasurement_rejectsFutureDateAndImplausibleValue() {
        HealthService service = service();

        assertThatThrownBy(() -> service.saveMeasurement(MeasurementType.WEIGHT, today.plusDays(1), BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.saveMeasurement(MeasurementType.WEIGHT, today, new BigDecimal("820")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Gewicht");
    }

    @Test
    void measurements_areSeparatedByUser() {
        service().saveMeasurement(MeasurementType.WEIGHT, today, new BigDecimal("82.0"));
        loginAs("other");

        assertThat(service().latest(MeasurementType.WEIGHT)).isEmpty();
        assertThat(service().weightTrend()).isEqualTo(WeightTrend.EMPTY);
    }

    @Test
    void weightTrend_fallsBackToOldLatestValue() {
        HealthService service = service();
        service.saveMeasurement(MeasurementType.WEIGHT, today.minusDays(40), new BigDecimal("85.0"));

        WeightTrend trend = service.weightTrend();

        assertThat(trend.trendKg()).isNull();
        assertThat(trend.latestKg()).isEqualByComparingTo("85.0");
    }

    @Test
    void status_reportsFirstLatestAndDue() {
        HealthService service = service();
        service.saveMeasurement(MeasurementType.WAIST, today.minusDays(30), new BigDecimal("97.0"));
        HealthGoal goal = service.changeGoal(goalForm(2000));
        goal.setValidFrom(today.minusDays(20));
        service.saveMeasurement(MeasurementType.WAIST, today.minusDays(16), new BigDecimal("96.0"));
        service.saveMeasurement(MeasurementType.WAIST, today.minusDays(2), new BigDecimal("95.0"));

        MeasurementStatus waist = service.status(MeasurementType.WAIST, Optional.of(goal));
        MeasurementStatus pushUps = service.status(MeasurementType.PUSH_UPS, Optional.of(goal));

        assertThat(waist.first()).isEqualByComparingTo("96.0");
        assertThat(waist.latest()).isEqualByComparingTo("95.0");
        assertThat(waist.due()).isFalse();
        assertThat(pushUps.due()).isTrue();
        assertThat(pushUps.target()).isEqualTo(25);
    }

    @Test
    void goalProgress_computesShareAndRequiredRate() {
        HealthService service = service();
        HealthGoal goal = service.changeGoal(goalForm(2000));
        WeightTrend trend = new WeightTrend(new BigDecimal("81.5"), new BigDecimal("-0.40"), null, null);

        GoalProgress progress = service.goalProgress(goal, trend).orElseThrow();

        assertThat(progress.percent()).isEqualTo(25);
        // 6 kg over the 110 days from 10.11.2026 to 28.02.2027
        assertThat(progress.requiredWeeklyRate()).isEqualByComparingTo("-0.38");
        assertThat(service.goalProgress(goal, WeightTrend.EMPTY)).isEmpty();
    }

    @Test
    void goalFormForChange_prefillsFromCurrentGoalOrLatestWeight() {
        HealthService service = service();
        service.saveMeasurement(MeasurementType.WEIGHT, today, new BigDecimal("83.2"));
        assertThat(service.goalFormForChange().getStartWeightKg()).isEqualByComparingTo("83.2");

        service.changeGoal(goalForm(1950));
        assertThat(service.goalFormForChange().getKcalTarget()).isEqualTo(1950);
    }
}
