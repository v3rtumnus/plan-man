package at.v3rtumnus.planman.dto.health;

import at.v3rtumnus.planman.entity.health.FoodValues;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MealItemForm implements FoodValues {

    @NotBlank
    @Size(max = 255)
    private String name;
    @Min(0)
    @Max(5000)
    private int grams;
    @NotNull
    @DecimalMin("0")
    @DecimalMax("900")
    private BigDecimal kcalPer100g;
    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal proteinPer100g;
    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal carbsPer100g;
    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal fatPer100g;
    private boolean unsure;
    @Size(max = 255)
    private String portionHint;

    public static MealItemForm of(FoodValues values, String name, boolean unsure, String portionHint) {
        return new MealItemForm(name, values.getGrams(), values.getKcalPer100g(), values.getProteinPer100g(),
                values.getCarbsPer100g(), values.getFatPer100g(), unsure, portionHint);
    }

    /** An empty row for "+ Zutat ergänzen" and manual entry. */
    public static MealItemForm empty() {
        return new MealItemForm("", 100, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                false, null);
    }
}
