package at.v3rtumnus.planman.dto.health;

import java.math.BigDecimal;

/**
 * @param percent            share of the way from start to target weight, 0–100
 * @param requiredWeeklyRate weekly change needed to reach the target weight on the target date
 */
public record GoalProgress(BigDecimal currentKg, int percent, BigDecimal requiredWeeklyRate,
                           BigDecimal actualWeeklyRate) {
}
