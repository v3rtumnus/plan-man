package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.BodyMeasurementRepository;
import at.v3rtumnus.planman.dao.HealthDayRepository;
import at.v3rtumnus.planman.dao.HealthGoalRepository;
import at.v3rtumnus.planman.dao.HealthPauseRepository;
import at.v3rtumnus.planman.dao.NotificationRepository;
import at.v3rtumnus.planman.dao.WorkoutRepository;
import at.v3rtumnus.planman.dto.notification.NotificationAction;
import at.v3rtumnus.planman.entity.health.BodyMeasurement;
import at.v3rtumnus.planman.entity.health.HealthDay;
import at.v3rtumnus.planman.entity.health.HealthGoal;
import at.v3rtumnus.planman.entity.health.MeasurementType;
import at.v3rtumnus.planman.entity.health.WorkoutType;
import at.v3rtumnus.planman.entity.notification.Notification;
import at.v3rtumnus.planman.entity.notification.NotificationRule;
import at.v3rtumnus.planman.entity.notification.NotificationSeverity;
import at.v3rtumnus.planman.service.notification.NotificationActionHandler;
import at.v3rtumnus.planman.service.notification.NotificationEvaluator;
import at.v3rtumnus.planman.service.notification.RuleParameters;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Health nudges (H6): the football question at night, waist due, weight missing. Each goes only
 * to a user with a health goal, never during a Pause, and only while it still needs an answer:
 * once answered elsewhere (on *Heute*, the measure page) the open notification is marked read on
 * the next run. All health rules together push at most {@code planman.health.max-pushes-per-day}
 * times a day per user; beyond that they land in the inbox only.
 */
public final class HealthNudgeNotifications {

    public static final String FOOTBALL = "HEALTH_FOOTBALL";
    public static final String WAIST_DUE = "HEALTH_WAIST_DUE";
    public static final String WEIGHT_MISSING = "HEALTH_WEIGHT_MISSING";
    public static final String ACTION_PLAYED = "played";
    public static final String ACTION_CANCELLED = "cancelled";

    private HealthNudgeNotifications() {
    }

    /** Shared lookups: who gets health nudges today, and whether a push is still allowed. */
    @Component
    @RequiredArgsConstructor
    public static class Nudges {

        private final HealthGoalRepository goals;
        private final HealthPauseRepository pauses;
        private final NotificationRepository notifications;
        private final Clock clock;
        @Value("${planman.health.max-pushes-per-day:2}")
        private int maxPushesPerDay = 2;

        LocalDate today() {
            return LocalDate.now(clock);
        }

        int hour() {
            return LocalDateTime.now(clock).getHour();
        }

        /** Users with a goal in force today and no Pause. */
        List<String> activeUsers() {
            LocalDate today = today();
            List<String> users = new ArrayList<>();
            for (String username : goals.findUsernamesWithGoal()) {
                if (goals.findGoalFor(username, today).isPresent() && pauses.findActive(username, today).isEmpty()) {
                    users.add(username);
                }
            }
            return users;
        }

        Optional<HealthGoal> goal(String username) {
            return goals.findGoalFor(username, today());
        }

        boolean pushAllowed(String username) {
            return notifications.countByRecipientUsernameAndPushTrueAndCreatedAtGreaterThanEqualAndRuleRuleTypeStartingWith(
                    username, today().atStartOfDay(), "HEALTH_") < maxPushesPerDay;
        }

        void resolve(NotificationRule rule, String periodKeyPrefix) {
            notifications.markReadByPeriodKeyPrefix(rule, periodKeyPrefix, LocalDateTime.now(clock));
        }
    }

    /** Football weekday, from {@code hour} (default 22): "Fußball heute?" with Gespielt / Fällt aus. */
    @Component
    @RequiredArgsConstructor
    public static class FootballEvaluator implements NotificationEvaluator {

        private final Nudges nudges;
        private final HealthDayRepository days;
        private final WorkoutRepository workouts;
        @Value("${planman.health.football-day:TUESDAY}")
        private DayOfWeek footballDay = DayOfWeek.TUESDAY;

        @Override
        public String ruleType() {
            return FOOTBALL;
        }

        @Override
        public List<NotificationCandidate> evaluate(NotificationRule rule) {
            LocalDate today = nudges.today();
            if (today.getDayOfWeek() != footballDay) {
                return List.of();
            }
            int hour = RuleParameters.parse(rule.getParametersJson()).integer("hour", 22);
            String key = "football-" + today;
            List<NotificationCandidate> candidates = new ArrayList<>();
            for (String username : nudges.activeUsers()) {
                boolean cancelled = days.findByUserUsernameAndDate(username, today).map(HealthDay::isFootballCancelled).orElse(false);
                boolean played = workouts.findByUserUsernameAndDoneOnOrderByCreatedAtAsc(username, today).stream()
                        .anyMatch(w -> w.getType() == WorkoutType.FOOTBALL);
                if (cancelled || played) {
                    nudges.resolve(rule, key);
                    continue;
                }
                if (nudges.hour() < hour) {
                    continue;
                }
                int bonus = nudges.goal(username).map(HealthGoal::getFootballBonusKcal).orElse(0);
                candidates.add(new NotificationCandidate("Fußball heute?",
                        "Noch nicht eingetragen. Gespielt gibt +" + bonus + " kcal für heute.",
                        NotificationSeverity.INFO, key, "/health",
                        List.of(new NotificationAction(ACTION_PLAYED, "Gespielt"),
                                new NotificationAction(ACTION_CANCELLED, "Fällt aus")),
                        nudges.pushAllowed(username), username));
            }
            return candidates;
        }
    }

