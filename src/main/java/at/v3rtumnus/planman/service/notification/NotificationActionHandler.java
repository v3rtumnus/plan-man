package at.v3rtumnus.planman.service.notification;

import at.v3rtumnus.planman.entity.notification.Notification;

/**
 * Carries out an action button (e.g. "Gespielt") for the notifications of one rule type.
 * The built-in action {@link NotificationService#ACTION_READ} needs no handler.
 */
public interface NotificationActionHandler {

    String ruleType();

    void handle(Notification notification, String actionKey);
}
