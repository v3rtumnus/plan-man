package at.v3rtumnus.planman.dto.fitness;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MealLogDTO {
    private LocalDate logDate;
    private String mealText;
    private Integer aiCalories;
    private Integer aiProteinG;
    private Integer aiCarbsG;
    private Integer aiFatG;
    private String aiNotes;
    private Integer dailyCalorieTarget;
    private Integer targetProteinG;
    private Integer targetCarbsG;
    private Integer targetFatG;
    private Integer calorieDelta;
    private Integer proteinDelta;
    private Integer carbsDelta;
    private Integer fatDelta;
    private Integer externalCaloriesBurned;
}