    /** "Gespielt" / "Fällt aus" from the push or the inbox, for the day the question was asked. */
    @Component
    @RequiredArgsConstructor
    public static class FootballActionHandler implements NotificationActionHandler {

        private final TrainingService trainingService;

        @Override
        public String ruleType() {
            return FOOTBALL;
        }

        @Override
        public void handle(Notification notification, String actionKey) {
            LocalDate date = LocalDate.parse(notification.getPeriodKey().substring("football-".length()));
            switch (actionKey) {
                case ACTION_PLAYED -> trainingService.logFootball(date);
                case ACTION_CANCELLED -> trainingService.cancelFootball(date);
                default -> throw new IllegalArgumentException("Unknown football action " + actionKey);
            }
        }
    }

    /** Waist every 14 days, in the morning from {@code hour} (default 7), once per due date. */
    @Component
    @RequiredArgsConstructor
    public static class WaistDueEvaluator implements NotificationEvaluator {

        private final Nudges nudges;
        private final BodyMeasurementRepository measurements;

        @Override
        public String ruleType() {
            return WAIST_DUE;
        }

        @Override
        public List<NotificationCandidate> evaluate(NotificationRule rule) {
            int hour = RuleParameters.parse(rule.getParametersJson()).integer("hour", 7);
            LocalDate today = nudges.today();
            int interval = MeasurementType.WAIST.getIntervalDays();
            List<NotificationCandidate> candidates = new ArrayList<>();
            for (String username : nudges.activeUsers()) {
                Optional<BodyMeasurement> latest = measurements.findFirstByUserUsernameAndTypeOrderByMeasuredOnDesc(username, MeasurementType.WAIST);
                if (latest.isPresent() && latest.get().getMeasuredOn().equals(today)) {
                    nudges.resolve(rule, "waist-");
                    continue;
                }
                LocalDate due = latest.map(m -> m.getMeasuredOn().plusDays(interval))
                        .orElseGet(() -> nudges.goal(username).map(HealthGoal::getValidFrom).orElse(today));
                if (due.isAfter(today) || nudges.hour() < hour) {
                    continue;
                }
                String message = latest
                        .map(m -> "Zuletzt " + m.getMeasuredValue().stripTrailingZeros().toPlainString() + " cm vor "
                                + ChronoUnit.DAYS.between(m.getMeasuredOn(), today) + " Tagen. Morgens vor dem Frühstück messen.")
                        .orElse("Noch keine Taille gemessen. Morgens vor dem Frühstück messen.");
                candidates.add(new NotificationCandidate("Taille messen", message, NotificationSeverity.INFO,
                        "waist-" + due, "/health/measure?type=WAIST", List.of(), nudges.pushAllowed(username), username));
            }
            return candidates;
        }
    }

    /**
     * No weight for {@code days} days (default 2: neither yesterday nor today) by {@code hour}
     * (default 10). Single missed days are normal; the trend needs most days, not all.
     */
    @Component
    @RequiredArgsConstructor
    public static class WeightMissingEvaluator implements NotificationEvaluator {

        private final Nudges nudges;
        private final BodyMeasurementRepository measurements;

        @Override
        public String ruleType() {
            return WEIGHT_MISSING;
        }

        @Override
        public List<NotificationCandidate> evaluate(NotificationRule rule) {
            RuleParameters params = RuleParameters.parse(rule.getParametersJson());
            int hour = params.integer("hour", 10);
            int days = Math.max(1, params.integer("days", 2));
            LocalDate today = nudges.today();
            List<NotificationCandidate> candidates = new ArrayList<>();
            for (String username : nudges.activeUsers()) {
                Optional<LocalDate> last = measurements.findFirstByUserUsernameAndTypeOrderByMeasuredOnDesc(username, MeasurementType.WEIGHT)
                        .map(BodyMeasurement::getMeasuredOn);
                if (last.isPresent() && last.get().equals(today)) {
                    nudges.resolve(rule, "weight-");
                    continue;
                }
                if (nudges.hour() < hour || last.isPresent() && last.get().isAfter(today.minusDays(days))) {
                    continue;
                }
                String message = last
                        .map(d -> "Seit " + ChronoUnit.DAYS.between(d, today) + " Tagen kein Gewicht. Der Trend braucht die meisten Tage.")
                        .orElse("Noch kein Gewicht eingetragen.");
                candidates.add(new NotificationCandidate("Gewicht eintragen", message, NotificationSeverity.INFO,
                        "weight-" + today, "/health/measure?type=WEIGHT", List.of(), nudges.pushAllowed(username), username));
            }
            return candidates;
        }
    }
}
