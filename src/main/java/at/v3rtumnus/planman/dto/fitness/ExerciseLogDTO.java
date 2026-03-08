package at.v3rtumnus.planman.dto.fitness;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseLogDTO {
    private Long exerciseId;
    private Integer orderIndex;
    private Integer setNumber;
    private Integer repsDone;
    private Integer durationSeconds;
    private Integer distanceMeters;
    private Integer durationRunSeconds;
    private String notes;
}
