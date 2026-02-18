package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.entity.assistant.ConversationEntity;
import at.v3rtumnus.planman.entity.assistant.MessageEntity;
import at.v3rtumnus.planman.mcp.McpToolCallbackProvider;
import at.v3rtumnus.planman.service.anonymization.AnonymizationResult;
import at.v3rtumnus.planman.service.anonymization.AnonymizationService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates AI query processing using cloud LLM with native tool calling.
 * MCP tools are exposed as OpenAI tools - the LLM decides when to call them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiOrchestrationService {

    private final OpenAiChatModel chatModel;
    private final McpToolCallbackProvider toolCallbackProvider;
    private final AnonymizationService anonymizationService;
    private final ConversationService conversationService;
    private final Tracer tracer;

    @Value("${assistant.system-prompt:You are a helpful AI assistant. Answer questions directly and concisely. Use available tools when they can help provide accurate, up-to-date information.}")
    private String systemPrompt;

    @Value("${assistant.conversation.max-history:10}")
    private int maxHistoryMessages;

    public QueryResult processQuery(String query, String conversationId) {
        Span span = tracer.nextSpan().name("ai.process_query").start();

        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            String traceId = span.context().traceId();
            long startTime = System.currentTimeMillis();

            log.info("Processing query [{}]: '{}'", traceId, query);

            // 1. Anonymize query for privacy
            long anonymizationStart = System.currentTimeMillis();
            AnonymizationResult anonymization = anonymizationService.anonymize(query);
            String anonymizedQuery = anonymization.getAnonymizedText();
            long anonymizationDuration = System.currentTimeMillis() - anonymizationStart;

            if (anonymization.hasAnonymizedEntities()) {
                log.info("Anonymized {} PII entities in {}ms: {}",
                    anonymization.getEntityCount(), anonymizationDuration, anonymization.getDetectedEntityTypes());
            }
            span.tag("anonymization.count", String.valueOf(anonymization.getEntityCount()));

            // 2. Load conversation history if conversationId is provided
            List<Message> conversationHistory = new ArrayList<>();
            if (conversationId != null && !conversationId.isBlank()) {
                conversationHistory = loadConversationHistory(conversationId);
                log.debug("Loaded {} messages from conversation history", conversationHistory.size());
                span.tag("history.messages", String.valueOf(conversationHistory.size()));
            }

            // 3. Get MCP tools as tool callbacks and clear previous tracking
            toolCallbackProvider.clearToolCalls();
            toolCallbackProvider.setAnonymizationContext(anonymization);
            List<ToolCallback> toolCallbacks = toolCallbackProvider.getToolCallbacks();
            log.debug("Registered {} MCP tools for tool calling", toolCallbacks.size());

            // 4. Call LLM with tools and conversation history
            long llmStart = System.currentTimeMillis();

            ChatClient chatClient = ChatClient.builder(chatModel).build();

            ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .system(systemPrompt)
                .messages(conversationHistory)
                .user(anonymizedQuery)
                .toolCallbacks(toolCallbacks.toArray(new ToolCallback[0]));

            String response = requestSpec.call().content();

            long llmDuration = System.currentTimeMillis() - llmStart;
            log.info("LLM call completed in {}ms", llmDuration);
            span.tag("llm.duration_ms", String.valueOf(llmDuration));

            // 5. De-anonymize the response
            if (response != null && anonymization.hasAnonymizedEntities()) {
                response = anonymization.deanonymize(response);
            }

            // 6. Get tool calls made during this request
            List<McpToolCallbackProvider.ToolCall> toolCalls = toolCallbackProvider.getToolCalls();

            // Build result
            long totalDuration = System.currentTimeMillis() - startTime;

            QueryResult result = new QueryResult();
            result.setQuery(query);
            result.setResponse(response != null ? response : "No response generated");
            result.setTraceId(traceId);
            result.setSuccess(true);
            result.setTotalDurationMs(totalDuration);
            result.setAnonymizedEntities(anonymization.getEntityCount());
            result.setToolsUsed(toolCalls.stream()
                .map(tc -> tc.getServerName() + ":" + tc.getToolName())
                .toList());

            log.info("Query completed in {}ms (anon={}ms, llm={}ms): history={}, entities={}, tools={}",
                totalDuration, anonymizationDuration, llmDuration,
                conversationHistory.size(), anonymization.getEntityCount(), toolCalls.size());

            return result;

        } catch (Exception e) {
            log.error("Error processing query: {}", query, e);
            span.error(e);

            QueryResult result = new QueryResult();
            result.setQuery(query);
            result.setSuccess(false);
            result.setError(e.getMessage());
            result.setTraceId(span.context().traceId());
            return result;

        } finally {
            toolCallbackProvider.clearAnonymizationContext();
            span.end();
        }
    }

    /**
     * Process a query with streaming response.
     * Returns a Flux of StreamEvent containing content chunks and a final metadata event.
     */
    public Flux<StreamEvent> processQueryStream(String query, String conversationId) {
        log.info("Processing streaming query: '{}'", query);

        // 1. Anonymize query for privacy
        AnonymizationResult anonymization = anonymizationService.anonymize(query);
        String anonymizedQuery = anonymization.getAnonymizedText();

        if (anonymization.hasAnonymizedEntities()) {
            log.info("Anonymized {} PII entities for streaming: {}",
                anonymization.getEntityCount(), anonymization.getDetectedEntityTypes());
        }

        // 2. Load conversation history
        List<Message> conversationHistory = new ArrayList<>();
        if (conversationId != null && !conversationId.isBlank()) {
            conversationHistory = loadConversationHistory(conversationId);
            log.debug("Loaded {} messages from conversation history", conversationHistory.size());
        }

        // 3. Get MCP tools and set anonymization context
        toolCallbackProvider.clearToolCalls();
        toolCallbackProvider.setAnonymizationContext(anonymization);
        List<ToolCallback> toolCallbacks = toolCallbackProvider.getToolCallbacks();

        // 4. Create streaming request
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        // Capture tool calls after streaming completes
        AtomicReference<List<String>> capturedToolCalls = new AtomicReference<>(List.of());

        // 5. Stream response with deanonymization applied to each chunk
        Flux<StreamEvent> contentFlux = chatClient.prompt()
            .system(systemPrompt)
            .messages(conversationHistory)
            .user(anonymizedQuery)
            .toolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
            .stream()
            .content()
            .map(chunk -> {
                if (anonymization.hasAnonymizedEntities() && chunk != null) {
                    return anonymization.deanonymize(chunk);
                }
                return chunk;
            })
            .map(StreamEvent::content)
            .doOnComplete(() -> {
                capturedToolCalls.set(toolCallbackProvider.getToolCalls().stream()
                    .map(tc -> tc.getServerName() + ":" + tc.getToolName())
                    .toList());
                log.debug("Captured {} tool calls for metadata", capturedToolCalls.get().size());
            });

        // 6. Append final metadata event after content stream completes
        Flux<StreamEvent> metadataFlux = Mono.fromSupplier(() ->
            StreamEvent.metadata(capturedToolCalls.get())
        ).flux();

        return contentFlux
            .concatWith(metadataFlux)
            .doFinally(signal -> {
                toolCallbackProvider.clearAnonymizationContext();
                log.debug("Streaming completed with signal: {}", signal);
            });
    }

    /**
     * Represents a streaming event - either content or metadata.
     */
    public sealed interface StreamEvent {
        record Content(String text) implements StreamEvent {}
        record Metadata(List<String> toolsUsed) implements StreamEvent {}

        static StreamEvent content(String text) {
            return new Content(text);
        }

        static StreamEvent metadata(List<String> toolsUsed) {
            return new Metadata(toolsUsed);
        }
    }

    /**
     * Load conversation history from database and convert to Spring AI messages.
     * Limits to maxHistoryMessages to avoid context overflow.
     */
    private List<Message> loadConversationHistory(String conversationId) {
        List<Message> messages = new ArrayList<>();

        try {
            ConversationEntity conversation = conversationService.getConversationWithMessages(conversationId);
            if (conversation == null || conversation.getMessages() == null) {
                return messages;
            }

            List<MessageEntity> dbMessages = conversation.getMessages();

            int startIndex = Math.max(0, dbMessages.size() - maxHistoryMessages);
            for (int i = startIndex; i < dbMessages.size(); i++) {
                MessageEntity msg = dbMessages.get(i);
                if ("user".equals(msg.getRole())) {
                    messages.add(new UserMessage(msg.getContent()));
                } else if ("assistant".equals(msg.getRole())) {
                    messages.add(new AssistantMessage(msg.getContent()));
                }
            }

        } catch (Exception e) {
            log.warn("Failed to load conversation history for {}: {}", conversationId, e.getMessage());
        }

        return messages;
    }

    public static class QueryResult {
        private String query;
        private String response;
        private String traceId;
        private boolean success;
        private String error;
        private long totalDurationMs;
        private int anonymizedEntities;
        private List<String> toolsUsed = new ArrayList<>();

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }

        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }

        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public long getTotalDurationMs() { return totalDurationMs; }
        public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }

        public int getAnonymizedEntities() { return anonymizedEntities; }
        public void setAnonymizedEntities(int anonymizedEntities) { this.anonymizedEntities = anonymizedEntities; }

        public List<String> getToolsUsed() { return toolsUsed; }
        public void setToolsUsed(List<String> toolsUsed) { this.toolsUsed = toolsUsed; }
    }
}
