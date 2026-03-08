package at.v3rtumnus.planman.dto.fitness;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MarkDoneRequestDTO {
    private Integer difficultyRating;
    private String notes;
}
