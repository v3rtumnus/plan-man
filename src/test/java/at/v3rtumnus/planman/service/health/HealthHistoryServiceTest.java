package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.*;
import at.v3rtumnus.planman.dto.health.DaySummary;
import at.v3rtumnus.planman.dto.health.HealthChart;
import at.v3rtumnus.planman.dto.health.WeekSummary;
import at.v3rtumnus.planman.dto.health.WeightTrend;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.health.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class HealthHistoryServiceTest {

    static final ZoneId ZONE = ZoneId.of("Europe/Vienna");
    /** Monday of the week under test; today is the Tuesday after it. */
    static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

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
    private MealRepository mealRepository;
    @Autowired
    private WorkoutRepository workoutRepository;
    @Autowired
    private BodyMeasurementRepository measurementRepository;

    private UserProfile michael;

    @BeforeEach
    void setUp() {
        michael = user("michael");
        user("other");
    }

    private UserProfile user(String name) {
        UserProfile user = new UserProfile();
        user.setUsername(name);
        user.setPassword("pw");
        return em.persist(user);
    }

    HealthHistoryService service() {
        Clock clock = Clock.fixed(TODAY.atTime(5, 30).atZone(ZONE).toInstant(), ZONE);
        return new HealthHistoryService(goalRepository, dayRepository, pauseRepository, mealRepository,
                workoutRepository, measurementRepository, clock);
    }

    static HealthGoal goal(UserProfile user, LocalDate from, LocalDate to, int kcal) {
        HealthGoal goal = new HealthGoal();
        goal.setUser(user);
        goal.setTitle("Fett verlieren + Kraft");
        goal.setValidFrom(from);
        goal.setValidTo(to);
        goal.setKcalTarget(kcal);
        goal.setProteinTargetG(160);
        goal.setFootballBonusKcal(250);
        goal.setStepsTarget(6000);
        goal.setStartWeightKg(new BigDecimal("83.0"));
        goal.setTargetWeightKg(new BigDecimal("77.0"));
        goal.setTargetDate(LocalDate.of(2027, 3, 31));
        goal.setPushUpsTarget(25);
        goal.setCreatedAt(LocalDateTime.now());
        return goal;
    }

    static Meal meal(UserProfile user, LocalDate date, int kcal, String protein) {
        Meal meal = new Meal();
        meal.setUser(user);
        meal.setEatenOn(date);
        meal.setSlot(MealSlot.LUNCH);
        meal.setTitle("Essen");
        meal.setSource(MealSource.MANUAL);
        meal.setKcal(kcal);
        meal.setProteinG(new BigDecimal(protein));
        meal.setCarbsG(BigDecimal.ZERO);
        meal.setFatG(BigDecimal.ZERO);
        meal.setCreatedAt(LocalDateTime.now());
        return meal;
    }

    static Workout workout(UserProfile user, LocalDate date, WorkoutType type, int bonus) {
        Workout workout = new Workout();
        workout.setUser(user);
        workout.setDoneOn(date);
        workout.setType(type);
        workout.setDurationMin(30);
        workout.setIntensity(Intensity.MEDIUM);
        workout.setMet(new BigDecimal("7.0"));
        workout.setKcal(bonus + 40);
        workout.setBonusKcal(bonus);
        workout.setKcalSource(KcalSource.MET);
        workout.setCreatedAt(LocalDateTime.now());
        return workout;
    }

    static BodyMeasurement measurement(UserProfile user, LocalDate date, MeasurementType type, String value) {
        BodyMeasurement m = new BodyMeasurement();
        m.setUser(user);
        m.setMeasuredOn(date);
        m.setType(type);
        m.setMeasuredValue(new BigDecimal(value));
        m.setSource(MeasurementSource.MANUAL);
        m.setCreatedAt(LocalDateTime.now());
        return m;
    }

    /** A typical week: 5 logged days, a pause on Saturday, Freeletics twice, football on Tuesday. */
    void typicalWeek() {
        goalRepository.save(goal(michael, MONDAY.minusWeeks(4), null, 2000));
        int[] kcal = {1900, 2300, 2050, 1800, 2100};
        String[] protein = {"170", "150", "161", "120", "165"};
        for (int i = 0; i < kcal.length; i++) {
            mealRepository.save(meal(michael, MONDAY.plusDays(i), kcal[i] - 500, "40"));
            mealRepository.save(meal(michael, MONDAY.plusDays(i), 500, new BigDecimal(protein[i]).subtract(new BigDecimal("40")).toPlainString()));
        }
        mealRepository.save(meal(em.find(UserProfile.class, "other"), MONDAY, 5000, "10"));
        workoutRepository.save(workout(michael, MONDAY, WorkoutType.FREELETICS, 250));
        workoutRepository.save(workout(michael, MONDAY.plusDays(1), WorkoutType.FOOTBALL, 250));
        workoutRepository.save(workout(michael, MONDAY.plusDays(3), WorkoutType.FREELETICS, 300));
        HealthPause pause = new HealthPause();
        pause.setUser(michael);
        pause.setStartDate(MONDAY.plusDays(5));
        pause.setEndDate(MONDAY.plusDays(5));
        pauseRepository.save(pause);
        // weights: the week before around 83, this week around 82.5
        for (int i = 1; i <= 7; i++) {
            measurementRepository.save(measurement(michael, MONDAY.minusDays(i), MeasurementType.WEIGHT, "83.0"));
            measurementRepository.save(measurement(michael, MONDAY.plusDays(i - 1), MeasurementType.WEIGHT, "82.5"));
        }
        measurementRepository.save(measurement(michael, MONDAY.minusWeeks(3), MeasurementType.WAIST, "97.0"));
        measurementRepository.save(measurement(michael, MONDAY.plusDays(2), MeasurementType.WAIST, "95.5"));
        measurementRepository.save(measurement(michael, MONDAY.plusDays(9), MeasurementType.WAIST, "94.0"));
        measurementRepository.save(measurement(michael, MONDAY.plusDays(4), MeasurementType.PUSH_UPS, "14"));
    }

    @Test
    void days_combineTargetsTrainingMealsWeightAndPauses() {
        typicalWeek();
        HealthDay snapshot = new HealthDay();
        snapshot.setUser(michael);
        snapshot.setDate(MONDAY.plusDays(2));
        snapshot.setKcalTarget(2100);
        snapshot.setProteinTargetG(150);
        dayRepository.save(snapshot);

        List<DaySummary> days = service().days("michael", MONDAY, MONDAY.plusDays(6));

        assertThat(days).hasSize(7);
        DaySummary monday = days.getFirst();
        assertThat(monday.baseKcalTarget()).isEqualTo(2000);
        assertThat(monday.kcalTarget()).isEqualTo(2250);
        assertThat(monday.kcalEaten()).isEqualTo(1900);
        assertThat(monday.proteinG()).isEqualByComparingTo("170");
        assertThat(monday.meals()).isEqualTo(2);
        assertThat(monday.weightKg()).isEqualByComparingTo("82.5");
        assertThat(monday.logged()).isTrue();
        assertThat(monday.proteinReached()).isTrue();
        assertThat(monday.dayLabel()).isEqualTo("Mo");
        assertThat(monday.overTarget()).isFalse();
        assertThat(monday.kcalPercent()).isEqualTo(84);
        // the day's snapshot wins over the goal
        assertThat(days.get(2).kcalTarget()).isEqualTo(2100);
        assertThat(days.get(2).proteinTargetG()).isEqualTo(150);
        assertThat(days.get(1).overTarget()).isTrue();
        assertThat(days.get(5).paused()).isTrue();
        assertThat(days.get(5).logged()).isFalse();
        assertThat(days.get(6).meals()).isZero();
    }

    @Test
    void days_withoutGoal_haveNoTargets() {
        mealRepository.save(meal(michael, MONDAY, 800, "30"));
        DaySummary day = service().days("michael", MONDAY, MONDAY).getFirst();
        assertThat(day.kcalTarget()).isNull();
        assertThat(day.proteinReached()).isFalse();
        assertThat(day.kcalPercent()).isZero();
        assertThat(day.overTarget()).isFalse();
    }

    @Test
    void goalOn_picksTheGoalInForce() {
        HealthGoal old = goal(michael, MONDAY.minusWeeks(4), MONDAY.plusDays(3), 2200);
        HealthGoal current = goal(michael, MONDAY.plusDays(3), null, 1900);
        List<HealthGoal> goals = List.of(current, old);
        assertThat(HealthHistoryService.goalOn(goals, MONDAY.plusDays(2))).contains(old);
        assertThat(HealthHistoryService.goalOn(goals, MONDAY.plusDays(3))).contains(current);
        assertThat(HealthHistoryService.goalOn(goals, MONDAY.minusWeeks(5))).isEmpty();
    }

    @Test
    void week_aggregatesLoggedDaysOnly() {
        typicalWeek();

        WeekSummary week = service().week("michael", MONDAY);

        assertThat(week.weekStart()).isEqualTo(MONDAY);
        assertThat(week.weekEnd()).isEqualTo(MONDAY.plusDays(6));
        assertThat(week.loggedDays()).isEqualTo(5);
        assertThat(week.pausedDays()).isEqualTo(1);
        assertThat(week.kcalAvg()).isEqualTo(2030);
        // targets: 2250, 2250, 2000, 2300, 2000 → 2160
        assertThat(week.kcalTargetAvg()).isEqualTo(2160);
        assertThat(week.proteinAvgG()).isEqualByComparingTo("153.2");
        assertThat(week.proteinTargetG()).isEqualTo(160);
        assertThat(week.proteinDays()).isEqualTo(3);
        assertThat(week.freeleticsSessions()).isEqualTo(2);
        assertThat(week.footballPlayed()).isTrue();
        assertThat(week.trainingKcal()).isEqualTo(800);
        assertThat(week.weightTrendKg()).isEqualByComparingTo("82.5");
        assertThat(week.weightChangeKg()).isEqualByComparingTo("-0.5");
        assertThat(week.weeklyRateKg()).isNotNull().isNegative();
        // waist: latest up to Sunday, change against the first one since the goal began
        assertThat(week.waistCm()).isEqualByComparingTo("95.5");
        assertThat(week.waistChangeCm()).isEqualByComparingTo("-1.5");
        assertThat(week.pushUps()).isEqualTo(14);
    }

    @Test
    void week_empty() {
        WeekSummary week = service().week("michael", MONDAY);
        assertThat(week.loggedDays()).isZero();
        assertThat(week.kcalAvg()).isNull();
        assertThat(week.kcalTargetAvg()).isNull();
        assertThat(week.proteinAvgG()).isNull();
        assertThat(week.weightTrendKg()).isNull();
        assertThat(week.weightChangeKg()).isNull();
        assertThat(week.waistCm()).isNull();
        assertThat(week.waistChangeCm()).isNull();
        assertThat(week.pushUps()).isNull();
    }

    @Test
    void chart_hasDailyTrendAndGoalLines() {
        typicalWeek();

        HealthChart chart = service().chart("michael");

        assertThat(chart.weight()).hasSize(14);
        assertThat(chart.weight().getFirst().date()).isEqualTo(MONDAY.minusDays(7).toString());
        // a trend value for every day from the first weight until today while the 7-day window has data
        assertThat(chart.trend().getFirst().date()).isEqualTo(MONDAY.minusDays(7).toString());
        assertThat(chart.trend().getLast().date()).isEqualTo(TODAY.toString());
        assertThat(chart.waist()).hasSize(3);
        assertThat(chart.pushUps()).hasSize(1);
        assertThat(chart.pushUps().getFirst().value()).isEqualByComparingTo("14");
        assertThat(chart.targetKg()).isEqualByComparingTo("77.0");
        assertThat(chart.pushUpsTarget()).isEqualTo(25);
        assertThat(chart.goalStart()).isEqualTo(MONDAY.minusWeeks(4).toString());
        assertThat(chart.today()).isEqualTo(TODAY.toString());
    }

    @Test
    void chart_withoutData() {
        HealthChart chart = service().chart("michael");
        assertThat(chart.weight()).isEmpty();
        assertThat(chart.trend()).isEmpty();
        assertThat(chart.targetKg()).isNull();
        assertThat(chart.goalStart()).isNull();
    }

    @Test
    void weightTrend_forAUser() {
        assertThat(service().weightTrend("michael", TODAY)).isEqualTo(WeightTrend.EMPTY);
        typicalWeek();
        assertThat(service().weightTrend("michael", MONDAY.plusDays(6)).trendKg()).isEqualByComparingTo("82.5");
    }

    @Test
    void mondayOf() {
        assertThat(HealthHistoryService.mondayOf(LocalDate.of(2026, 11, 8))).isEqualTo(MONDAY);
        assertThat(HealthHistoryService.mondayOf(MONDAY)).isEqualTo(MONDAY);
        assertThat(service().today()).isEqualTo(TODAY);
    }
}
