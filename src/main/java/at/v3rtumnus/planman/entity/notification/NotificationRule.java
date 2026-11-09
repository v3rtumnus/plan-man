package at.v3rtumnus.planman.entity.notification;

import at.v3rtumnus.planman.entity.UserProfile;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A configured check. {@code ruleType} selects the {@code NotificationEvaluator} bean,
 * {@code parametersJson} holds its thresholds (editable without a redeploy).
 */
@Entity
@Data
@NoArgsConstructor
public class NotificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String ruleType;

    @Column(columnDefinition = "TEXT")
    private String parametersJson;

    @Enumerated(EnumType.STRING)
    private NotificationTargetingMode targetingMode = NotificationTargetingMode.ALL_USERS;

    private boolean enabled = true;

    private LocalDateTime createdAt;

    /** Only used with {@link NotificationTargetingMode#SPECIFIC_USERS}. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "notification_rule_target_user",
            joinColumns = @JoinColumn(name = "rule_id"),
            inverseJoinColumns = @JoinColumn(name = "user_profile_id"))
    private List<UserProfile> targetUsers = new ArrayList<>();
}
