package at.v3rtumnus.planman.mcp;

import at.v3rtumnus.planman.conf.McpServerConfig;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpDiscoveryService {

    private final McpServerConfig mcpServerConfig;
    private final Tracer tracer;
    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();
    private final Map<String, List<McpClient.McpTool>> toolsCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        log.info("Initializing MCP servers...");

        if (mcpServerConfig.getServers() == null || mcpServerConfig.getServers().isEmpty()) {
            log.warn("No MCP servers configured");
            return;
        }

        for (McpServerConfig.McpServer serverConfig : mcpServerConfig.getServers()) {
            if (!serverConfig.isEnabled()) {
                log.info("Skipping disabled MCP server: {}", serverConfig.getName());
                continue;
            }

            try {
                McpClient client = createClient(serverConfig);
                client.initialize();
                clients.put(serverConfig.getName(), client);

                List<McpClient.McpTool> tools = client.listTools();
                toolsCache.put(serverConfig.getName(), tools);
                log.info("MCP server {} initialized with {} tools", serverConfig.getName(), tools.size());

            } catch (Exception e) {
                log.error("Failed to initialize MCP server: {}", serverConfig.getName(), e);
            }
        }

        log.info("Initialized {} MCP servers", clients.size());
    }

    private McpClient createClient(McpServerConfig.McpServer config) {
        if ("stdio".equals(config.getType())) {
            return new StdioMcpClient(
                    config.getName(),
                    config.getCommand(),
                    config.getArgs(),
                    config.getEnv(),
                    tracer
            );
        }
        throw new IllegalArgumentException("Unsupported MCP server type: " + config.getType());
    }

    public McpClient getClient(String serverName) {
        return clients.get(serverName);
    }

    public String findServerForTool(String toolName) {
        for (Map.Entry<String, List<McpClient.McpTool>> entry : toolsCache.entrySet()) {
            for (McpClient.McpTool tool : entry.getValue()) {
                if (tool.getName().equals(toolName)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    public McpClient getClientForTool(String toolName) {
        String serverName = findServerForTool(toolName);
        return serverName != null ? clients.get(serverName) : null;
    }

    public Map<String, List<McpClient.McpTool>> getAllTools() {
        return Collections.unmodifiableMap(toolsCache);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down MCP servers...");
        clients.values().forEach(McpClient::close);
        clients.clear();
        toolsCache.clear();
    }
}
