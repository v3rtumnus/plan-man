package at.v3rtumnus.planman.dto.notification;

import at.v3rtumnus.planman.entity.notification.NotificationSeverity;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationDTO(Long id, String title, String message, NotificationSeverity severity,
                              String url, List<NotificationAction> actions, LocalDateTime createdAt,
                              boolean read) {

    public String iconClass() {
        return severity.getIconClass();
    }

    public String textClass() {
        return severity.getTextClass();
    }
}
