package at.v3rtumnus.planman.entity.fitness;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "fitness_session_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitnessSessionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fitness_profile_id", nullable = false)
    private FitnessProfile fitnessProfile;

    @ManyToOne(optional = true)
    @JoinColumn(name = "plan_session_id")
    private FitnessPlanSession planSession;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 20)
    private SessionType sessionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "skip_reason", length = 50)
    private SkipReason skipReason;

    @Column(name = "skip_notes", length = 255)
    private String skipNotes;

    @Column(name = "actual_duration_minutes")
    private Integer actualDurationMinutes;

    @Column(name = "difficulty_rating")
    private Integer difficultyRating;

    @Column(name = "feedback_text", columnDefinition = "TEXT")
    private String feedbackText;

    @Column(name = "ai_analyzed", nullable = false)
    private boolean aiAnalyzed;
}
