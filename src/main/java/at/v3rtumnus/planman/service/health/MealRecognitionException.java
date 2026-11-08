package at.v3rtumnus.planman.service.health;

import lombok.Getter;

/** The model could not produce a usable draft; the message is shown to the user. */
@Getter
public class MealRecognitionException extends RuntimeException {

    public enum Reason {
        /** Nothing to recognise, or nothing recognised. */
        INPUT,
        /** API down, timeout, rate limit, refusal: trying again may help. */
        UNAVAILABLE,
        /** Prepaid credits used up or the workspace spend limit reached. */
        BILLING
    }

    private final Reason reason;

    public MealRecognitionException(String message) {
        this(Reason.INPUT, message, null);
    }

    public MealRecognitionException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }
}
