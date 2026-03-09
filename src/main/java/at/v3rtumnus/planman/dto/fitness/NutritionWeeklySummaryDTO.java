package at.v3rtumnus.planman.dto.fitness;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NutritionWeeklySummaryDTO {
    private int daysLogged;
    private Integer avgCalories;
    private Integer avgProteinG;
    private Integer avgCarbsG;
    private Integer avgFatG;
    private Integer targetCalories;
    private Integer targetProteinG;
    private Integer targetCarbsG;
    private Integer targetFatG;
}
