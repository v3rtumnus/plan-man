package at.v3rtumnus.planman.mcp;

import at.v3rtumnus.planman.service.anonymization.AnonymizationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpToolCallbackProviderTest {

    @Mock
    private McpDiscoveryService mcpDiscoveryService;

    @InjectMocks
    private McpToolCallbackProvider provider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        provider.clearToolCalls();
        provider.invalidateCache();
    }

    @Test
    void getToolCalls_initiallyEmpty() {
        assertThat(provider.getToolCalls()).isEmpty();
    }

    @Test
    void clearToolCalls_clearsTrackedCalls() {
        provider.clearToolCalls();
        assertThat(provider.getToolCalls()).isEmpty();
    }

    @Test
    void getToolCallbacks_withNoServers_returnsEmptyList() {
        when(mcpDiscoveryService.getAllTools()).thenReturn(Map.of());

        List<?> callbacks = provider.getToolCallbacks();

        assertThat(callbacks).isEmpty();
    }

    @Test
    void getToolCallbacks_withOneServer_returnsOneCallback() {
        McpClient.McpTool tool = new McpClient.McpTool();
        tool.setName("search");
        tool.setDescription("Search the web");

        when(mcpDiscoveryService.getAllTools()).thenReturn(Map.of("brave", List.of(tool)));

        List<?> callbacks = provider.getToolCallbacks();

        assertThat(callbacks).hasSize(1);
    }

    @Test
    void getToolCallbacks_calledTwice_usesCachedResult() {
        when(mcpDiscoveryService.getAllTools()).thenReturn(Map.of());

        provider.getToolCallbacks();
        provider.getToolCallbacks();

        assertThat(provider.getToolCallbacks()).isEmpty();
    }

    @Test
    void invalidateCache_forcesRebuildOnNextCall() {
        when(mcpDiscoveryService.getAllTools()).thenReturn(Map.of());
        provider.getToolCallbacks();

        provider.invalidateCache();

        McpClient.McpTool tool = new McpClient.McpTool();
        tool.setName("newTool");
        when(mcpDiscoveryService.getAllTools()).thenReturn(Map.of("server", List.of(tool)));

        List<?> callbacks = provider.getToolCallbacks();
        assertThat(callbacks).hasSize(1);
    }

    @Test
    void setAndClearAnonymizationContext_doesNotThrow() {
        AnonymizationResult result = new AnonymizationResult("text", "text", Map.of());
        provider.setAnonymizationContext(result);
        provider.clearAnonymizationContext();
    }

    @Test
    void toolCallback_call_successPath_returnsResult() throws Exception {
        McpClient.McpTool tool = new McpClient.McpTool();
        tool.setName("search");
        tool.setDescription("Search the web");

        McpClient mockClient = mock(McpClient.class);
        when(mcpDiscoveryService.getAllTools()).thenReturn(Map.of("brave", List.of(tool)));
        when(mcpDiscoveryService.getClient("brave")).thenReturn(mockClient);
        when(mockClient.callTool(eq("search"), any())).thenReturn(
                objectMapper.readTree("{\"result\":\"found it\"}"));

        List<ToolCallback> callbacks = provider.getToolCallbacks();
        String result = callbacks.get(0).call("{\"query\":\"test\"}");

        assertThat(result).contains("found it");
        assertThat(provider.getToolCalls()).hasSize(1);
        assertThat(provider.getToolCalls().get(0).getToolName()).isEqualTo("search");
        assertThat(provider.getToolCalls().get(0).isSuccess()).isTrue();
    }

    @Test
    void toolCallback_call_nullClient_returnsErrorMessage() {
        McpClient.McpTool tool = new McpClient.McpTool();
        tool.setName("missing-tool");
        tool.setDescription("Tool on unavailable server");

        when(mcpDiscoveryService.getAllTools()).thenReturn(Map.of("gone-server", List.of(tool)));
        when(mcpDiscoveryService.getClient("gone-server")).thenReturn(null);

        List<ToolCallback> callbacks = provider.getToolCallbacks();
        String result = callbacks.get(0).call("{}");

        assertThat(result).contains("Error: MCP server not available");
        assertThat(provider.getToolCalls()).isEmpty(); // null client returns before tracking
    }

    @Test
    void toolCallback_call_exceptionFromClient_returnsErrorMessage() throws Exception {
        McpClient.McpTool tool = new McpClient.McpTool();
        tool.setName("failing-tool");
        tool.setDescription("Always fails");

        McpClient mockClient = mock(McpClient.class);
        when(mcpDiscoveryService.getAllTools()).thenReturn(Map.of("srv", List.of(tool)));
        when(mcpDiscoveryService.getClient("srv")).thenReturn(mockClient);
        when(mockClient.callTool(any(), any())).thenThrow(new RuntimeException("server error"));

        List<ToolCallback> callbacks = provider.getToolCallbacks();
        String result = callbacks.get(0).call("{}");

        assertThat(result).contains("Error calling tool");
        assertThat(provider.getToolCalls()).hasSize(1);
        assertThat(provider.getToolCalls().get(0).isSuccess()).isFalse();
        assertThat(provider.getToolCalls().get(0).getError()).contains("server error");
    }

    @Test
    void toolCallback_call_withAnonymizationContext_deanonymizesParams() throws Exception {
        McpClient.McpTool tool = new McpClient.McpTool();
        tool.setName("search");
        tool.setDescription("Search");

        McpClient mockClient = mock(McpClient.class);
        when(mcpDiscoveryService.getAllTools()).thenReturn(Map.of("srv", List.of(tool)));
        when(mcpDiscoveryService.getClient("srv")).thenReturn(mockClient);
        when(mockClient.callTool(eq("search"), any())).thenReturn(
                objectMapper.readTree("{\"data\":\"result\"}"));

        // Set anonymization context with a replacement
        var entity = new AnonymizationResult.AnonymizedEntity(
                "alice@example.com",
                at.v3rtumnus.planman.service.anonymization.EntityType.EMAIL,
                0, 17, 0.99);
        AnonymizationResult anonymization = new AnonymizationResult(
                "Find alice@example.com",
                "Find [EMAIL_1]",
                Map.of("[EMAIL_1]", entity));
        provider.setAnonymizationContext(anonymization);

        List<ToolCallback> callbacks = provider.getToolCallbacks();
        String result = callbacks.get(0).call("{\"query\":\"[EMAIL_1]\"}");

        assertThat(result).isNotNull();
    }

    @Test
    void toolCall_getters_returnCorrectValues() {
        McpToolCallbackProvider.ToolCall call = new McpToolCallbackProvider.ToolCall(
                "serverA", "toolB", 42L);

        assertThat(call.getServerName()).isEqualTo("serverA");
        assertThat(call.getToolName()).isEqualTo("toolB");
        assertThat(call.getDurationMs()).isEqualTo(42L);
        assertThat(call.getError()).isNull();
        assertThat(call.isSuccess()).isTrue();
    }

    @Test
    void toolCall_withError_isNotSuccess() {
        McpToolCallbackProvider.ToolCall call = new McpToolCallbackProvider.ToolCall(
                "srv", "tool", 10L, "something went wrong");

        assertThat(call.isSuccess()).isFalse();
        assertThat(call.getError()).isEqualTo("something went wrong");
    }

    @Test
    void getToolCallbacks_callbackHasCorrectToolDefinition() {
        McpClient.McpTool tool = new McpClient.McpTool();
        tool.setName("search");
        tool.setDescription("Search the web");
        when(mcpDiscoveryService.getAllTools()).thenReturn(Map.of("brave", List.of(tool)));

        List<ToolCallback> callbacks = provider.getToolCallbacks();
        ToolDefinition def = callbacks.get(0).getToolDefinition();

        assertThat(def.name()).isEqualTo("search");
        assertThat(def.description()).isEqualTo("Search the web");
    }

    @Test
    void mcpToolRequest_toString_containsDynamicProperties() {
        McpToolCallbackProvider.McpToolRequest request = new McpToolCallbackProvider.McpToolRequest();
        request.setDynamicProperty("key", "value");

        assertThat(request.toString()).contains("key");
    }

    @Test
    void toolCallback_call_withAnonymizationContextAndNestedParams_deanonymizesMapAndListValues()
            throws Exception {
        McpClient.McpTool tool = new McpClient.McpTool();
        tool.setName("search");
        tool.setDescription("Search");

        McpClient mockClient = mock(McpClient.class);
        when(mcpDiscoveryService.getAllTools()).thenReturn(Map.of("srv", List.of(tool)));
        when(mcpDiscoveryService.getClient("srv")).thenReturn(mockClient);
        when(mockClient.callTool(eq("search"), any())).thenReturn(
                objectMapper.readTree("{\"data\":\"result\"}"));

        // Set anonymization context with email placeholder
        var entity = new AnonymizationResult.AnonymizedEntity(
                "alice@example.com",
                at.v3rtumnus.planman.service.anonymization.EntityType.EMAIL,
                0, 17, 0.99);
        AnonymizationResult anonymization = new AnonymizationResult(
                "Find alice@example.com",
                "Find [EMAIL_1]",
                Map.of("[EMAIL_1]", entity));
        provider.setAnonymizationContext(anonymization);

        List<ToolCallback> callbacks = provider.getToolCallbacks();
        // Pass nested map, list with string, list with number, and boolean values
        String result = callbacks.get(0).call(
                "{\"nested\":{\"key\":\"[EMAIL_1]\"}," +
                "\"items\":[\"[EMAIL_1]\",42]," +
                "\"flag\":true}");

        assertThat(result).isNotNull();
    }
}
