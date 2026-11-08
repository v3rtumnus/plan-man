package at.v3rtumnus.planman.dto.health;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.Locale;

/** One row of *Hantelgewichte*; tipKg is the suggested next weight, or null. */
public record ExerciseView(Long id, String name, BigDecimal weightKg, LocalDate since, BigDecimal tipKg) {

    static final BigDecimal STEP = new BigDecimal("2.5");
    static final BigDecimal MIN = new BigDecimal("1.25");

    public BigDecimal lighterKg() {
        return weightKg == null ? null : weightKg.subtract(STEP).max(MIN);
    }

    public BigDecimal heavierKg() {
        return weightKg == null ? STEP : weightKg.add(STEP);
    }

    public String weightLabel() {
        return weightKg == null ? "–" : format(weightKg) + " kg";
    }

    public String tipLabel() {
        return tipKg == null ? null : format(tipKg) + " kg";
    }

    /** 12.50 → "12,5", 15.00 → "15", 11.25 → "11,25". */
    static String format(BigDecimal kg) {
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.GERMAN)).format(kg);
    }
}
