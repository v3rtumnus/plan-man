package at.v3rtumnus.planman.dto.health;

import at.v3rtumnus.planman.entity.health.Meal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * What was eaten on a day against the day's targets (targets null without a goal). The kcal
 * target is the goal's base plus what the day's logged training adds.
 */
public record NutritionDay(Integer baseKcalTarget, int trainingKcal, Integer proteinTargetG, int kcalEaten,
                           BigDecimal proteinEatenG, List<Meal> meals) {

    public Integer kcalTarget() {
        return baseKcalTarget == null ? null : baseKcalTarget + trainingKcal;
    }

    public Integer kcalLeft() {
        return baseKcalTarget == null ? null : kcalTarget() - kcalEaten;
    }

    public Integer proteinLeftG() {
        return proteinTargetG == null ? null
                : Math.max(0, proteinTargetG - proteinEatenG.setScale(0, RoundingMode.HALF_UP).intValue());
    }

    /** 0–100, for the progress bars. */
    public int kcalPercent() {
        return percent(kcalEaten, kcalTarget());
    }

    public int proteinPercent() {
        return percent(proteinEatenG.intValue(), proteinTargetG);
    }

    private static int percent(int value, Integer target) {
        if (target == null || target <= 0) {
            return 0;
        }
        return Math.min(100, Math.round(value * 100f / target));
    }
}
