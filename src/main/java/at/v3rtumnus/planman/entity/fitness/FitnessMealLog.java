package at.v3rtumnus.planman.entity.fitness;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fitness_meal_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitnessMealLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fitness_profile_id", nullable = false)
    private FitnessProfile fitnessProfile;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "meal_text", nullable = false, columnDefinition = "TEXT")
    private String mealText;

    @Column(name = "ai_calories")
    private Integer aiCalories;

    @Column(name = "ai_protein_g")
    private Integer aiProteinG;

    @Column(name = "ai_carbs_g")
    private Integer aiCarbsG;

    @Column(name = "ai_fat_g")
    private Integer aiFatG;

    @Column(name = "ai_notes", columnDefinition = "TEXT")
    private String aiNotes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
