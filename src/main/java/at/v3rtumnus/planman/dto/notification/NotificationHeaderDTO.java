package at.v3rtumnus.planman.dto.notification;

import java.util.List;

/**
 * Data for the bell in the header: unread count, the newest five unread and how many devices
 * get push (0 shows "Push aus").
 */
public record NotificationHeaderDTO(long unreadCount, List<NotificationDTO> notifications, long pushDevices) {

    public static NotificationHeaderDTO empty() {
        return new NotificationHeaderDTO(0, List.of(), 0);
    }
}
