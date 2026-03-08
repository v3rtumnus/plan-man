package at.v3rtumnus.planman.entity.fitness;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fitness_plan_session")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitnessPlanSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fitness_plan_id", nullable = false)
    private FitnessPlan fitnessPlan;

    @Column(name = "session_number", nullable = false)
    private Integer sessionNumber;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 20)
    private SessionType sessionType;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(columnDefinition = "TEXT")
    private String description;
}
