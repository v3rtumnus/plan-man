package at.v3rtumnus.planman.dto.notification;

/** A button on a notification; {@code key} is what the evaluator's action handler receives. */
public record NotificationAction(String key, String label) {
}
