package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.entity.assistant.ConversationEntity;
import at.v3rtumnus.planman.entity.assistant.MessageEntity;
import at.v3rtumnus.planman.mcp.McpToolCallbackProvider;
import at.v3rtumnus.planman.service.anonymization.AnonymizationResult;
import at.v3rtumnus.planman.service.anonymization.AnonymizationService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiOrchestrationServiceTest {

    @Mock
    private OpenAiChatModel chatModel;

    @Mock
    private McpToolCallbackProvider toolCallbackProvider;

    @Mock
    private AnonymizationService anonymizationService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private Tracer tracer;

    @InjectMocks
    private AiOrchestrationService service;

    @Mock
    private Span span;

    @Mock
    private Tracer.SpanInScope spanInScope;

    @Mock
    private TraceContext spanContext;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "systemPrompt", "You are a helpful assistant.");
        ReflectionTestUtils.setField(service, "maxHistoryMessages", 10);

        // Standard Tracer/Span chain used by processQuery
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(spanInScope);
        when(span.context()).thenReturn(spanContext);
        when(spanContext.traceId()).thenReturn("test-trace-id");
        lenient().when(span.tag(anyString(), anyString())).thenReturn(span);
        lenient().when(span.error(any())).thenReturn(span);

        // Tool provider defaults
        lenient().when(toolCallbackProvider.getToolCallbacks()).thenReturn(Collections.emptyList());
        lenient().when(toolCallbackProvider.getToolCalls()).thenReturn(Collections.emptyList());
    }

    @Test
    void processQuery_noHistory_returnsSuccessResult() {
        // Given
        AnonymizationResult noEntities = new AnonymizationResult("What is 2+2?", "What is 2+2?", Map.of());
        when(anonymizationService.anonymize("What is 2+2?")).thenReturn(noEntities);

        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage("4"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        // When
        AiOrchestrationService.QueryResult result = service.processQuery("What is 2+2?", null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResponse()).isEqualTo("4");
        assertThat(result.getTraceId()).isEqualTo("test-trace-id");
        assertThat(result.getQuery()).isEqualTo("What is 2+2?");
        assertThat(result.getToolsUsed()).isEmpty();
    }

    @Test
    void processQuery_withAnonymizedEntities_deanonymizesResponse() {
        // Create a result that anonymized "john@example.com" to "[EMAIL_1]"
        var entity = new AnonymizationResult.AnonymizedEntity("john@example.com",
                at.v3rtumnus.planman.service.anonymization.EntityType.EMAIL, 0, 16, 0.99);
        AnonymizationResult withEntities = new AnonymizationResult(
                "Email john@example.com please",
                "Email [EMAIL_1] please",
                Map.of("[EMAIL_1]", entity));

        when(anonymizationService.anonymize("Email john@example.com please")).thenReturn(withEntities);

        // Model returns response containing the placeholder
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage("Sent to [EMAIL_1]"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        AiOrchestrationService.QueryResult result =
                service.processQuery("Email john@example.com please", null);

        // De-anonymization should have replaced the placeholder
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResponse()).isEqualTo("Sent to john@example.com");
        assertThat(result.getAnonymizedEntities()).isEqualTo(1);
    }

    @Test
    void processQuery_whenModelThrows_returnsErrorResult() {
        AnonymizationResult noEntities = new AnonymizationResult("query", "query", Map.of());
        when(anonymizationService.anonymize("query")).thenReturn(noEntities);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("API unavailable"));

        AiOrchestrationService.QueryResult result = service.processQuery("query", null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("API unavailable");
        assertThat(result.getTraceId()).isEqualTo("test-trace-id");
        verify(span).error(any(RuntimeException.class));
    }

    @Test
    void processQuery_withConversationId_loadsHistory() {
        // conversationService returns null (no history found) → empty history
        when(conversationService.getConversationWithMessages("conv-123")).thenReturn(null);

        AnonymizationResult noEntities = new AnonymizationResult("Hi", "Hi", Map.of());
        when(anonymizationService.anonymize("Hi")).thenReturn(noEntities);

        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage("Hello!"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        AiOrchestrationService.QueryResult result = service.processQuery("Hi", "conv-123");

        assertThat(result.isSuccess()).isTrue();
        verify(conversationService).getConversationWithMessages("conv-123");
    }

    @Test
    void processQuery_withConversationContainingUserAndAssistantMessages_includesHistoryInRequest() {
        ConversationEntity conv = new ConversationEntity();
        MessageEntity userMsg = new MessageEntity("user", "Previous question");
        MessageEntity asstMsg = new MessageEntity("assistant", "Previous answer");
        conv.getMessages().add(userMsg);
        conv.getMessages().add(asstMsg);

        when(conversationService.getConversationWithMessages("conv-with-history")).thenReturn(conv);

        AnonymizationResult noEntities = new AnonymizationResult("Follow-up", "Follow-up", Map.of());
        when(anonymizationService.anonymize("Follow-up")).thenReturn(noEntities);

        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage("Follow-up answer"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        AiOrchestrationService.QueryResult result =
                service.processQuery("Follow-up", "conv-with-history");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResponse()).isEqualTo("Follow-up answer");
        verify(conversationService).getConversationWithMessages("conv-with-history");
    }

    @Test
    void streamEvent_contentFactory_createsContentInstance() {
        AiOrchestrationService.StreamEvent event = AiOrchestrationService.StreamEvent.content("hello");
        assertThat(event).isInstanceOf(AiOrchestrationService.StreamEvent.Content.class);
        assertThat(((AiOrchestrationService.StreamEvent.Content) event).text()).isEqualTo("hello");
    }

    @Test
    void streamEvent_metadataFactory_createsMetadataInstance() {
        AiOrchestrationService.StreamEvent event =
                AiOrchestrationService.StreamEvent.metadata(List.of("tool1", "tool2"));
        assertThat(event).isInstanceOf(AiOrchestrationService.StreamEvent.Metadata.class);
        assertThat(((AiOrchestrationService.StreamEvent.Metadata) event).toolsUsed())
                .containsExactly("tool1", "tool2");
    }
}
