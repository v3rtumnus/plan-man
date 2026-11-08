package at.v3rtumnus.planman.entity.health;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** An item with grams and per-100 g values; totals are derived, never stored on the item. */
public interface FoodValues {

    String getName();

    int getGrams();

    BigDecimal getKcalPer100g();

    BigDecimal getProteinPer100g();

    BigDecimal getCarbsPer100g();

    BigDecimal getFatPer100g();

    default int kcal() {
        return scaled(getKcalPer100g()).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    default BigDecimal protein() {
        return scaled(getProteinPer100g());
    }

    default BigDecimal carbs() {
        return scaled(getCarbsPer100g());
    }

    default BigDecimal fat() {
        return scaled(getFatPer100g());
    }

    private BigDecimal scaled(BigDecimal per100g) {
        return per100g.multiply(BigDecimal.valueOf(getGrams()))
                .divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP);
    }
}
