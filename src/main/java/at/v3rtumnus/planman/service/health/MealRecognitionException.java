package at.v3rtumnus.planman.service.health;

/** The model could not produce a usable draft; the message is shown to the user. */
public class MealRecognitionException extends RuntimeException {

    public MealRecognitionException(String message) {
        super(message);
    }

    public MealRecognitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
