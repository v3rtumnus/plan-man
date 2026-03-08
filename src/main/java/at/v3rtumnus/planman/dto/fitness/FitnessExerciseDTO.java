package at.v3rtumnus.planman.dto.fitness;

import at.v3rtumnus.planman.entity.fitness.Equipment;
import at.v3rtumnus.planman.entity.fitness.ExerciseCategory;
import at.v3rtumnus.planman.entity.fitness.ExerciseTrackingType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitnessExerciseDTO {
    private Long id;
    private String name;
    private String description;
    private ExerciseCategory category;
    private ExerciseTrackingType trackingType;
    private Equipment equipment;
    private Integer difficulty;
    private String imageUrl;
    private String videoUrl;
}
