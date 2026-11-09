package at.v3rtumnus.planman.dto.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** {@code GET /api/digest}: what the Morning Briefing and Glance show from plan-man. */
public record DigestDTO(LocalDateTime generatedAt, Map<String, UserDigest> users) {

    public record UserDigest(long unread, List<DigestNotification> notifications) {
    }

    public record DigestNotification(Long id, String title, String message, String severity, String url,
                                     LocalDateTime createdAt) {
    }
}
