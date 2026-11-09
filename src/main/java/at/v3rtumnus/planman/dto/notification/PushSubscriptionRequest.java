package at.v3rtumnus.planman.dto.notification;

/** Body the browser posts after {@code pushManager.subscribe()} (PushSubscription.toJSON plus a label). */
public record PushSubscriptionRequest(String endpoint, Keys keys, String label) {

    public record Keys(String p256dh, String auth) {
    }
}
