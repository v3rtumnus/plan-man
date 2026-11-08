package at.v3rtumnus.planman.dto.health;

import java.math.BigDecimal;

/**
 * One row in "Schnell wieder": a saved meal (savedMealId) or "wie gestern" (mealId).
 */
public record QuickMeal(Long savedMealId, Long mealId, String name, String detail, int kcal, BigDecimal proteinG) {
}
