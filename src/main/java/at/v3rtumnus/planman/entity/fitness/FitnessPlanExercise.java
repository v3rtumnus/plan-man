package at.v3rtumnus.planman.entity.fitness;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fitness_plan_exercise")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitnessPlanExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "plan_session_id", nullable = false)
    private FitnessPlanSession planSession;

    @ManyToOne
    @JoinColumn(name = "exercise_id", nullable = false)
    private FitnessExercise exercise;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "target_sets")
    private Integer targetSets;

    @Column(name = "target_reps")
    private Integer targetReps;

    @Column(name = "target_duration_seconds")
    private Integer targetDurationSeconds;

    @Column(name = "target_distance_meters")
    private Integer targetDistanceMeters;

    @Column(name = "target_duration_run_seconds")
    private Integer targetDurationRunSeconds;

    @Column(name = "rest_seconds", nullable = false)
    private Integer restSeconds;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
