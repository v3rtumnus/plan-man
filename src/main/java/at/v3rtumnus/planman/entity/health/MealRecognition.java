package at.v3rtumnus.planman.entity.health;

import at.v3rtumnus.planman.entity.UserProfile;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One capture sent to the model. During the Haiku vs Sonnet test both models run (A and B) and
 * the review shows one of them; the saved meal after your corrections is the yardstick.
 */
@Entity
@Table(name = "MEAL_RECOGNITION")
@Data
@NoArgsConstructor
public class MealRecognition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "USER_PROFILE_ID")
    private UserProfile user;

    private LocalDateTime createdAt;
    private String description;
    private boolean hadPhoto;

    private String modelA;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String resultA;
    private Integer kcalA;
    private Integer latencyAMs;

    private String modelB;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String resultB;
    private Integer kcalB;
    private Integer latencyBMs;

    private String shownModel;
}
