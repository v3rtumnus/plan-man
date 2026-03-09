package at.v3rtumnus.planman.dto.fitness;

import at.v3rtumnus.planman.entity.fitness.ExerciseCategory;
import at.v3rtumnus.planman.entity.fitness.ExerciseTrackingType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonalRecordDTO {
    private Long exerciseId;
    private String exerciseName;
    private ExerciseCategory category;
    private ExerciseTrackingType trackingType;
    private Integer bestReps;
    private Integer bestDurationSeconds;
    private Integer bestDistanceMeters;
    private LocalDate achievedDate;
    private int totalSessions;
}
