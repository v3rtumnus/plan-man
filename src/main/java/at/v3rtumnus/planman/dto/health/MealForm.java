package at.v3rtumnus.planman.dto.health;

import at.v3rtumnus.planman.entity.health.MealSlot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The review form: a draft from the model, a manual entry or an existing meal being edited. */
@Data
@NoArgsConstructor
public class MealForm {

    /** Set when an existing meal is edited. */
    private Long mealId;
    /** Set when the draft came from the model. */
    private Long recognitionId;
    @NotNull
    private MealSlot slot;
    @NotNull
    private LocalDate date;
    @NotBlank
    @Size(max = 255)
    private String title;
    /** Set when the draft came from a scanned barcode (H7). */
    @Size(max = 32)
    private String barcode;

    /** Keep as a favourite for "Schnell wieder". */
    private boolean remember;
    @Valid
    @NotEmpty
    private List<MealItemForm> items = new ArrayList<>();
}
