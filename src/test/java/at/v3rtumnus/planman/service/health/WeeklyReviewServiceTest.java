package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.*;
import at.v3rtumnus.planman.dto.health.ai.ReviewDraft;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.health.*;
import at.v3rtumnus.planman.service.JobStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static at.v3rtumnus.planman.service.health.HealthHistoryServiceTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class WeeklyReviewServiceTest {

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
    @Autowired
    private UserProfileRepository userRepository;
    @Autowired
    private WeeklyReviewRepository reviewRepository;

    private final AnthropicWeeklyReviewer reviewer = mock(AnthropicWeeklyReviewer.class);
    private final JobStatusService jobs = mock(JobStatusService.class);
    private UserProfile michael;
    private LocalDate today = TODAY;

    @BeforeEach
    void setUp() {
        michael = new UserProfile();
        michael.setUsername("michael");
        michael.setPassword("pw");
        em.persist(michael);
        when(reviewer.available()).thenReturn(true);
        when(reviewer.model()).thenReturn("claude-opus-5-5");
    }

    private WeeklyReviewService service() {
        Clock clock = Clock.fixed(today.atTime(5, 30).atZone(ZONE).toInstant(), ZONE);
        HealthHistoryService history = new HealthHistoryService(goalRepository, dayRepository, pauseRepository,
                mealRepository, workoutRepository, measurementRepository, clock);
        return new WeeklyReviewService(reviewRepository, goalRepository, pauseRepository, measurementRepository,
                userRepository, history, reviewer, jobs, clock);
    }

    private HealthGoal goalSince(LocalDate from) {
        return goalRepository.save(goal(michael, from, null, 2000));
    }

    private void logDays(int count) {
        for (int i = 0; i < count; i++) {
            mealRepository.save(meal(michael, MONDAY.plusDays(i), 1900, "150"));
        }
        measurementRepository.save(measurement(michael, MONDAY, MeasurementType.WAIST, "96.0"));
        measurementRepository.save(measurement(michael, MONDAY.plusDays(4), MeasurementType.PUSH_UPS, "14"));
    }

    private static ReviewDraft draft(GoalStatus status, int kcal, String date) {
        return new ReviewDraft("Gute Woche, Protein knapp", "Fünf Tage eingetragen.", status, "Trend flach.",
                kcal, 170, date, "Kleiner Schritt.");
    }

    @Test
    void lastWeekStart_isTheMondayBefore() {
        assertThat(service().lastWeekStart()).isEqualTo(MONDAY);
        assertThat(service().aiAvailable()).isTrue();
    }

    @Test
    void noGoal_noReview() {
        assertThat(service().review("michael", MONDAY)).isEmpty();
        verifyNoInteractions(reviewer);
    }

    @Test
    void review_withGoalCheck_storesNumbersTextAndProposal() {
        goalSince(MONDAY.minusWeeks(4));
        logDays(5);
        when(reviewer.review(any(), any(), anyList(), anyList(), anyList(), eq(true)))
                .thenReturn(draft(GoalStatus.STALLED, 1850, "2027-04-30"));

        WeeklyReview review = service().review("michael", MONDAY).orElseThrow();

        assertThat(review.getId()).isNotNull();
        assertThat(review.getFeedbackStatus()).isEqualTo(FeedbackStatus.DONE);
        assertThat(review.getLoggedDays()).isEqualTo(5);
        assertThat(review.getKcalAvg()).isEqualTo(1900);
        assertThat(review.getProteinDays()).isZero();
        assertThat(review.getWaistCm()).isEqualByComparingTo("96.0");
        assertThat(review.getPushUps()).isEqualTo(14);
        assertThat(review.getHeadline()).isEqualTo("Gute Woche, Protein knapp");
        assertThat(review.getFeedback()).isEqualTo("Fünf Tage eingetragen.");
        assertThat(review.isGoalCheck()).isTrue();
        assertThat(review.getGoalStatus()).isEqualTo(GoalStatus.STALLED);
        assertThat(review.needsTalk()).isTrue();
        assertThat(review.getProposedKcalTarget()).isEqualTo(1850);
        assertThat(review.getProposedProteinTargetG()).isEqualTo(170);
        assertThat(review.getProposedTargetDate()).isEqualTo(LocalDate.of(2027, 4, 30));
        assertThat(review.getProposalReason()).isEqualTo("Kleiner Schritt.");
        assertThat(review.hasProposal()).isTrue();
        assertThat(review.getModel()).isEqualTo("claude-opus-5-5");
        assertThat(review.getFeedbackAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 5, 30));
        assertThat(review.weekEnd()).isEqualTo(LocalDate.of(2026, 11, 8));
        // the 4 earlier weeks since the goal began, and the measurements up to Sunday
        verify(reviewer).review(any(), argThat(w -> w.weekStart().equals(MONDAY)),
                argThat(earlier -> earlier.size() == 4), argThat(waist -> waist.equals(List.of("02.11. 96"))),
                argThat(push -> push.size() == 1), eq(true));

        // a finished review is not asked again
        service().review("michael", MONDAY);
        verify(reviewer, times(1)).review(any(), any(), anyList(), anyList(), anyList(), anyBoolean());
    }

    @Test
    void review_withoutGoalCheck_whenTheGoalIsYoung() {
        goalSince(MONDAY.minusWeeks(1));
        logDays(4);
        when(reviewer.review(any(), any(), anyList(), anyList(), anyList(), eq(false)))
                .thenReturn(draft(GoalStatus.TOO_FAST, 1850, ""));

        WeeklyReview review = service().review("michael", MONDAY).orElseThrow();

        assertThat(review.isGoalCheck()).isFalse();
        assertThat(review.getGoalStatus()).isNull();
        assertThat(review.hasProposal()).isFalse();
        assertThat(review.needsTalk()).isFalse();
    }

    @Test
    void review_failedCall_staysPendingAndIsRetried() {
        goalSince(MONDAY.minusWeeks(4));
        logDays(5);
        when(reviewer.review(any(), any(), anyList(), anyList(), anyList(), anyBoolean()))
                .thenThrow(new AiRequestException("Guthaben aufgebraucht"))
                .thenReturn(draft(GoalStatus.ON_TRACK, 0, ""));

        service().runDaily();

        WeeklyReview review = reviewRepository.findByUserUsernameAndWeekStart("michael", MONDAY).orElseThrow();
        assertThat(review.getFeedbackStatus()).isEqualTo(FeedbackStatus.PENDING);
        assertThat(review.getError()).isEqualTo("Guthaben aufgebraucht");
        verify(jobs).success(JobStatusService.HEALTH_REVIEW);
        verify(jobs).failure(eq(JobStatusService.HEALTH_REVIEW_AI), any());

        today = TODAY.plusDays(1);
        service().runDaily();

        review = reviewRepository.findByUserUsernameAndWeekStart("michael", MONDAY).orElseThrow();
        assertThat(review.getFeedbackStatus()).isEqualTo(FeedbackStatus.DONE);
        assertThat(review.getError()).isNull();
        assertThat(review.getGoalStatus()).isEqualTo(GoalStatus.ON_TRACK);
        assertThat(review.hasProposal()).isFalse();
        verify(jobs).success(JobStatusService.HEALTH_REVIEW_AI);
    }

    @Test
    void runDaily_unexpectedError_marksTheJobFailed() {
        goalSince(MONDAY.minusWeeks(4));
        logDays(5);
        when(reviewer.available()).thenThrow(new IllegalStateException("boom"));

        service().runDaily();

        verify(jobs).failure(eq(JobStatusService.HEALTH_REVIEW), any());
        verify(jobs).success(JobStatusService.HEALTH_REVIEW_AI);
    }

    @Test
    void review_noTextWhenPausedTooFewDaysNoKeyOrTooLate() {
        goalSince(MONDAY.minusWeeks(4));
        logDays(2);
        assertThat(service().review("michael", MONDAY).orElseThrow().getFeedbackStatus()).isEqualTo(FeedbackStatus.TOO_FEW_DAYS);
        reviewRepository.deleteAll();

        logDays(5);
        HealthPause pause = new HealthPause();
        pause.setUser(michael);
        pause.setStartDate(TODAY.minusDays(1));
        pauseRepository.save(pause);
        assertThat(service().review("michael", MONDAY).orElseThrow().getFeedbackStatus()).isEqualTo(FeedbackStatus.PAUSED);
        reviewRepository.deleteAll();
        pauseRepository.deleteAll();

        when(reviewer.available()).thenReturn(false);
        assertThat(service().review("michael", MONDAY).orElseThrow().getFeedbackStatus()).isEqualTo(FeedbackStatus.SKIPPED);
        reviewRepository.deleteAll();

        when(reviewer.available()).thenReturn(true);
        today = MONDAY.plusDays(15);
        assertThat(service().review("michael", MONDAY).orElseThrow().getFeedbackStatus()).isEqualTo(FeedbackStatus.SKIPPED);
        verify(reviewer, never()).review(any(), any(), anyList(), anyList(), anyList(), anyBoolean());
    }

    @Test
    void review_mostlyPausedWeek_isPaused() {
        goalSince(MONDAY.minusWeeks(4));
        logDays(5);
        HealthPause pause = new HealthPause();
        pause.setUser(michael);
        pause.setStartDate(MONDAY.minusDays(2));
        pause.setEndDate(MONDAY.plusDays(3));
        pauseRepository.save(pause);

        WeeklyReview review = service().review("michael", MONDAY).orElseThrow();
        assertThat(review.getPausedDays()).isEqualTo(4);
        assertThat(review.getFeedbackStatus()).isEqualTo(FeedbackStatus.PAUSED);
    }

    @Test
    void goalCheckDue_everyFourWeeks() {
        HealthGoal goal = goalSince(MONDAY.minusWeeks(8));
        WeeklyReviewService service = service();
        assertThat(service.goalCheckDue("michael", goal, MONDAY)).isTrue();

        WeeklyReview earlier = new WeeklyReview();
        earlier.setUser(michael);
        earlier.setWeekStart(MONDAY.minusWeeks(2));
        earlier.setGoalCheck(true);
        earlier.setFeedbackStatus(FeedbackStatus.DONE);
        earlier.setCreatedAt(LocalDateTime.now());
        reviewRepository.save(earlier);
        assertThat(service.goalCheckDue("michael", goal, MONDAY)).isFalse();

        earlier.setWeekStart(MONDAY.minusWeeks(4));
        reviewRepository.save(earlier);
        assertThat(service.goalCheckDue("michael", goal, MONDAY)).isTrue();

        HealthGoal young = goal(michael, MONDAY.minusWeeks(2), null, 2000);
        assertThat(service.goalCheckDue("michael", young, MONDAY)).isFalse();
    }

    @Test
    void apply_guardsTheProposal() {
        WeeklyReview review = new WeeklyReview();
        WeeklyReviewService.apply(new ReviewDraft(" ", "Text", null, " ", 0, 0, "", ""), review, true);
        assertThat(review.getHeadline()).isNull();
        assertThat(review.getGoalStatus()).isEqualTo(GoalStatus.ON_TRACK);
        assertThat(review.getGoalReason()).isNull();
        assertThat(review.hasProposal()).isFalse();

        review = new WeeklyReview();
        WeeklyReviewService.apply(new ReviewDraft("x".repeat(300), "Text", GoalStatus.TOO_FAST, "Zu schnell",
                1500, 500, "irgendwann", "Grund"), review, true);
        assertThat(review.getHeadline()).hasSize(255);
        assertThat(review.getProposedKcalTarget()).isNull();
        assertThat(review.getProposedProteinTargetG()).isNull();
        assertThat(review.getProposedTargetDate()).isNull();
        assertThat(review.getProposalReason()).isNull();

        // RECOMPOSITION never carries a proposal
        review = new WeeklyReview();
        WeeklyReviewService.apply(draft(GoalStatus.RECOMPOSITION, 1850, "2027-04-30"), review, true);
        assertThat(review.hasProposal()).isFalse();
        assertThat(review.needsTalk()).isFalse();
    }

    @Test
    void findLatestAndDecide() {
        goalSince(MONDAY.minusWeeks(1));
        logDays(1);
        WeeklyReviewService service = service();
        WeeklyReview review = service.review("michael", MONDAY).orElseThrow();

        assertThat(service.find("michael", MONDAY)).contains(review);
        assertThat(service.find("michael", review.getId())).contains(review);
        assertThat(service.latest("michael")).contains(review);
        assertThat(service.find("other", review.getId())).isEmpty();

        assertThat(service.decide("michael", review.getId(), ReviewDecision.KEEP)).isTrue();
        assertThat(reviewRepository.findById(review.getId()).orElseThrow().getDecision()).isEqualTo(ReviewDecision.KEEP);
        assertThat(reviewRepository.findById(review.getId()).orElseThrow().getDecidedAt()).isNotNull();
        assertThat(service.decide("other", review.getId(), ReviewDecision.TALK)).isFalse();
        assertThat(service.decide("michael", 999L, ReviewDecision.TALK)).isFalse();
        assertThat(Optional.ofNullable(reviewRepository.findById(review.getId()).orElseThrow().getDecision()))
                .contains(ReviewDecision.KEEP);
    }

    @Test
    void daysFilledInLater_stillCount() {
        goalSince(MONDAY.minusWeeks(4));
        logDays(2);
        when(reviewer.review(any(), any(), anyList(), anyList(), anyList(), anyBoolean()))
                .thenReturn(draft(GoalStatus.ON_TRACK, 0, ""));
        assertThat(service().review("michael", MONDAY).orElseThrow().getFeedbackStatus()).isEqualTo(FeedbackStatus.TOO_FEW_DAYS);

        mealRepository.save(meal(michael, MONDAY.plusDays(4), 2000, "160"));
        WeeklyReview review = service().review("michael", MONDAY).orElseThrow();
        assertThat(review.getLoggedDays()).isEqualTo(3);
        assertThat(review.getFeedbackStatus()).isEqualTo(FeedbackStatus.DONE);
    }
}
