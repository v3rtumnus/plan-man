package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.HealthPauseRepository;
import at.v3rtumnus.planman.dao.WeeklyReviewRepository;
import at.v3rtumnus.planman.dto.notification.NotificationAction;
import at.v3rtumnus.planman.entity.health.FeedbackStatus;
import at.v3rtumnus.planman.entity.health.ReviewDecision;
import at.v3rtumnus.planman.entity.health.WeeklyReview;
import at.v3rtumnus.planman.entity.notification.Notification;
import at.v3rtumnus.planman.entity.notification.NotificationRule;
import at.v3rtumnus.planman.entity.notification.NotificationSeverity;
import at.v3rtumnus.planman.service.notification.NotificationActionHandler;
import at.v3rtumnus.planman.service.notification.NotificationEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The health rules on the notification system: "Wochenrückblick" (inbox only, the briefing
 * carries it on Monday) and "Ziel-Check: Gespräch lohnt sich" (push with two buttons). Each
 * notification goes only to the review's own user and none during a Pause.
 */
public final class HealthReviewNotifications {

    public static final String WEEKLY_REVIEW = "HEALTH_WEEKLY_REVIEW";
    public static final String GOAL_CHECK = "HEALTH_GOAL_CHECK";
    public static final String ACTION_TALK = "talk";
    public static final String ACTION_KEEP = "keep";

    /** Reviews whose text arrived within this window still notify (the job may have been down). */
    static final int RECENT_DAYS = 6;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN);

    private HealthReviewNotifications() {
    }

    static String weekUrl(WeeklyReview review) {
        return "/health/week?start=" + review.getWeekStart();
    }

    static String weekLabel(WeeklyReview review) {
        return DAY.format(review.getWeekStart()) + " – " + DAY.format(review.weekEnd());
    }

    static String periodKey(String prefix, WeeklyReview review) {
        return prefix + "-" + review.getWeekStart();
    }

    @Component
    @RequiredArgsConstructor
    public static class WeeklyReviewEvaluator implements NotificationEvaluator {

        private final WeeklyReviewRepository reviews;
        private final HealthPauseRepository pauses;
        private final Clock clock;

        @Override
        public String ruleType() {
            return WEEKLY_REVIEW;
        }

        @Override
        public List<NotificationCandidate> evaluate(NotificationRule rule) {
            LocalDate today = LocalDate.now(clock);
            return reviews.findByFeedbackAtAfter(LocalDateTime.now(clock).minusDays(RECENT_DAYS)).stream()
                    .filter(r -> r.getFeedbackStatus() == FeedbackStatus.DONE)
                    .filter(r -> pauses.findActive(r.getUser().getUsername(), today).isEmpty())
                    .map(r -> new NotificationCandidate("Wochenrückblick " + weekLabel(r),
                            r.getHeadline() != null ? r.getHeadline() : "Dein Rückblick auf die Woche ist da.",
                            NotificationSeverity.INFO, periodKey("review", r), weekUrl(r), List.of(), false,
                            r.getUser().getUsername()))
                    .toList();
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class GoalCheckEvaluator implements NotificationEvaluator {

        private final WeeklyReviewRepository reviews;
        private final HealthPauseRepository pauses;
        private final Clock clock;

        @Override
        public String ruleType() {
            return GOAL_CHECK;
        }

        @Override
        public List<NotificationCandidate> evaluate(NotificationRule rule) {
            LocalDate today = LocalDate.now(clock);
            return reviews.findByFeedbackAtAfter(LocalDateTime.now(clock).minusDays(RECENT_DAYS)).stream()
                    .filter(WeeklyReview::needsTalk)
                    .filter(r -> r.getDecision() == null)
                    .filter(r -> pauses.findActive(r.getUser().getUsername(), today).isEmpty())
                    .map(r -> new NotificationCandidate("Ziel-Check: Gespräch lohnt sich", message(r),
                            NotificationSeverity.WARNING, periodKey("goalcheck", r), weekUrl(r),
                            List.of(new NotificationAction(ACTION_TALK, "Gespräch führen"),
                                    new NotificationAction(ACTION_KEEP, "Passt so")),
                            true, r.getUser().getUsername()))
                    .toList();
        }

        static String message(WeeklyReview review) {
            String text = review.getGoalStatus().getLabel()
                    + (review.getGoalReason() != null ? ": " + review.getGoalReason() : ".");
            return text.length() > 300 ? text.substring(0, 297) + "…" : text;
        }
    }

    /** "Gespräch führen" / "Passt so" from the push or the inbox. */
    @Component
    @RequiredArgsConstructor
    public static class GoalCheckActionHandler implements NotificationActionHandler {

        private final WeeklyReviewService reviewService;

        @Override
        public String ruleType() {
            return GOAL_CHECK;
        }

        @Override
        public void handle(Notification notification, String actionKey) {
            ReviewDecision decision = switch (actionKey) {
                case ACTION_TALK -> ReviewDecision.TALK;
                case ACTION_KEEP -> ReviewDecision.KEEP;
                default -> throw new IllegalArgumentException("Unknown goal check action " + actionKey);
            };
            String key = notification.getPeriodKey();
            LocalDate week = LocalDate.parse(key.substring(key.indexOf('-') + 1));
            String username = notification.getRecipient().getUsername();
            WeeklyReview review = reviewService.find(username, week)
                    .orElseThrow(() -> new IllegalStateException("No review for " + username + " week " + week));
            reviewService.decide(username, review.getId(), decision);
        }
    }
}
