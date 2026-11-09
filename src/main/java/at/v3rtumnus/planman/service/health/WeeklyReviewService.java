package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.BodyMeasurementRepository;
import at.v3rtumnus.planman.dao.HealthGoalRepository;
import at.v3rtumnus.planman.dao.HealthPauseRepository;
import at.v3rtumnus.planman.dao.UserProfileRepository;
import at.v3rtumnus.planman.dao.WeeklyReviewRepository;
import at.v3rtumnus.planman.dto.health.WeekSummary;
import at.v3rtumnus.planman.dto.health.ai.ReviewDraft;
import at.v3rtumnus.planman.entity.health.*;
import at.v3rtumnus.planman.service.JobStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Writes one {@code weekly_review} per user and week, the Monday after the week. Runs daily:
 * a Monday that failed (API down, credits used up) is retried on the following days until the
 * text arrives, so the "plan-man job stale" alert only sees a real, lasting failure.
 * <p>
 * Every 4 weeks the review also checks the goal. A target change is only ever proposed.
 */
@Service
@Slf4j
public class WeeklyReviewService {

    /** Fewer logged days and there is nothing to judge. */
    static final int MIN_LOGGED_DAYS = 3;
    /** This many paused days and the week counts as paused. */
    static final int PAUSED_WEEK_DAYS = 4;
    static final int GOAL_CHECK_WEEKS = 4;
    static final int HISTORY_WEEKS = 12;

    private final WeeklyReviewRepository reviews;
    private final HealthGoalRepository goals;
    private final HealthPauseRepository pauses;
    private final BodyMeasurementRepository measurements;
    private final UserProfileRepository users;
    private final HealthHistoryService history;
    private final AnthropicWeeklyReviewer reviewer;
    private final JobStatusService jobs;
    private final Clock clock;

    public WeeklyReviewService(WeeklyReviewRepository reviews, HealthGoalRepository goals, HealthPauseRepository pauses,
                               BodyMeasurementRepository measurements, UserProfileRepository users,
                               HealthHistoryService history, AnthropicWeeklyReviewer reviewer, JobStatusService jobs,
                               Clock clock) {
        this.reviews = reviews;
        this.goals = goals;
        this.pauses = pauses;
        this.measurements = measurements;
        this.users = users;
        this.history = history;
        this.reviewer = reviewer;
        this.jobs = jobs;
        this.clock = clock;
    }

    public boolean aiAvailable() {
        return reviewer.available();
    }

    public LocalDate lastWeekStart() {
        return HealthHistoryService.mondayOf(LocalDate.now(clock)).minusWeeks(1);
    }

    @Scheduled(cron = "${planman.health.review-cron:0 30 5 * * *}")
    public void runDaily() {
        Exception reviewError = null;
        Exception aiError = null;
        for (String username : goals.findUsernamesWithGoal()) {
            try {
                Optional<WeeklyReview> review = review(username, lastWeekStart());
                if (review.isPresent() && review.get().getFeedbackStatus() == FeedbackStatus.PENDING
                        && review.get().getError() != null && aiError == null) {
                    aiError = new IllegalStateException(review.get().getError());
                }
            } catch (Exception e) {
                log.error("Weekly review for {} failed", username, e);
                if (reviewError == null) {
                    reviewError = e;
                }
            }
        }
        if (reviewError == null) {
            jobs.success(JobStatusService.HEALTH_REVIEW);
        } else {
            jobs.failure(JobStatusService.HEALTH_REVIEW, reviewError);
        }
        if (aiError == null) {
            jobs.success(JobStatusService.HEALTH_REVIEW_AI);
        } else {
            jobs.failure(JobStatusService.HEALTH_REVIEW_AI, aiError);
        }
    }

    /**
     * Creates or refreshes the review of the week starting {@code monday}. Until the text is
     * there, every run recomputes the numbers and the reason for no text, so days filled in later
     * (◀ on Heute) still count; a review with text stays as it is. Empty without a goal on the
     * week's last day.
     */
    public Optional<WeeklyReview> review(String username, LocalDate monday) {
        LocalDate sunday = monday.plusDays(6);
        Optional<HealthGoal> goal = goals.findGoalFor(username, sunday);
        if (goal.isEmpty()) {
            return Optional.empty();
        }
        WeeklyReview review = reviews.findByUserUsernameAndWeekStart(username, monday).orElseGet(() -> {
            WeeklyReview created = new WeeklyReview();
            created.setUser(users.findByUsername(username).orElseThrow());
            created.setWeekStart(monday);
            created.setCreatedAt(LocalDateTime.now(clock));
            return created;
        });
        if (review.getFeedbackStatus() == FeedbackStatus.DONE) {
            return Optional.of(review);
        }
        review.setFeedbackStatus(FeedbackStatus.PENDING);
        WeekSummary week = history.week(username, monday);
        copyNumbers(week, review);

        LocalDate today = LocalDate.now(clock);
        if (week.pausedDays() >= PAUSED_WEEK_DAYS || pauses.findActive(username, today).isPresent()) {
            review.setFeedbackStatus(FeedbackStatus.PAUSED);
        } else if (week.loggedDays() < MIN_LOGGED_DAYS) {
            review.setFeedbackStatus(FeedbackStatus.TOO_FEW_DAYS);
        } else if (!reviewer.available() || today.isAfter(sunday.plusDays(7))) {
            review.setFeedbackStatus(FeedbackStatus.SKIPPED);
        } else {
            askModel(username, goal.get(), week, review);
        }
        return Optional.of(reviews.save(review));
    }

