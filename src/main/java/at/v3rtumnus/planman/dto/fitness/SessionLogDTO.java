package at.v3rtumnus.planman.dto.fitness;

import at.v3rtumnus.planman.entity.fitness.SessionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionLogDTO {
    private Long planSessionId;
    private LocalDate logDate;
    private SessionType sessionType;
    private Integer actualDurationMinutes;
    private Integer difficultyRating;
    private String feedbackText;
    private List<ExerciseLogDTO> exercises;
}
