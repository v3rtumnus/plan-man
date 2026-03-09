package at.v3rtumnus.planman.dto.fitness;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitnessProgressDTO {
    private List<String> weekLabels;
    private List<Integer> trainingDaysPerWeek;
    private List<Double> runningKmPerWeek;
    private List<Double> avgDifficultyPerWeek;
    private Integer totalSessionsCompleted;
    private Integer totalSessionsSkipped;
    private Double totalRunningKm;
    private Integer currentStreak;
    private Integer longestStreak;
}
