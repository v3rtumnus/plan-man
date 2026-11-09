package at.v3rtumnus.planman.entity.notification;

import at.v3rtumnus.planman.entity.UserProfile;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A browser's Web Push subscription (one per installed app / device). */
@Entity
@Data
@NoArgsConstructor
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_profile_id")
    private UserProfile user;

    private String endpoint;

    /** The browser's P-256 public key, base64url. */
    @Column(name = "p256dh")
    private String p256dh;

    /** The browser's 16-byte auth secret, base64url. */
    private String auth;

    private String deviceLabel;

    private LocalDateTime createdAt;

    private LocalDateTime lastSuccessAt;

    private LocalDateTime lastFailureAt;

    private String lastError;
}
