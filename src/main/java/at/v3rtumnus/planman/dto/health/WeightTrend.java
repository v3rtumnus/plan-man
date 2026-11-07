package at.v3rtumnus.planman.dto.health;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param trendKg      mean of the last 7 days, null without a weight in that window
 * @param weeklyRateKg change per week (negative = losing), null until there is enough data
 */
public record WeightTrend(BigDecimal trendKg, BigDecimal weeklyRateKg, BigDecimal latestKg, LocalDate latestDate) {

    public static final WeightTrend EMPTY = new WeightTrend(null, null, null, null);
}
