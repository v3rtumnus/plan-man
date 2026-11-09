package at.v3rtumnus.planman.dto.health;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The health part of {@code /api/digest}: yesterday against its targets, the weight trend and the
 * latest weekly review. {@code line} is the ready-made sentence for the Morning Briefing.
 *
 * @param review latest review with feedback; {@code fresh} = its text arrived in the last 24 h
 */
public record HealthDigest(LocalDate date, boolean paused, int meals, int kcalEaten, Integer kcalTarget,
                           BigDecimal proteinG, Integer proteinTargetG, BigDecimal weightTrendKg,
                           BigDecimal weeklyRateKg, BigDecimal targetWeightKg, String line, Review review) {

    public record Review(LocalDate weekStart, String headline, String feedback, String goalStatus,
                         boolean talkSuggested, boolean fresh, String url) {
    }
}
