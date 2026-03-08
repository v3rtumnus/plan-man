package at.v3rtumnus.planman.entity.fitness;

import at.v3rtumnus.planman.entity.UserProfile;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fitness_profile")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitnessProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "username", nullable = false)
    private UserProfile userProfile;

    @Column(nullable = false)
    private boolean assessmentCompleted;

    @Column(name = "current_plan_id")
    private Long currentPlanId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "height_cm")
    private Integer heightCm;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "biological_sex", length = 10)
    private BiologicalSex biologicalSex;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", length = 20)
    private ActivityLevel activityLevel;

    @Column(name = "target_weight_kg", precision = 5, scale = 2)
    private BigDecimal targetWeightKg;

    @Column(name = "target_protein_g")
    private Integer targetProteinG;

    @Column(name = "target_carbs_g")
    private Integer targetCarbsG;
}
