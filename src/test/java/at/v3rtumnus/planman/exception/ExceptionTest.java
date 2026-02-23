package at.v3rtumnus.planman.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionTest {

    @Test
    void assistantException_messageConstructor_preservesMessage() {
        AssistantException ex = new AssistantException("AI failed");
        assertThat(ex.getMessage()).isEqualTo("AI failed");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void assistantException_causeConstructor_preservesMessageAndCause() {
        Throwable cause = new IllegalStateException("root");
        AssistantException ex = new AssistantException("wrapped", cause);
        assertThat(ex.getMessage()).isEqualTo("wrapped");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void mcpServerException_messageConstructor_preservesMessage() {
        McpServerException ex = new McpServerException("MCP down");
        assertThat(ex.getMessage()).isEqualTo("MCP down");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void mcpServerException_causeConstructor_preservesMessageAndCause() {
        Throwable cause = new RuntimeException("io error");
        McpServerException ex = new McpServerException("MCP failed", cause);
        assertThat(ex.getMessage()).isEqualTo("MCP failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
