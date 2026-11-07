package at.v3rtumnus.planman.dto.health;

import at.v3rtumnus.planman.entity.health.HealthGoal;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
public class GoalForm {

    @NotBlank
    @Size(max = 255)
    private String title = "Fett verlieren + Kraft";

    @NotNull @Min(800) @Max(6000)
    private Integer kcalTarget = 2000;

    @NotNull @Min(0) @Max(400)
    private Integer proteinTargetG = 160;

    @NotNull @Min(0) @Max(2000)
    private Integer footballBonusKcal = 250;

    @NotNull @Min(0) @Max(2000)
    private Integer freeleticsBonusKcal = 100;

    @NotNull @Min(0) @Max(100000)
    private Integer stepsTarget = 8000;

    @NotNull @DecimalMin("30") @DecimalMax("300")
    private BigDecimal startWeightKg;

    @NotNull @DecimalMin("30") @DecimalMax("300")
    private BigDecimal targetWeightKg;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate targetDate;

    @Min(0) @Max(500)
    private Integer pushUpsTarget = 25;

    public static GoalForm from(HealthGoal goal) {
        GoalForm form = new GoalForm();
        form.setTitle(goal.getTitle());
        form.setKcalTarget(goal.getKcalTarget());
        form.setProteinTargetG(goal.getProteinTargetG());
        form.setFootballBonusKcal(goal.getFootballBonusKcal());
        form.setFreeleticsBonusKcal(goal.getFreeleticsBonusKcal());
        form.setStepsTarget(goal.getStepsTarget());
        form.setStartWeightKg(goal.getStartWeightKg());
        form.setTargetWeightKg(goal.getTargetWeightKg());
        form.setTargetDate(goal.getTargetDate());
        form.setPushUpsTarget(goal.getPushUpsTarget());
        return form;
    }
}
