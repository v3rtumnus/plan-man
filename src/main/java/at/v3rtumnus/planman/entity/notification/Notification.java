package at.v3rtumnus.planman.entity.notification;

import at.v3rtumnus.planman.entity.UserProfile;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One notification for one recipient; each user has their own read state. */
@Entity
@Data
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for notifications not created by a rule (e.g. the test push). */
    @ManyToOne
    @JoinColumn(name = "rule_id")
    private NotificationRule rule;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_profile_id")
    private UserProfile recipient;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationSeverity severity;

    /** Page that opens when the notification is tapped; null = the overview page. */
    private String url;

    /** Up to two action buttons as JSON: [{"key":"played","label":"Gespielt"}]. */
    @Column(columnDefinition = "TEXT")
    private String actionsJson;

    /** Also sent to the user's devices, not only shown in the inbox. */
    private boolean push;

    private String periodKey;

    private LocalDateTime createdAt;

    private LocalDateTime readAt;

    private LocalDateTime pushedAt;

    public boolean isRead() {
        return readAt != null;
    }
}
