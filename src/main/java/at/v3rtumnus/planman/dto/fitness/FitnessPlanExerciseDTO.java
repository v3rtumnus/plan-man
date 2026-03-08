package at.v3rtumnus.planman.dto.fitness;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitnessPlanExerciseDTO {
    private Long id;
    private FitnessExerciseDTO exercise;
    private Integer orderIndex;
    private Integer targetSets;
    private Integer targetReps;
    private Integer targetDurationSeconds;
    private Integer targetDistanceMeters;
    private Integer targetDurationRunSeconds;
    private Integer restSeconds;
    private String notes;
}
