package at.v3rtumnus.planman.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;
import java.util.Map;

public interface McpClient {

    void initialize() throws Exception;

    List<McpTool> listTools() throws Exception;

    JsonNode callTool(String toolName, Map<String, Object> parameters) throws Exception;

    boolean isConnected();

    void close();

    @Data
    class McpTool {
        private String name;
        private String description;
        private JsonNode inputSchema;
    }
}
