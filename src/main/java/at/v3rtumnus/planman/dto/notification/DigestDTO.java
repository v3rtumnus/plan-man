package at.v3rtumnus.planman.dto.notification;

import at.v3rtumnus.planman.dto.health.HealthDigest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** {@code GET /api/digest}: what the Morning Briefing and Glance show from plan-man (notifications and health). */
public record DigestDTO(LocalDateTime generatedAt, Map<String, UserDigest> users) {

    /** @param health null for users without a health goal */
    public record UserDigest(long unread, List<DigestNotification> notifications, HealthDigest health) {

        public UserDigest(long unread, List<DigestNotification> notifications) {
            this(unread, notifications, null);
        }
    }

    public record DigestNotification(Long id, String title, String message, String severity, String url,
                                     LocalDateTime createdAt) {
    }
}
