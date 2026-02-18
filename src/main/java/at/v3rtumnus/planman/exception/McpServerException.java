package at.v3rtumnus.planman.exception;

public class McpServerException extends AssistantException {

    public McpServerException(String message) {
        super(message);
    }

    public McpServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
