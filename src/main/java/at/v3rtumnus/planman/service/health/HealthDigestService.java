package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.HealthGoalRepository;
import at.v3rtumnus.planman.dao.HealthPauseRepository;
import at.v3rtumnus.planman.dto.health.DaySummary;
import at.v3rtumnus.planman.dto.health.HealthDigest;
import at.v3rtumnus.planman.dto.health.WeightTrend;
import at.v3rtumnus.planman.dto.notification.DigestDTO;
import at.v3rtumnus.planman.entity.health.FeedbackStatus;
import at.v3rtumnus.planman.entity.health.HealthGoal;
import at.v3rtumnus.planman.entity.health.WeeklyReview;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** The health line of the Morning Briefing: yesterday, the weight trend and last week's review. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthDigestService {

    private final HealthGoalRepository goals;
    private final HealthPauseRepository pauses;
    private final HealthHistoryService history;
    private final WeeklyReviewService reviews;
    private final Clock clock;

    /** Adds each user's health part to the notification digest. */
    public DigestDTO withHealth(DigestDTO digest) {
        Map<String, DigestDTO.UserDigest> users = new LinkedHashMap<>();
        digest.users().forEach((username, user) -> users.put(username,
                new DigestDTO.UserDigest(user.unread(), user.notifications(), digest(username).orElse(null))));
        return new DigestDTO(digest.generatedAt(), users, digest.depot());
    }

    public Optional<HealthDigest> digest(String username) {
        LocalDate today = LocalDate.now(clock);
        Optional<HealthGoal> goal = goals.findGoalFor(username, today);
        if (goal.isEmpty()) {
            return Optional.empty();
        }
        LocalDate yesterday = today.minusDays(1);
        DaySummary day = history.days(username, yesterday, yesterday).getFirst();
        WeightTrend trend = history.weightTrend(username, today);
        boolean paused = pauses.findActive(username, today).isPresent();
        HealthDigest.Review review = reviews.latest(username)
                .filter(r -> r.getFeedbackStatus() == FeedbackStatus.DONE)
                .filter(r -> r.getWeekStart().equals(HealthHistoryService.mondayOf(today).minusWeeks(1)))
                .map(r -> review(r, LocalDateTime.now(clock)))
                .orElse(null);
        return Optional.of(new HealthDigest(yesterday, paused, day.meals(), day.kcalEaten(), day.kcalTarget(),
                day.proteinG().setScale(0, RoundingMode.HALF_UP), day.proteinTargetG(), trend.trendKg(),
                trend.weeklyRateKg(), goal.get().getTargetWeightKg(), line(day, trend, paused), review));
    }

    private static HealthDigest.Review review(WeeklyReview r, LocalDateTime now) {
        return new HealthDigest.Review(r.getWeekStart(), r.getHeadline(), r.getFeedback(),
                r.getGoalStatus() != null ? r.getGoalStatus().name() : null, r.needsTalk() && r.getDecision() == null,
                r.getFeedbackAt() != null && r.getFeedbackAt().isAfter(now.minusHours(24)),
                HealthReviewNotifications.weekUrl(r));
    }

    static String line(DaySummary day, WeightTrend trend, boolean paused) {
        if (paused) {
            return "Gesundheit: Pause aktiv.";
        }
        StringBuilder line = new StringBuilder();
        if (day.meals() == 0) {
            line.append("Gestern nichts eingetragen");
        } else {
            line.append("Gestern ").append(number(day.kcalEaten()));
            if (day.kcalTarget() != null) {
                line.append(" / ").append(number(day.kcalTarget()));
            }
            line.append(" kcal, ").append(day.proteinG().setScale(0, RoundingMode.HALF_UP));
            if (day.proteinTargetG() != null) {
                line.append(" / ").append(day.proteinTargetG());
            }
            line.append(" g Protein");
        }
        if (trend.trendKg() != null) {
            line.append(" · Trend ").append(decimal(trend.trendKg(), 1)).append(" kg");
            if (trend.weeklyRateKg() != null) {
                line.append(" (").append(trend.weeklyRateKg().signum() > 0 ? "+" : "")
                        .append(decimal(trend.weeklyRateKg(), 1)).append(" kg/Woche)");
            }
        }
        return line.append('.').toString();
    }

    private static String number(int value) {
        return String.format(Locale.GERMAN, "%,d", value);
    }

    private static String decimal(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
