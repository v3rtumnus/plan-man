package at.v3rtumnus.planman.dto.health;

import at.v3rtumnus.planman.entity.health.MeasurementType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param first first value since the current goal started (or ever, without a goal)
 * @param due   true when the last value is older than the type's interval, or there is none
 */
public record MeasurementStatus(MeasurementType type, BigDecimal first, BigDecimal latest, LocalDate latestDate,
                                boolean due, Integer target) {
}
