package at.v3rtumnus.planman.entity.fitness;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fitness_session_exercise_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitnessSessionExerciseLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_log_id", nullable = false)
    private FitnessSessionLog sessionLog;

    @ManyToOne
    @JoinColumn(name = "exercise_id", nullable = false)
    private FitnessExercise exercise;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "set_number")
    private Integer setNumber;

    @Column(name = "reps_done")
    private Integer repsDone;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(name = "duration_run_seconds")
    private Integer durationRunSeconds;

    @Column(length = 255)
    private String notes;
}
