package at.v3rtumnus.planman.exception;

public class AssistantException extends RuntimeException {

    public AssistantException(String message) {
        super(message);
    }

    public AssistantException(String message, Throwable cause) {
        super(message, cause);
    }
}
