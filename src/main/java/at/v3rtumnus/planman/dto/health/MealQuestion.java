package at.v3rtumnus.planman.dto.health;

import java.math.BigDecimal;
import java.util.List;

/** The model's one tap-answer question; each option replaces one item's per-100 g values. */
public record MealQuestion(String text, int itemIndex, List<Option> options) {

    public record Option(String label, BigDecimal kcalPer100g, BigDecimal proteinPer100g,
                         BigDecimal carbsPer100g, BigDecimal fatPer100g) {
    }
}
