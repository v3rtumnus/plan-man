package at.v3rtumnus.planman.dto.fitness;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryReadinessDTO {
    private String status;   // READY, TAKE_IT_EASY, REST_RECOMMENDED, EASE_BACK_IN
    private String label;
    private String message;
    private String color;    // Bootstrap color: success, warning, danger, info
}
