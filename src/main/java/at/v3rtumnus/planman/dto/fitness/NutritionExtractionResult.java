package at.v3rtumnus.planman.dto.fitness;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NutritionExtractionResult {
    private Integer calories;
    private Integer proteinG;
    private Integer carbsG;
    private Integer fatG;
    private String notes;
}
