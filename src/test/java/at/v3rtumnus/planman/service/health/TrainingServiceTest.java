package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.*;
import at.v3rtumnus.planman.dto.health.ExerciseView;
import at.v3rtumnus.planman.dto.health.FootballState;
import at.v3rtumnus.planman.dto.health.TrainingWeek;
import at.v3rtumnus.planman.dto.health.ai.ActivityDraft;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TrainingServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");
    /** A Tuesday: the football day. */
    private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 10);

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
    @Autowired
    private WorkoutRepository workoutRepository;
    @Autowired
    private ExerciseRepository exerciseRepository;

    private final ActivityEstimator estimator = mock(ActivityEstimator.class);
    private LocalDate today = TUESDAY;

    @BeforeEach
    void setUp() {
        for (String name : List.of("michael", "other")) {
            UserProfile user = new UserProfile();
            user.setUsername(name);
            user.setPassword("pw");
            em.persist(user);
        }
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

    private HealthService health() {
        Clock clock = Clock.fixed(today.atTime(18, 0).atZone(ZONE).toInstant(), ZONE);
        return new HealthService(goalRepository, dayRepository, pauseRepository, measurementRepository,
                userProfileRepository, clock);
    }

    private TrainingService service() {
        Clock clock = Clock.fixed(today.atTime(18, 0).atZone(ZONE).toInstant(), ZONE);
        return new TrainingService(workoutRepository, exerciseRepository, dayRepository, userProfileRepository,
                health(), estimator, clock, DayOfWeek.TUESDAY, 70, 2);
    }

    private void goal() {
        HealthGoal goal = new HealthGoal();
        goal.setUser(userProfileRepository.findByUsername("michael").orElseThrow());
        goal.setTitle("Ziel");
        goal.setValidFrom(TUESDAY.minusDays(30));
        goal.setKcalTarget(2000);
        goal.setProteinTargetG(160);
        goal.setFootballBonusKcal(250);
        goal.setStepsTarget(6000);
        goal.setStartWeightKg(new BigDecimal("83.0"));
        goal.setTargetWeightKg(new BigDecimal("77.0"));
        goal.setTargetDate(TUESDAY.plusMonths(4));
        goal.setCreatedAt(LocalDateTime.now());
        goalRepository.save(goal);
    }

    // --- kcal ---

    @Test
    void netKcal_matchesThePlan() {
        // health.md § 1: 30 min mittel at 83 kg ≈ 250
        assertThat(TrainingService.netKcal(7, new BigDecimal("83"), 30)).isEqualTo(250);
        assertThat(TrainingService.grossKcal(7, new BigDecimal("83"), 30)).isEqualTo(290);
        assertThat(TrainingService.netKcal(0.5, new BigDecimal("83"), 30)).isZero();
    }

    @Test
    void bodyWeight_usesLatestWeight_elseDefault() {
        assertThat(service().bodyWeightKg()).isEqualByComparingTo("83");
        health().saveMeasurement(MeasurementType.WEIGHT, TUESDAY, new BigDecimal("80.0"));
        assertThat(service().bodyWeightKg()).isEqualByComparingTo("80.0");
        assertThat(service().freeleticsBonus(30, Intensity.MEDIUM)).isEqualTo(240);
    }

    // --- logging ---

    @Test
    void logFreeletics_storesNetBonusAndGrossBurn() {
        Workout workout = service().logFreeletics(30, Intensity.HARD);

        assertThat(workout.getType()).isEqualTo(WorkoutType.FREELETICS);
        assertThat(workout.getDoneOn()).isEqualTo(TUESDAY);
        assertThat(workout.getIntensity()).isEqualTo(Intensity.HARD);
        assertThat(workout.getMet()).isEqualByComparingTo("9.0");
        assertThat(workout.getBonusKcal()).isEqualTo(330);
        assertThat(workout.getKcal()).isEqualTo(370);
        assertThat(workout.getKcalSource()).isEqualTo(KcalSource.MET);
        assertThat(workout.label()).isEqualTo("Freeletics");
        assertThat(workout.dayLabel()).isEqualTo("Di");
    }

    @Test
    void logActivity_walkAndBike_only() {
        Workout walk = service().logActivity(WorkoutType.WALK, 60);
        assertThat(walk.getBonusKcal()).isEqualTo(210);
        assertThat(service().logActivity(WorkoutType.CYCLING, 30).getBonusKcal()).isEqualTo(210);
        assertThatThrownBy(() -> service().logActivity(WorkoutType.FOOTBALL, 30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().logActivity(WorkoutType.WALK, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void logOther_usesModelMetAndMinutesFromFormOrSentence() {
        when(estimator.estimate(anyString())).thenReturn(new ActivityDraft("Tennis Doppel", 5.0, 60));

        Workout fromSentence = service().logOther("1 Stunde Tennis Doppel", null);
        assertThat(fromSentence.getTitle()).isEqualTo("Tennis Doppel");
        assertThat(fromSentence.getDurationMin()).isEqualTo(60);
        assertThat(fromSentence.getBonusKcal()).isEqualTo(330);
        assertThat(fromSentence.getKcalSource()).isEqualTo(KcalSource.AI);
        assertThat(fromSentence.label()).isEqualTo("Tennis Doppel");

        assertThat(service().logOther("Tennis Doppel", 30).getDurationMin()).isEqualTo(30);

        when(estimator.estimate(anyString())).thenReturn(new ActivityDraft("Tennis", 5.0, 0));
        assertThatThrownBy(() -> service().logOther("Tennis", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Wie lange?");
    }

    // --- football ---

    @Test
    void football_openOnTuesday_playedAddsGoalBonus_cancelAndReset() {
        goal();
        TrainingService service = service();
        assertThat(service.footballState(TUESDAY)).isEqualTo(FootballState.OPEN);
        assertThat(service.footballState(TUESDAY.plusDays(1))).isEqualTo(FootballState.NOT_ASKED);

        Workout played = service.logFootball();
        assertThat(played.getBonusKcal()).isEqualTo(250);
        assertThat(played.getDurationMin()).isEqualTo(70);
        assertThat(played.getKcal()).isEqualTo(770);
        assertThat(service.footballState(TUESDAY)).isEqualTo(FootballState.PLAYED);

        service.logFootball();
        assertThat(service.workoutsOn(TUESDAY)).hasSize(1);

        service.cancelFootball();
        assertThat(service.footballState(TUESDAY)).isEqualTo(FootballState.CANCELLED);
        assertThat(service.workoutsOn(TUESDAY)).isEmpty();

        service.resetFootball();
        assertThat(service.footballState(TUESDAY)).isEqualTo(FootballState.OPEN);
    }

    @Test
    void football_withoutGoal_addsNothing() {
        assertThat(service().logFootball().getBonusKcal()).isZero();
    }

    @Test
    void week_countsFreeleticsAndFootballFromMonday() {
        TrainingService service = service();
        today = TUESDAY.minusDays(2); // Sunday of the week before
        service().logFreeletics(30, Intensity.MEDIUM);
        today = TUESDAY;
        service.logFreeletics(30, Intensity.MEDIUM);

        TrainingWeek week = service().week();
        assertThat(week.freeletics()).isEqualTo(1);
        assertThat(week.freeleticsTarget()).isEqualTo(2);
        assertThat(week.footballLabel()).isEqualTo("Fußball offen");

        service().cancelFootball();
        assertThat(service().week().footballLabel()).isEqualTo("Fußball fällt aus");
        service().logFootball();
        assertThat(service().week().footballLabel()).isEqualTo("Fußball gespielt");
    }

    @Test
    void delete_andRecent_onlyOwn() {
        Workout workout = service().logFreeletics(30, Intensity.MEDIUM);
        loginAs("other");
        assertThat(service().recent()).isEmpty();
        service().delete(workout.getId());
        loginAs("michael");
        assertThat(service().recent()).hasSize(1);
        service().delete(workout.getId());
        assertThat(service().recent()).isEmpty();
    }

    // --- dumbbell weights ---

    @Test
    void exercises_addChangeSameDayAndRemove() {
        TrainingService service = service();
        Exercise squat = service.addExercise(" Goblet Squat ", new BigDecimal("12.5"));
        service.addExercise("Rudern", null);

        List<ExerciseView> views = service.exercises();
        assertThat(views).extracting(ExerciseView::name).containsExactly("Goblet Squat", "Rudern");
        assertThat(views.getFirst().weightLabel()).isEqualTo("12,5 kg");
        assertThat(views.getFirst().since()).isEqualTo(TUESDAY);
        assertThat(views.get(1).weightKg()).isNull();
        assertThat(views.get(1).heavierKg()).isEqualByComparingTo("2.5");

        service.changeWeight(squat.getId(), new BigDecimal("15"));
        em.flush();
        em.clear();
        assertThat(exerciseRepository.findById(squat.getId()).orElseThrow().getWeights()).hasSize(1);
        assertThat(service.exercises().getFirst().weightLabel()).isEqualTo("15 kg");

        today = TUESDAY.plusDays(7);
        service().changeWeight(squat.getId(), new BigDecimal("17.5"));
        em.flush();
        em.clear();
        assertThat(exerciseRepository.findById(squat.getId()).orElseThrow().getWeights())
                .extracting(w -> w.getWeightKg().stripTrailingZeros().toPlainString())
                .containsExactly("17.5", "15");

        service().removeExercise(squat.getId());
        assertThat(service().exercises()).extracting(ExerciseView::name).containsExactly("Rudern");
        service().addExercise("goblet squat", null);
        assertThat(service().exercises()).hasSize(2);

        assertThatThrownBy(() -> service().changeWeight(squat.getId(), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().addExercise(" ", null)).isInstanceOf(IllegalArgumentException.class);
        loginAs("other");
        assertThatThrownBy(() -> service().changeWeight(squat.getId(), BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tip_afterThreeWeeksWithThreeEasySessions_noUpperLimit() {
        today = TUESDAY.minusDays(21);
        Exercise squat = service().addExercise("Goblet Squat", new BigDecimal("40"));
        for (int i = 0; i < 3; i++) {
            today = TUESDAY.minusDays(20 - i * 5);
            service().logFreeletics(30, Intensity.MEDIUM);
        }
        today = TUESDAY;

        ExerciseView view = service().exercises().getFirst();
        assertThat(view.tipKg()).isEqualByComparingTo("42.5");
        assertThat(view.tipLabel()).isEqualTo("42,5 kg");

        // one hard session since the weight was set: no tip
        service().logFreeletics(30, Intensity.HARD);
        assertThat(service().exercises().getFirst().tipKg()).isNull();
        // taking the tip starts a new 3-week period
        service().changeWeight(squat.getId(), new BigDecimal("42.5"));
        assertThat(service().exercises().getFirst().tipKg()).isNull();
    }

    @Test
    void tip_tooFewSessionsOrTooSoon_none() {
        today = TUESDAY.minusDays(21);
        service().addExercise("Rudern", new BigDecimal("10"));
        service().logFreeletics(30, Intensity.LIGHT);
        service().logFreeletics(30, Intensity.LIGHT);
        today = TUESDAY;
        assertThat(service().exercises().getFirst().tipKg()).isNull();

        today = TUESDAY.minusDays(20);
        service().addExercise("Schulterdrücken", new BigDecimal("7.5"));
        today = TUESDAY;
        service().logFreeletics(30, Intensity.LIGHT);
        assertThat(service().exercises().get(1).tipKg()).isNull();
    }
}
