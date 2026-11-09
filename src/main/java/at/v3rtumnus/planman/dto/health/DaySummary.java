package at.v3rtumnus.planman.dto.health;

import at.v3rtumnus.planman.entity.health.Workout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * One day for the week view, the weekly review and the digest. The base target is the day's
 * snapshot (or the goal in force then); training adds to it as on Heute.
 */
public record DaySummary(LocalDate date, Integer baseKcalTarget, int trainingKcal, Integer proteinTargetG,
                         int kcalEaten, BigDecimal proteinG, int meals, BigDecimal weightKg, boolean paused,
                         List<Workout> workouts) {

    public Integer kcalTarget() {
        return baseKcalTarget == null ? null : baseKcalTarget + trainingKcal;
    }

    /** Counts for averages: something was eaten and logged, and no pause. */
    public boolean logged() {
        return meals > 0 && !paused;
    }

    public boolean proteinReached() {
        return proteinTargetG != null && proteinG.setScale(0, RoundingMode.HALF_UP).intValue() >= proteinTargetG;
    }

    public int kcalPercent() {
        Integer target = kcalTarget();
        return target == null || target <= 0 ? 0 : Math.min(100, Math.round(kcalEaten * 100f / target));
    }

    public boolean overTarget() {
        Integer target = kcalTarget();
        return target != null && kcalEaten > target;
    }

    /** "Mo", "Di", … */
    public String dayLabel() {
        return date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.GERMAN).replace(".", "");
    }
}
