package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.assistant.ChatRequest;
import at.v3rtumnus.planman.dto.assistant.ChatResponse;
import at.v3rtumnus.planman.entity.assistant.ConversationEntity;
import at.v3rtumnus.planman.service.AiOrchestrationService;
import at.v3rtumnus.planman.service.ConversationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantApiController {

    private final AiOrchestrationService orchestrationService;
    private final ConversationService conversationService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Received chat request: {}", request.getMessage());

        String conversationId = request.getConversationId() != null
            ? request.getConversationId()
            : conversationService.createConversation().getId();

        AiOrchestrationService.QueryResult result = orchestrationService.processQuery(
            request.getMessage(), conversationId);

        conversationService.addMessage(conversationId, "user", request.getMessage(), null);
        conversationService.addMessage(conversationId, "assistant", result.getResponse(), result.getTraceId());

        ChatResponse response = ChatResponse.builder()
                .response(result.getResponse())
                .conversationId(conversationId)
                .traceId(result.getTraceId())
                .success(result.isSuccess())
                .error(result.getError())
                .durationMs(result.getTotalDurationMs())
                .anonymizedEntities(result.getAnonymizedEntities())
                .toolsUsed(result.getToolsUsed())
                .build();

        return ResponseEntity.ok(response);
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        log.info("Received streaming chat request: {}", request.getMessage());

        String conversationId = request.getConversationId() != null
            ? request.getConversationId()
            : conversationService.createConversation().getId();

        conversationService.addMessage(conversationId, "user", request.getMessage(), null);

        StringBuilder fullResponse = new StringBuilder();

        return orchestrationService.processQueryStream(request.getMessage(), conversationId)
            .map(event -> {
                if (event instanceof AiOrchestrationService.StreamEvent.Content content) {
                    fullResponse.append(content.text());
                    return "data:" + content.text() + "\n\n";
                } else if (event instanceof AiOrchestrationService.StreamEvent.Metadata metadata) {
                    try {
                        String json = objectMapper.writeValueAsString(Map.of(
                            "toolsUsed", metadata.toolsUsed(),
                            "conversationId", conversationId
                        ));
                        return "event: done\ndata:" + json + "\n\n";
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize metadata", e);
                        return "event: done\ndata:{}\n\n";
                    }
                }
                return "";
            })
            .doOnComplete(() -> {
                conversationService.addMessage(conversationId, "assistant", fullResponse.toString(), null);
                log.info("Streaming complete, saved response ({} chars)", fullResponse.length());
            })
            .doOnError(e -> log.error("Streaming error: {}", e.getMessage()));
    }

    @GetMapping("/conversation/{id}")
    public ResponseEntity<ConversationEntity> getConversation(@PathVariable String id) {
        ConversationEntity conversation = conversationService.getConversationWithMessages(id);
        return ResponseEntity.ok(conversation);
    }
}
