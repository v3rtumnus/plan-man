package at.v3rtumnus.planman.entity.health;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public enum MeasurementType {
    WEIGHT("Gewicht", "kg", 1, 1, 30, 300),
    WAIST("Taille", "cm", 14, 1, 40, 200),
    PUSH_UPS("Liegestütze am Stück", "", 28, 0, 0, 500);

    private final String label;
    private final String unit;
    /** How often the measurement is due, in days. */
    private final int intervalDays;
    private final int fractionDigits;
    private final int min;
    private final int max;

    public boolean accepts(BigDecimal value) {
        return value != null
                && value.compareTo(BigDecimal.valueOf(min)) >= 0
                && value.compareTo(BigDecimal.valueOf(max)) <= 0;
    }
}
