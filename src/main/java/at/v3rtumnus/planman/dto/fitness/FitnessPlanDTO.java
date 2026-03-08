package at.v3rtumnus.planman.dto.fitness;

import at.v3rtumnus.planman.entity.fitness.PlanGenerationReason;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitnessPlanDTO {
    private Long id;
    private Integer version;
    private LocalDateTime generatedAt;
    private boolean active;
    private PlanGenerationReason generationReason;
    private String aiNotes;
    private List<FitnessPlanSessionDTO> sessions;
}
