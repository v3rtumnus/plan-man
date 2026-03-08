package at.v3rtumnus.planman.dto.fitness;

import at.v3rtumnus.planman.entity.fitness.SessionStatus;
import at.v3rtumnus.planman.entity.fitness.SessionType;
import at.v3rtumnus.planman.entity.fitness.SkipReason;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitnessPlanSessionDTO {
    private Long id;
    private Integer sessionNumber;
    private Integer weekNumber;
    private SessionType sessionType;
    private Integer estimatedDurationMinutes;
    private String description;
    private List<FitnessPlanExerciseDTO> exercises;
    private SessionStatus status;
    private SkipReason skipReason;
}
