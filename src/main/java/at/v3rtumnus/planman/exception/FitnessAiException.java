package at.v3rtumnus.planman.exception;

public class FitnessAiException extends RuntimeException {

    public FitnessAiException(String message) {
        super(message);
    }

    public FitnessAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
