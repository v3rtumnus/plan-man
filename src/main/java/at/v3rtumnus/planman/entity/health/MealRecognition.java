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

    // Explicit names: the naming strategy maps a trailing single capital (modelA) to "modela"
    @Column(name = "MODEL_A")
    private String modelA;
    @Lob
    @Column(name = "RESULT_A", columnDefinition = "TEXT")
    private String resultA;
    @Column(name = "KCAL_A")
    private Integer kcalA;
    @Column(name = "LATENCY_A_MS")
    private Integer latencyAMs;

    @Column(name = "MODEL_B")
    private String modelB;
    @Lob
    @Column(name = "RESULT_B", columnDefinition = "TEXT")
    private String resultB;
    @Column(name = "KCAL_B")
    private Integer kcalB;
    @Column(name = "LATENCY_B_MS")
    private Integer latencyBMs;

    private String shownModel;
}
