package at.v3rtumnus.planman.entity.health;

import at.v3rtumnus.planman.entity.UserProfile;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * One training session. kcal is the estimated burn; bonusKcal is what it adds to the day's
 * eating target (net kcal above resting, or the goal's flat football bonus).
 */
@Entity
@Table(name = "WORKOUT")
@Data
@NoArgsConstructor
public class Workout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "USER_PROFILE_ID")
    private UserProfile user;

    @Column(name = "DONE_ON")
    private LocalDate doneOn;

    @Enumerated(EnumType.STRING)
    private WorkoutType type;

    /** Free text for OTHER ("Tennis Doppel"), null otherwise. */
    private String title;

    @Column(name = "DURATION_MIN")
    private int durationMin;

    @Enumerated(EnumType.STRING)
    private Intensity intensity;

    private BigDecimal met;

    private int kcal;

    @Column(name = "BONUS_KCAL")
    private int bonusKcal;

    @Enumerated(EnumType.STRING)
    @Column(name = "KCAL_SOURCE")
    private KcalSource kcalSource;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    /** "Mo", "Di", … */
    public String dayLabel() {
        return doneOn.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.GERMAN).replace(".", "");
    }

    public String label() {
        return title != null && !title.isBlank() ? title : type.getLabel();
    }
}
