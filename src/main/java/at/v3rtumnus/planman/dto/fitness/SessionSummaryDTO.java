package at.v3rtumnus.planman.dto.fitness;

import at.v3rtumnus.planman.entity.fitness.SessionStatus;
import at.v3rtumnus.planman.entity.fitness.SessionType;
import at.v3rtumnus.planman.entity.fitness.SkipReason;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummaryDTO {
    private Long id;
    private LocalDate logDate;
    private SessionType sessionType;
    private SessionStatus status;
    private SkipReason skipReason;
    private String skipNotes;
    private Integer actualDurationMinutes;
    private Integer difficultyRating;
    private String feedbackText;
    private Integer externalCaloriesBurned;
    private Integer totalDistanceMeters;
    private String paceDisplay;
}
