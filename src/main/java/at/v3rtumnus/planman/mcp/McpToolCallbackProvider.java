package at.v3rtumnus.planman.mcp;

import at.v3rtumnus.planman.service.anonymization.AnonymizationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolCallbackProvider {

    private final McpDiscoveryService mcpDiscoveryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final ThreadLocal<List<ToolCall>> toolCallsTracker = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<AnonymizationResult> anonymizationContext = new ThreadLocal<>();

    private volatile List<ToolCallback> cachedCallbacks = null;
    private volatile long cacheTimestamp = 0;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

    public void clearToolCalls() {
        toolCallsTracker.get().clear();
    }

    public List<ToolCall> getToolCalls() {
        return new ArrayList<>(toolCallsTracker.get());
    }

    public void setAnonymizationContext(AnonymizationResult anonymization) {
        anonymizationContext.set(anonymization);
    }

    public void clearAnonymizationContext() {
        anonymizationContext.remove();
    }

    public List<ToolCallback> getToolCallbacks() {
        long now = System.currentTimeMillis();

        if (cachedCallbacks != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
            log.debug("Using cached tool callbacks ({} tools)", cachedCallbacks.size());
            return cachedCallbacks;
        }

        synchronized (this) {
            if (cachedCallbacks != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
                return cachedCallbacks;
            }

            List<ToolCallback> callbacks = new ArrayList<>();
            Map<String, List<McpClient.McpTool>> allTools = mcpDiscoveryService.getAllTools();

            for (Map.Entry<String, List<McpClient.McpTool>> entry : allTools.entrySet()) {
                String serverName = entry.getKey();
                for (McpClient.McpTool tool : entry.getValue()) {
                    callbacks.add(createCallback(serverName, tool));
                    log.debug("Registered MCP tool: {}", tool.getName());
                }
            }

            cachedCallbacks = callbacks;
            cacheTimestamp = now;
            log.info("Rebuilt tool callback cache: {} MCP tools registered", callbacks.size());
            return callbacks;
        }
    }

    public void invalidateCache() {
        synchronized (this) {
            cachedCallbacks = null;
            cacheTimestamp = 0;
            log.info("Tool callback cache invalidated");
        }
    }

    private ToolCallback createCallback(String serverName, McpClient.McpTool tool) {
        Function<McpToolRequest, String> toolFunction = request -> {
            long startTime = System.currentTimeMillis();
            try {
                log.info("Calling MCP tool: {} on {}", tool.getName(), serverName);

                McpClient client = mcpDiscoveryService.getClient(serverName);
                if (client == null) {
                    return "Error: MCP server not available: " + serverName;
                }

                Map<String, Object> params = request.toMap();

                AnonymizationResult anonymization = anonymizationContext.get();
                if (anonymization != null && anonymization.hasAnonymizedEntities()) {
                    params = deanonymizeParams(params, anonymization);
                    log.debug("Deanonymized MCP tool parameters for {}", tool.getName());
                }

                JsonNode result = client.callTool(tool.getName(), params);

                long duration = System.currentTimeMillis() - startTime;
                log.debug("MCP tool {} returned: {} bytes in {}ms", tool.getName(), result.toString().length(), duration);

                toolCallsTracker.get().add(new ToolCall(serverName, tool.getName(), duration));

                String responseStr = result.toString();
                if (anonymization != null && anonymization.hasAnonymizedEntities()) {
                    responseStr = anonymization.anonymizeWithExistingMappings(responseStr);
                    log.debug("Anonymized MCP tool response for {}", tool.getName());
                }

                return responseStr;

            } catch (Exception e) {
                log.error("Error calling MCP tool {}: {}", tool.getName(), e.getMessage());
                long duration = System.currentTimeMillis() - startTime;
                toolCallsTracker.get().add(new ToolCall(serverName, tool.getName(), duration, e.getMessage()));
                return "Error calling tool: " + e.getMessage();
            }
        };

        String inputSchemaStr = tool.getInputSchema() != null
                ? tool.getInputSchema().toString()
                : "{\"type\":\"object\",\"properties\":{}}";

        return new McpToolCallback(tool.getName(), tool.getDescription(), inputSchemaStr, toolFunction);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deanonymizeParams(Map<String, Object> params, AnonymizationResult anonymization) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String strValue) {
                result.put(entry.getKey(), anonymization.deanonymize(strValue));
            } else if (value instanceof Map) {
                result.put(entry.getKey(), deanonymizeParams((Map<String, Object>) value, anonymization));
            } else if (value instanceof List<?> list) {
                List<Object> newList = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof String strItem) {
                        newList.add(anonymization.deanonymize(strItem));
                    } else if (item instanceof Map) {
                        newList.add(deanonymizeParams((Map<String, Object>) item, anonymization));
                    } else {
                        newList.add(item);
                    }
                }
                result.put(entry.getKey(), newList);
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    public static class ToolCall {
        private final String serverName;
        private final String toolName;
        private final long durationMs;
        private final String error;

        public ToolCall(String serverName, String toolName, long durationMs) {
            this(serverName, toolName, durationMs, null);
        }

        public ToolCall(String serverName, String toolName, long durationMs, String error) {
            this.serverName = serverName;
            this.toolName = toolName;
            this.durationMs = durationMs;
            this.error = error;
        }

        public String getServerName() { return serverName; }
        public String getToolName() { return toolName; }
        public long getDurationMs() { return durationMs; }
        public String getError() { return error; }
        public boolean isSuccess() { return error == null; }
    }

    private class McpToolCallback implements ToolCallback {
        private final String name;
        private final String description;
        private final String inputSchema;
        private final Function<McpToolRequest, String> toolFunction;
        private final ToolDefinition toolDefinition;

        McpToolCallback(String name, String description, String inputSchema, Function<McpToolRequest, String> toolFunction) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
            this.toolFunction = toolFunction;
            this.toolDefinition = ToolDefinition.builder()
                    .name(name)
                    .description(description != null ? description : "")
                    .inputSchema(inputSchema)
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public String call(String toolInput) {
            try {
                McpToolRequest request = objectMapper.readValue(toolInput, McpToolRequest.class);
                return toolFunction.apply(request);
            } catch (Exception e) {
                log.error("Error parsing tool input for {}: {}", name, e.getMessage());
                return "Error parsing tool input: " + e.getMessage();
            }
        }
    }

    public static class McpToolRequest {
        private final Map<String, Object> dynamicProperties = new HashMap<>();

        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void setDynamicProperty(String name, Object value) {
            dynamicProperties.put(name, value);
        }

        @com.fasterxml.jackson.annotation.JsonAnyGetter
        public Map<String, Object> getDynamicProperties() {
            return dynamicProperties;
        }

        public Map<String, Object> toMap() {
            return new HashMap<>(dynamicProperties);
        }

        @Override
        public String toString() {
            return "McpToolRequest" + dynamicProperties;
        }
    }
}
