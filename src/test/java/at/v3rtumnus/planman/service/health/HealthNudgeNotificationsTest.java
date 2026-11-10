package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.*;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.health.*;
import at.v3rtumnus.planman.entity.notification.Notification;
import at.v3rtumnus.planman.entity.notification.NotificationRule;
import at.v3rtumnus.planman.service.notification.NotificationEvaluator.NotificationCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HealthNudgeNotificationsTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");
    /** A Tuesday. */
    private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 10);

    private final HealthGoalRepository goals = mock(HealthGoalRepository.class);
    private final HealthPauseRepository pauses = mock(HealthPauseRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final HealthDayRepository days = mock(HealthDayRepository.class);
    private final WorkoutRepository workouts = mock(WorkoutRepository.class);
    private final BodyMeasurementRepository measurements = mock(BodyMeasurementRepository.class);
    private final NotificationRule rule = new NotificationRule();
    private HealthGoal goal;

    @BeforeEach
    void setUp() {
        goal = new HealthGoal();
        goal.setFootballBonusKcal(250);
        goal.setValidFrom(LocalDate.of(2026, 11, 7));
        when(goals.findUsernamesWithGoal()).thenReturn(List.of("michael", "aga"));
        when(goals.findGoalFor(eq("michael"), any())).thenReturn(Optional.of(goal));
        when(goals.findGoalFor(eq("aga"), any())).thenReturn(Optional.empty());
        when(pauses.findActive(any(), any())).thenReturn(Optional.empty());
        when(days.findByUserUsernameAndDate(any(), any())).thenReturn(Optional.empty());
        when(measurements.findFirstByUserUsernameAndTypeOrderByMeasuredOnDesc(any(), any())).thenReturn(Optional.empty());
    }

    private HealthNudgeNotifications.Nudges nudges(LocalDateTime at) {
        Clock clock = Clock.fixed(at.atZone(ZONE).toInstant(), ZONE);
        return new HealthNudgeNotifications.Nudges(goals, pauses, notifications, clock);
    }

    private static BodyMeasurement measurement(LocalDate on, String value) {
        BodyMeasurement m = new BodyMeasurement();
        m.setMeasuredOn(on);
        m.setMeasuredValue(new BigDecimal(value));
        return m;
    }

    // --- football

    private HealthNudgeNotifications.FootballEvaluator football(LocalDateTime at) {
        return new HealthNudgeNotifications.FootballEvaluator(nudges(at), days, workouts);
    }

    @Test
    void football_asksAtNightWithButtonsAndPush() {
        rule.setParametersJson("{\"hour\":22}");
        var evaluator = football(TUESDAY.atTime(22, 5));
        assertThat(evaluator.ruleType()).isEqualTo("HEALTH_FOOTBALL");

        List<NotificationCandidate> candidates = evaluator.evaluate(rule);

        assertThat(candidates).singleElement().satisfies(c -> {
            assertThat(c.title()).isEqualTo("Fußball heute?");
            assertThat(c.message()).isEqualTo("Noch nicht eingetragen. Gespielt gibt +250 kcal für heute.");
            assertThat(c.periodKey()).isEqualTo("football-2026-11-10");
            assertThat(c.actions()).extracting("key").containsExactly("played", "cancelled");
            assertThat(c.push()).isTrue();
            assertThat(c.recipient()).isEqualTo("michael");
        });
    }

    @Test
    void football_quietOnOtherDaysBeforeTheHourAndDuringPause() {
        rule.setParametersJson("{}");
        assertThat(football(TUESDAY.plusDays(1).atTime(22, 5)).evaluate(rule)).isEmpty();
        assertThat(football(TUESDAY.atTime(21, 5)).evaluate(rule)).isEmpty();

        when(pauses.findActive(eq("michael"), any())).thenReturn(Optional.of(new HealthPause()));
        assertThat(football(TUESDAY.atTime(22, 5)).evaluate(rule)).isEmpty();
    }

    @Test
    void football_answeredElsewhere_resolvesTheOpenQuestion() {
        rule.setParametersJson("{}");
        Workout played = new Workout();
        played.setType(WorkoutType.FOOTBALL);
        when(workouts.findByUserUsernameAndDoneOnOrderByCreatedAtAsc("michael", TUESDAY)).thenReturn(List.of(played));

        assertThat(football(TUESDAY.atTime(23, 5)).evaluate(rule)).isEmpty();
        verify(notifications).markReadByPeriodKeyPrefix(eq(rule), eq("football-2026-11-10"), any());

        HealthDay cancelled = new HealthDay();
        cancelled.setFootballCancelled(true);
        when(workouts.findByUserUsernameAndDoneOnOrderByCreatedAtAsc("michael", TUESDAY)).thenReturn(List.of());
        when(days.findByUserUsernameAndDate("michael", TUESDAY)).thenReturn(Optional.of(cancelled));
        assertThat(football(TUESDAY.atTime(23, 5)).evaluate(rule)).isEmpty();
    }

    @Test
    void pushCap_afterTwoHealthPushesInboxOnly() {
        rule.setParametersJson("{}");
        when(notifications.countByRecipientUsernameAndPushTrueAndCreatedAtGreaterThanEqualAndRuleRuleTypeStartingWith(
                "michael", TUESDAY.atStartOfDay(), "HEALTH_")).thenReturn(2L);

        assertThat(football(TUESDAY.atTime(22, 5)).evaluate(rule)).singleElement()
                .satisfies(c -> assertThat(c.push()).isFalse());

        var nudges = nudges(TUESDAY.atTime(22, 5));
        ReflectionTestUtils.setField(nudges, "maxPushesPerDay", 3);
        assertThat(nudges.pushAllowed("michael")).isTrue();
    }

    @Test
    void footballButtons_logOrCancelThatDay() {
        TrainingService training = mock(TrainingService.class);
        var handler = new HealthNudgeNotifications.FootballActionHandler(training);
        assertThat(handler.ruleType()).isEqualTo("HEALTH_FOOTBALL");
        Notification n = new Notification();
        n.setPeriodKey("football-2026-11-10");

        handler.handle(n, "played");
        handler.handle(n, "cancelled");

        verify(training).logFootball(TUESDAY);
        verify(training).cancelFootball(TUESDAY);
        assertThatThrownBy(() -> handler.handle(n, "later")).isInstanceOf(IllegalArgumentException.class);
    }

    // --- waist

    @Test
    void waist_dueAfter14DaysInTheMorning() {
        rule.setParametersJson("{\"hour\":7}");
        when(measurements.findFirstByUserUsernameAndTypeOrderByMeasuredOnDesc("michael", MeasurementType.WAIST))
                .thenReturn(Optional.of(measurement(TUESDAY.minusDays(15), "96.5")));
        var evaluator = new HealthNudgeNotifications.WaistDueEvaluator(nudges(TUESDAY.atTime(7, 5)), measurements);
        assertThat(evaluator.ruleType()).isEqualTo("HEALTH_WAIST_DUE");

        assertThat(evaluator.evaluate(rule)).singleElement().satisfies(c -> {
            assertThat(c.title()).isEqualTo("Taille messen");
            assertThat(c.message()).isEqualTo("Zuletzt 96.5 cm vor 15 Tagen. Morgens vor dem Frühstück messen.");
            assertThat(c.periodKey()).isEqualTo("waist-2026-11-09");
            assertThat(c.url()).isEqualTo("/health/measure?type=WAIST");
        });
        assertThat(new HealthNudgeNotifications.WaistDueEvaluator(nudges(TUESDAY.atTime(6, 5)), measurements).evaluate(rule)).isEmpty();
    }

    @Test
    void waist_notDueYet_orMeasuredToday_orNeverMeasured() {
        rule.setParametersJson("{}");
        var evaluator = new HealthNudgeNotifications.WaistDueEvaluator(nudges(TUESDAY.atTime(9, 5)), measurements);
        when(measurements.findFirstByUserUsernameAndTypeOrderByMeasuredOnDesc("michael", MeasurementType.WAIST))
                .thenReturn(Optional.of(measurement(TUESDAY.minusDays(13), "96")));
        assertThat(evaluator.evaluate(rule)).isEmpty();

        when(measurements.findFirstByUserUsernameAndTypeOrderByMeasuredOnDesc("michael", MeasurementType.WAIST))
                .thenReturn(Optional.of(measurement(TUESDAY, "95")));
        assertThat(evaluator.evaluate(rule)).isEmpty();
        verify(notifications).markReadByPeriodKeyPrefix(eq(rule), eq("waist-"), any());

        when(measurements.findFirstByUserUsernameAndTypeOrderByMeasuredOnDesc("michael", MeasurementType.WAIST))
                .thenReturn(Optional.empty());
        assertThat(evaluator.evaluate(rule)).singleElement().satisfies(c -> {
            assertThat(c.message()).startsWith("Noch keine Taille gemessen.");
            assertThat(c.periodKey()).isEqualTo("waist-2026-11-07");
        });
    }

    // --- weight

    @Test
    void weight_missingTwoDays() {
        rule.setParametersJson("{\"hour\":10,\"days\":2}");
        var evaluator = new HealthNudgeNotifications.WeightMissingEvaluator(nudges(TUESDAY.atTime(10, 5)), measurements);
        assertThat(evaluator.ruleType()).isEqualTo("HEALTH_WEIGHT_MISSING");
        when(measurements.findFirstByUserUsernameAndTypeOrderByMeasuredOnDesc("michael", MeasurementType.WEIGHT))
                .thenReturn(Optional.of(measurement(TUESDAY.minusDays(2), "83.1")));

        assertThat(evaluator.evaluate(rule)).singleElement().satisfies(c -> {
            assertThat(c.title()).isEqualTo("Gewicht eintragen");
            assertThat(c.message()).isEqualTo("Seit 2 Tagen kein Gewicht. Der Trend braucht die meisten Tage.");
            assertThat(c.periodKey()).isEqualTo("weight-2026-11-10");
            assertThat(c.url()).isEqualTo("/health/measure?type=WEIGHT");
        });
    }

    @Test
    void weight_quietWhenYesterdayIsThere_beforeTheHour_resolvedToday_andFirstEver() {
        rule.setParametersJson("{}");
        var evaluator = new HealthNudgeNotifications.WeightMissingEvaluator(nudges(TUESDAY.atTime(11, 5)), measurements);
        when(measurements.findFirstByUserUsernameAndTypeOrderByMeasuredOnDesc("michael", MeasurementType.WEIGHT))
                .thenReturn(Optional.of(measurement(TUESDAY.minusDays(1), "83")));
        assertThat(evaluator.evaluate(rule)).isEmpty();

        when(measurements.findFirstByUserUsernameAndTypeOrderByMeasuredOnDesc("michael", MeasurementType.WEIGHT))
                .thenReturn(Optional.of(measurement(TUESDAY, "83")));
        assertThat(evaluator.evaluate(rule)).isEmpty();
        verify(notifications).markReadByPeriodKeyPrefix(eq(rule), eq("weight-"), any());

        when(measurements.findFirstByUserUsernameAndTypeOrderByMeasuredOnDesc("michael", MeasurementType.WEIGHT))
                .thenReturn(Optional.empty());
        assertThat(new HealthNudgeNotifications.WeightMissingEvaluator(nudges(TUESDAY.atTime(9, 5)), measurements)
                .evaluate(rule)).isEmpty();
        assertThat(evaluator.evaluate(rule)).singleElement()
                .satisfies(c -> assertThat(c.message()).isEqualTo("Noch kein Gewicht eingetragen."));
    }
}
