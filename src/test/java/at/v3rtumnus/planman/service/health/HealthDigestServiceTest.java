package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.HealthGoalRepository;
import at.v3rtumnus.planman.dao.HealthPauseRepository;
import at.v3rtumnus.planman.dto.health.DaySummary;
import at.v3rtumnus.planman.dto.health.HealthDigest;
import at.v3rtumnus.planman.dto.health.WeightTrend;
import at.v3rtumnus.planman.dto.notification.DigestDTO;
import at.v3rtumnus.planman.entity.health.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HealthDigestServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");
    /** Tuesday 06:00; yesterday is Monday 2026-11-09, last week starts 2026-11-02. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private final Clock clock = Clock.fixed(TODAY.atTime(6, 0).atZone(ZONE).toInstant(), ZONE);

    private final HealthGoalRepository goals = mock(HealthGoalRepository.class);
    private final HealthPauseRepository pauses = mock(HealthPauseRepository.class);
    private final HealthHistoryService history = mock(HealthHistoryService.class);
    private final WeeklyReviewService reviews = mock(WeeklyReviewService.class);
    private final HealthDigestService service = new HealthDigestService(goals, pauses, history, reviews, clock);

    private static DaySummary day(int meals, int kcal, String protein) {
        return new DaySummary(TODAY.minusDays(1), 2000, 250, 160, kcal, new BigDecimal(protein), meals, null, false, List.of());
    }

    @BeforeEach
    void setUp() {
        HealthGoal goal = new HealthGoal();
        goal.setTargetWeightKg(new BigDecimal("77.0"));
        when(goals.findGoalFor("michael", TODAY)).thenReturn(Optional.of(goal));
        when(pauses.findActive(any(), any())).thenReturn(Optional.empty());
        when(history.days("michael", TODAY.minusDays(1), TODAY.minusDays(1))).thenReturn(List.of(day(4, 1850, "142.4")));
        when(history.weightTrend("michael", TODAY)).thenReturn(
                new WeightTrend(new BigDecimal("82.4"), new BigDecimal("-0.42"), new BigDecimal("82.1"), TODAY));
    }

    @Test
    void digest_yesterdayTrendAndLastWeeksReview() {
        WeeklyReview review = new WeeklyReview();
        review.setWeekStart(LocalDate.of(2026, 11, 2));
        review.setFeedbackStatus(FeedbackStatus.DONE);
        review.setHeadline("Gute Woche");
        review.setFeedback("Text");
        review.setGoalCheck(true);
        review.setGoalStatus(GoalStatus.STALLED);
        review.setFeedbackAt(LocalDateTime.of(2026, 11, 10, 5, 31));
        when(reviews.latest("michael")).thenReturn(Optional.of(review));

        HealthDigest digest = service.digest("michael").orElseThrow();

        assertThat(digest.date()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(digest.kcalEaten()).isEqualTo(1850);
        assertThat(digest.kcalTarget()).isEqualTo(2250);
        assertThat(digest.proteinG()).isEqualByComparingTo("142");
        assertThat(digest.targetWeightKg()).isEqualByComparingTo("77.0");
        assertThat(digest.line()).isEqualTo("Gestern 1.850 / 2.250 kcal, 142 / 160 g Protein · Trend 82,4 kg (-0,4 kg/Woche).");
        assertThat(digest.review().headline()).isEqualTo("Gute Woche");
        assertThat(digest.review().goalStatus()).isEqualTo("STALLED");
        assertThat(digest.review().talkSuggested()).isTrue();
        assertThat(digest.review().fresh()).isTrue();
        assertThat(digest.review().url()).isEqualTo("/health/week?start=2026-11-02");

        review.setDecision(ReviewDecision.KEEP);
        review.setFeedbackAt(LocalDateTime.of(2026, 11, 9, 5, 31));
        assertThat(service.digest("michael").orElseThrow().review().talkSuggested()).isFalse();
        assertThat(service.digest("michael").orElseThrow().review().fresh()).isFalse();

        review.setWeekStart(LocalDate.of(2026, 10, 26));
        assertThat(service.digest("michael").orElseThrow().review()).isNull();
    }

    @Test
    void digest_withoutGoalIsEmpty() {
        assertThat(service.digest("aga")).isEmpty();
    }

    @Test
    void line_variants() {
        WeightTrend noTrend = WeightTrend.EMPTY;
        assertThat(HealthDigestService.line(day(0, 0, "0"), noTrend, false)).isEqualTo("Gestern nichts eingetragen.");
        assertThat(HealthDigestService.line(day(0, 0, "0"), noTrend, true)).isEqualTo("Gesundheit: Pause aktiv.");
        WeightTrend rising = new WeightTrend(new BigDecimal("82.0"), new BigDecimal("0.2"), null, null);
        assertThat(HealthDigestService.line(day(0, 0, "0"), rising, false)).isEqualTo("Gestern nichts eingetragen · Trend 82,0 kg (+0,2 kg/Woche).");
        WeightTrend young = new WeightTrend(new BigDecimal("82.0"), null, null, null);
        DaySummary noTargets = new DaySummary(TODAY, null, 0, null, 900, new BigDecimal("50"), 1, null, false, List.of());
        assertThat(HealthDigestService.line(noTargets, young, false)).isEqualTo("Gestern 900 kcal, 50 g Protein · Trend 82,0 kg.");
    }

    @Test
    void withHealth_addsEachUsersPart() {
        when(reviews.latest(any())).thenReturn(Optional.empty());
        DigestDTO digest = new DigestDTO(LocalDateTime.of(2026, 11, 10, 6, 0), Map.of(
                "michael", new DigestDTO.UserDigest(0, List.of()),
                "aga", new DigestDTO.UserDigest(2, List.of())));

        DigestDTO result = service.withHealth(digest);

        assertThat(result.generatedAt()).isEqualTo(digest.generatedAt());
        assertThat(result.users().get("michael").health()).isNotNull();
        assertThat(result.users().get("aga").health()).isNull();
        assertThat(result.users().get("aga").unread()).isEqualTo(2);
        verify(history, never()).days(eq("aga"), any(), any());
    }
}
