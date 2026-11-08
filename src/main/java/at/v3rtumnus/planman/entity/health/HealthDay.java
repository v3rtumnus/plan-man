package at.v3rtumnus.planman.entity.health;

import at.v3rtumnus.planman.entity.UserProfile;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * One day of health data. The targets are copied from the goal in force on that day, so a
 * later goal change does not rewrite how past days are judged.
 */
@Entity
@Table(name = "HEALTH_DAY")
@Data
@NoArgsConstructor
public class HealthDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "USER_PROFILE_ID")
    private UserProfile user;

    @Column(name = "DAY_DATE")
    private LocalDate date;

    @ManyToOne
    @JoinColumn(name = "HEALTH_GOAL_ID")
    private HealthGoal goal;

    private Integer kcalTarget;

    @Column(name = "PROTEIN_TARGET_G")
    private Integer proteinTargetG;

    private Integer stepsTarget;

    private Integer steps;

    private Integer restingHr;

    private Integer sleepMinutes;

    private boolean paused;

    /** "Fällt aus" was tapped on the football question. */
    @Column(name = "FOOTBALL_CANCELLED")
    private boolean footballCancelled;
}
