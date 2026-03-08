package at.v3rtumnus.planman.dto.fitness;

import at.v3rtumnus.planman.entity.fitness.ActivityLevel;
import at.v3rtumnus.planman.entity.fitness.BiologicalSex;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitnessHealthProfileDTO {
    private Integer heightCm;
    private Integer birthYear;
    private BiologicalSex biologicalSex;
    private ActivityLevel activityLevel;
    private BigDecimal targetWeightKg;
    private Integer targetProteinG;
    private Integer targetCarbsG;
}
