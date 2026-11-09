package at.v3rtumnus.planman.dto.health;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The numbers of one week (Monday to Sunday) as the weekly review stores them.
 *
 * @param weightChangeKg trend on the last day minus trend on the day before the week
 * @param waistChangeCm  latest waist minus the first waist since the goal started
 */
public record WeekSummary(LocalDate weekStart, List<DaySummary> days, int loggedDays, int pausedDays,
                          Integer kcalAvg, Integer kcalTargetAvg, BigDecimal proteinAvgG, Integer proteinTargetG,
                          int proteinDays, int freeleticsSessions, boolean footballPlayed, int trainingKcal,
                          BigDecimal weightTrendKg, BigDecimal weightChangeKg, BigDecimal weeklyRateKg,
                          BigDecimal waistCm, BigDecimal waistChangeCm, Integer pushUps) {

    public LocalDate weekEnd() {
        return weekStart.plusDays(6);
    }
}