    private void askModel(String username, HealthGoal goal, WeekSummary week, WeeklyReview review) {
        boolean goalCheck = goalCheckDue(username, goal, week.weekStart());
        List<WeekSummary> earlier = new ArrayList<>();
        for (int i = 1; i < HISTORY_WEEKS; i++) {
            LocalDate monday = week.weekStart().minusWeeks(i);
            if (monday.plusDays(6).isBefore(goal.getValidFrom())) {
                break;
            }
            earlier.add(history.week(username, monday));
        }
        try {
            ReviewDraft draft = reviewer.review(goal, week, earlier,
                    measurementLines(username, MeasurementType.WAIST, week.weekEnd()),
                    measurementLines(username, MeasurementType.PUSH_UPS, week.weekEnd()), goalCheck);
            apply(draft, review, goalCheck);
            review.setModel(reviewer.model());
            review.setFeedbackStatus(FeedbackStatus.DONE);
            review.setFeedbackAt(LocalDateTime.now(clock));
            review.setError(null);
        } catch (AiRequestException e) {
            log.warn("Weekly review text for {} week {} failed: {}", username, week.weekStart(), e.getMessage());
            review.setError(truncate(e.getMessage(), 500));
        }
    }

    /** The goal has run 4 weeks and the last check is at least 4 weeks back. */
    boolean goalCheckDue(String username, HealthGoal goal, LocalDate monday) {
        if (goal.getValidFrom().isAfter(monday.minusWeeks(GOAL_CHECK_WEEKS - 1L))) {
            return false;
        }
        return reviews.findFirstByUserUsernameAndGoalCheckTrueOrderByWeekStartDesc(username)
                .filter(last -> !last.getWeekStart().equals(monday))
                .map(last -> !last.getWeekStart().isAfter(monday.minusWeeks(GOAL_CHECK_WEEKS)))
                .orElse(true);
    }

    static void apply(ReviewDraft draft, WeeklyReview review, boolean goalCheck) {
        review.setHeadline(truncate(blankToNull(draft.headline()), 255));
        review.setFeedback(blankToNull(draft.feedback()));
        review.setGoalCheck(goalCheck);
        if (!goalCheck) {
            return;
        }
        review.setGoalStatus(draft.goalStatus() != null ? draft.goalStatus() : GoalStatus.ON_TRACK);
        review.setGoalReason(blankToNull(draft.goalReason()));
        // A proposal only makes sense when the check found a problem; the floor guards the prompt's rule.
        if (review.getGoalStatus().needsTalk()) {
            review.setProposedKcalTarget(draft.proposedKcalTarget() >= 1700 && draft.proposedKcalTarget() <= 4000
                    ? draft.proposedKcalTarget() : null);
            review.setProposedProteinTargetG(draft.proposedProteinTargetG() > 0 && draft.proposedProteinTargetG() <= 400
                    ? draft.proposedProteinTargetG() : null);
            review.setProposedTargetDate(parseDate(draft.proposedTargetDate()));
            review.setProposalReason(review.hasProposal() ? blankToNull(draft.proposalReason()) : null);
        }
    }

    private List<String> measurementLines(String username, MeasurementType type, LocalDate until) {
        return measurements.findByUserUsernameAndTypeOrderByMeasuredOnAsc(username, type).stream()
                .filter(m -> !m.getMeasuredOn().isAfter(until))
                .map(m -> AnthropicWeeklyReviewer.measurement(m.getMeasuredOn(), m.getMeasuredValue()))
                .toList();
    }

    static void copyNumbers(WeekSummary week, WeeklyReview review) {
        review.setLoggedDays(week.loggedDays());
        review.setPausedDays(week.pausedDays());
        review.setKcalAvg(week.kcalAvg());
        review.setKcalTargetAvg(week.kcalTargetAvg());
        review.setProteinAvgG(week.proteinAvgG());
        review.setProteinTargetG(week.proteinTargetG());
        review.setProteinDays(week.proteinDays());
        review.setFreeleticsSessions(week.freeleticsSessions());
        review.setFootballPlayed(week.footballPlayed());
        review.setTrainingKcal(week.trainingKcal());
        review.setWeightTrendKg(week.weightTrendKg());
        review.setWeightChangeKg(week.weightChangeKg());
        review.setWeeklyRateKg(week.weeklyRateKg());
        review.setWaistCm(week.waistCm());
        review.setWaistChangeCm(week.waistChangeCm());
        review.setPushUps(week.pushUps());
    }

    // --- reading and deciding ---

    public Optional<WeeklyReview> find(String username, LocalDate monday) {
        return reviews.findByUserUsernameAndWeekStart(username, monday);
    }

    public Optional<WeeklyReview> find(String username, Long id) {
        return reviews.findByIdAndUserUsername(id, username);
    }

    public Optional<WeeklyReview> latest(String username) {
        return reviews.findFirstByUserUsernameOrderByWeekStartDesc(username);
    }

    /** "Gespräch führen", "Passt so" or an accepted proposal; returns false if the review is not the user's. */
    public boolean decide(String username, Long reviewId, ReviewDecision decision) {
        return reviews.findByIdAndUserUsername(reviewId, username).map(review -> {
            review.setDecision(decision);
            review.setDecidedAt(LocalDateTime.now(clock));
            reviews.save(review);
            return true;
        }).orElse(false);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
