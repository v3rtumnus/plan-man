package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.HealthPauseRepository;
import at.v3rtumnus.planman.dao.WeeklyReviewRepository;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.health.*;
import at.v3rtumnus.planman.entity.notification.Notification;
import at.v3rtumnus.planman.entity.notification.NotificationSeverity;
import at.v3rtumnus.planman.service.notification.NotificationEvaluator.NotificationCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HealthReviewNotificationsTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private final Clock clock = Clock.fixed(LocalDateTime.of(2026, 11, 9, 6, 5).atZone(ZONE).toInstant(), ZONE);

    private final WeeklyReviewRepository reviews = mock(WeeklyReviewRepository.class);
    private final HealthPauseRepository pauses = mock(HealthPauseRepository.class);
    private WeeklyReview done;
    private WeeklyReview stalled;

    @BeforeEach
    void setUp() {
        done = review("michael", MONDAY, GoalStatus.ON_TRACK);
        done.setHeadline("Gute Woche");
        stalled = review("aga", MONDAY, GoalStatus.STALLED);
        stalled.setGoalReason("Trend seit 3 Wochen flach.");
        WeeklyReview pending = review("michael", MONDAY.minusWeeks(1), null);
        pending.setFeedbackStatus(FeedbackStatus.PENDING);
        when(reviews.findByFeedbackAtAfter(LocalDateTime.of(2026, 11, 3, 6, 5))).thenReturn(List.of(done, stalled, pending));
        when(pauses.findActive(any(), any())).thenReturn(Optional.empty());
    }

    private static WeeklyReview review(String username, LocalDate monday, GoalStatus status) {
        UserProfile user = new UserProfile();
        user.setUsername(username);
        WeeklyReview review = new WeeklyReview();
        review.setId(monday.getDayOfMonth() + 0L);
        review.setUser(user);
        review.setWeekStart(monday);
        review.setFeedbackStatus(FeedbackStatus.DONE);
        review.setGoalCheck(status != null);
        review.setGoalStatus(status);
        return review;
    }

    @Test
    void weeklyReview_inboxOnlyForTheReviewsOwnUser() {
        var evaluator = new HealthReviewNotifications.WeeklyReviewEvaluator(reviews, pauses, clock);
        assertThat(evaluator.ruleType()).isEqualTo("HEALTH_WEEKLY_REVIEW");

        List<NotificationCandidate> candidates = evaluator.evaluate(null);

        assertThat(candidates).hasSize(2);
        NotificationCandidate first = candidates.getFirst();
        assertThat(first.title()).isEqualTo("Wochenrückblick 2. Nov. – 8. Nov.");
        assertThat(first.message()).isEqualTo("Gute Woche");
        assertThat(first.recipient()).isEqualTo("michael");
        assertThat(first.periodKey()).isEqualTo("review-2026-11-02");
        assertThat(first.url()).isEqualTo("/health/week?start=2026-11-02");
        assertThat(first.push()).isFalse();
        assertThat(first.severity()).isEqualTo(NotificationSeverity.INFO);
        assertThat(candidates.get(1).message()).isEqualTo("Dein Rückblick auf die Woche ist da.");
    }

    @Test
    void goalCheck_pushWithTwoButtons_onlyWhenATalkIsWorthIt() {
        var evaluator = new HealthReviewNotifications.GoalCheckEvaluator(reviews, pauses, clock);
        assertThat(evaluator.ruleType()).isEqualTo("HEALTH_GOAL_CHECK");

        List<NotificationCandidate> candidates = evaluator.evaluate(null);

        assertThat(candidates).hasSize(1);
        NotificationCandidate c = candidates.getFirst();
        assertThat(c.title()).isEqualTo("Ziel-Check: Gespräch lohnt sich");
        assertThat(c.message()).isEqualTo("Stagniert: Trend seit 3 Wochen flach.");
        assertThat(c.recipient()).isEqualTo("aga");
        assertThat(c.push()).isTrue();
        assertThat(c.severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(c.periodKey()).isEqualTo("goalcheck-2026-11-02");
        assertThat(c.actions()).extracting(a -> a.key()).containsExactly("talk", "keep");

        stalled.setDecision(ReviewDecision.KEEP);
        assertThat(evaluator.evaluate(null)).isEmpty();
    }

    @Test
    void goalCheck_messageWithoutReasonAndLongReason() {
        stalled.setGoalReason(null);
        assertThat(HealthReviewNotifications.GoalCheckEvaluator.message(stalled)).isEqualTo("Stagniert.");
        stalled.setGoalStatus(GoalStatus.TOO_FAST);
        stalled.setGoalReason("x".repeat(400));
        assertThat(HealthReviewNotifications.GoalCheckEvaluator.message(stalled)).hasSize(298).endsWith("…");
    }

    @Test
    void nothingDuringAPause() {
        when(pauses.findActive(eq("michael"), any())).thenReturn(Optional.of(new HealthPause()));
        when(pauses.findActive(eq("aga"), any())).thenReturn(Optional.of(new HealthPause()));
        assertThat(new HealthReviewNotifications.WeeklyReviewEvaluator(reviews, pauses, clock).evaluate(null)).isEmpty();
        assertThat(new HealthReviewNotifications.GoalCheckEvaluator(reviews, pauses, clock).evaluate(null)).isEmpty();
    }

    @Test
    void actionHandler_recordsTheDecision() {
        WeeklyReviewService service = mock(WeeklyReviewService.class);
        when(service.find("aga", MONDAY)).thenReturn(Optional.of(stalled));
        var handler = new HealthReviewNotifications.GoalCheckActionHandler(service);
        assertThat(handler.ruleType()).isEqualTo("HEALTH_GOAL_CHECK");
        Notification notification = new Notification();
        notification.setRecipient(stalled.getUser());
        notification.setPeriodKey("goalcheck-2026-11-02");

        handler.handle(notification, "talk");
        verify(service).decide("aga", stalled.getId(), ReviewDecision.TALK);
        handler.handle(notification, "keep");
        verify(service).decide("aga", stalled.getId(), ReviewDecision.KEEP);

        assertThatThrownBy(() -> handler.handle(notification, "other")).isInstanceOf(IllegalArgumentException.class);
        notification.setPeriodKey("goalcheck-2026-10-26");
        assertThatThrownBy(() -> handler.handle(notification, "talk")).isInstanceOf(IllegalStateException.class);
    }
}
