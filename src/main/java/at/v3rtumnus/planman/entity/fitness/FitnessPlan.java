package at.v3rtumnus.planman.entity.fitness;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fitness_plan")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitnessPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fitness_profile_id", nullable = false)
    private FitnessProfile fitnessProfile;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_reason", length = 50)
    private PlanGenerationReason generationReason;

    @Column(name = "ai_notes", columnDefinition = "TEXT")
    private String aiNotes;

    @Column(name = "exercise_refresh", nullable = false)
    private boolean exerciseRefresh;

    @Column(name = "start_date")
    private LocalDate startDate;
}
