package at.v3rtumnus.planman.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class StdioMcpClient implements McpClient {

    private final String serverName;
    private final String command;
    private final List<String> args;
    private final Map<String, String> env;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private Thread readerThread;
    private final AtomicLong requestId = new AtomicLong(1);
    private final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private boolean connected = false;

    public StdioMcpClient(String serverName, String command, List<String> args, Map<String, String> env, Tracer tracer) {
        this.serverName = serverName;
        this.command = command;
        this.args = args != null ? args : List.of();
        this.env = env != null ? env : Map.of();
        this.objectMapper = new ObjectMapper();
        this.tracer = tracer;
    }

    @Override
    public void initialize() throws Exception {
        Span span = tracer.nextSpan().name("mcp.initialize").start();
        span.tag("mcp.server", serverName);

        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            long startTime = System.currentTimeMillis();
            log.info("Initializing MCP server: {}", serverName);

            List<String> commandList = new ArrayList<>();
            commandList.add(command);
            commandList.addAll(args);

            ProcessBuilder pb = new ProcessBuilder(commandList);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            if (!env.isEmpty()) {
                pb.environment().putAll(env);
            }

            process = pb.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            readerThread = new Thread(this::readLoop, "MCP-" + serverName + "-Reader");
            readerThread.setDaemon(true);
            readerThread.start();

            ObjectNode initRequest = objectMapper.createObjectNode();
            initRequest.put("jsonrpc", "2.0");
            initRequest.put("id", requestId.getAndIncrement());
            initRequest.put("method", "initialize");

            ObjectNode params = objectMapper.createObjectNode();
            params.put("protocolVersion", "2024-11-05");

            ObjectNode clientInfo = objectMapper.createObjectNode();
            clientInfo.put("name", "plan-man-assistant");
            clientInfo.put("version", "1.0.0");
            params.set("clientInfo", clientInfo);

            ObjectNode capabilities = objectMapper.createObjectNode();
            capabilities.set("roots", objectMapper.createObjectNode());
            capabilities.set("sampling", objectMapper.createObjectNode());
            params.set("capabilities", capabilities);

            initRequest.set("params", params);

            JsonNode response = sendRequest(initRequest);

            ObjectNode initializedNotification = objectMapper.createObjectNode();
            initializedNotification.put("jsonrpc", "2.0");
            initializedNotification.put("method", "notifications/initialized");
            String notificationJson = objectMapper.writeValueAsString(initializedNotification);
            synchronized (writer) {
                writer.write(notificationJson);
                writer.newLine();
                writer.flush();
            }

            long duration = System.currentTimeMillis() - startTime;
            span.tag("mcp.init.duration_ms", String.valueOf(duration));
            log.info("MCP server {} initialized in {}ms: {}", serverName, duration, response);

            connected = true;
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Override
    public List<McpTool> listTools() throws Exception {
        Span span = tracer.nextSpan().name("mcp.list_tools").start();
        span.tag("mcp.server", serverName);

        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            long startTime = System.currentTimeMillis();

            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", requestId.getAndIncrement());
            request.put("method", "tools/list");

            JsonNode response = sendRequest(request);
            log.debug("tools/list response from {}: {}", serverName, response);

            List<McpTool> result = new ArrayList<>();

            JsonNode resultNode = response.get("result");
            if (resultNode == null) {
                log.warn("No result in tools/list response from {}: {}", serverName, response);
                return result;
            }

            JsonNode tools = resultNode.get("tools");
            if (tools == null || !tools.isArray()) {
                log.warn("No tools array in response from {}: {}", serverName, resultNode);
                return result;
            }

            for (JsonNode tool : tools) {
                McpTool mcpTool = new McpTool();
                mcpTool.setName(tool.get("name").asText());
                mcpTool.setDescription(tool.has("description") ? tool.get("description").asText() : "");
                mcpTool.setInputSchema(tool.get("inputSchema"));
                result.add(mcpTool);
            }

            long duration = System.currentTimeMillis() - startTime;
            span.tag("mcp.tools.count", String.valueOf(result.size()));
            span.tag("mcp.duration_ms", String.valueOf(duration));
            log.info("Listed {} tools from MCP server {} in {}ms", result.size(), serverName, duration);

            return result;
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Override
    public JsonNode callTool(String toolName, Map<String, Object> parameters) throws Exception {
        Span span = tracer.nextSpan().name("mcp.call_tool").start();
        span.tag("mcp.server", serverName);
        span.tag("mcp.tool", toolName);
        span.tag("mcp.params", objectMapper.writeValueAsString(parameters));

        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            long startTime = System.currentTimeMillis();
            log.info("Calling MCP tool: server={}, tool={}, params={}", serverName, toolName, parameters);

            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", requestId.getAndIncrement());
            request.put("method", "tools/call");

            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            params.set("arguments", objectMapper.valueToTree(parameters));

            request.set("params", params);

            JsonNode response = sendRequest(request);
            JsonNode result = response.get("result");

            long duration = System.currentTimeMillis() - startTime;
            span.tag("mcp.duration_ms", String.valueOf(duration));
            span.tag("mcp.result.size", String.valueOf(result.toString().length()));
            log.info("MCP tool call completed: server={}, tool={}, duration={}ms", serverName, toolName, duration);

            return result;
        } catch (Exception e) {
            span.error(e);
            log.error("MCP tool call failed: server={}, tool={}, error={}", serverName, toolName, e.getMessage(), e);
            throw e;
        } finally {
            span.end();
        }
    }

    private JsonNode sendRequest(ObjectNode request) throws Exception {
        long id = request.get("id").asLong();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        String jsonRequest = objectMapper.writeValueAsString(request);
        synchronized (writer) {
            writer.write(jsonRequest);
            writer.newLine();
            writer.flush();
        }

        return future.get(30, TimeUnit.SECONDS);
    }

    private void readLoop() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonNode message = objectMapper.readTree(line);

                    if (message.has("id")) {
                        long id = message.get("id").asLong();
                        CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                        if (future != null) {
                            future.complete(message);
                        }
                    } else {
                        log.debug("Received notification from {}: {}", serverName, message);
                    }
                } catch (Exception e) {
                    log.error("Error parsing message from {}: {}", serverName, line, e);
                }
            }
        } catch (IOException e) {
            log.error("Error reading from MCP server {}", serverName, e);
        }
    }

    @Override
    public boolean isConnected() {
        return connected && process != null && process.isAlive();
    }

    @Override
    public void close() {
        connected = false;
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (process != null) process.destroy();
        } catch (IOException e) {
            log.error("Error closing MCP client", e);
        }
    }
}
