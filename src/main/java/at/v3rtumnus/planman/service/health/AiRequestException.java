package at.v3rtumnus.planman.service.health;

import lombok.Getter;

/** A Claude API request gave no usable answer; the message is shown to the user. */
@Getter
public class AiRequestException extends RuntimeException {

    public enum Reason {
        /** Nothing to recognise, or nothing recognised. */
        INPUT,
        /** API down, timeout, rate limit, refusal: trying again may help. */
        UNAVAILABLE,
        /** Prepaid credits used up or the workspace spend limit reached. */
        BILLING
    }

    private final Reason reason;

    public AiRequestException(String message) {
        this(Reason.INPUT, message, null);
    }

    public AiRequestException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }
}
