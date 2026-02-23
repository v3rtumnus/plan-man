package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.entity.assistant.ConversationEntity;
import at.v3rtumnus.planman.service.AiOrchestrationService;
import at.v3rtumnus.planman.service.ConversationService;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AssistantApiController.class)
class AssistantApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiOrchestrationService orchestrationService;

    @MockitoBean
    private ConversationService conversationService;

    @MockitoBean
    private PlanManUserDetailsService userDetailsService;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private CacheManager cacheManager;

    private AiOrchestrationService.QueryResult successResult(String response) {
        AiOrchestrationService.QueryResult r = new AiOrchestrationService.QueryResult();
        r.setSuccess(true);
        r.setResponse(response);
        r.setTraceId("trace-1");
        r.setTotalDurationMs(100L);
        r.setAnonymizedEntities(0);
        r.setToolsUsed(List.of());
        return r;
    }

    @Test
    void chat_withNewConversation_creates200Response() throws Exception {
        ConversationEntity conv = new ConversationEntity();
        conv.setId("new-conv-id");
        when(conversationService.createConversation()).thenReturn(conv);
        when(orchestrationService.processQuery(eq("Hello"), eq("new-conv-id")))
                .thenReturn(successResult("Hi there!"));
        when(conversationService.addMessage(any(), any(), any(), any()))
                .thenReturn(null);

        mockMvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Hi there!"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.conversationId").value("new-conv-id"));
    }

    @Test
    void chat_withExistingConversation_usesProvidedId() throws Exception {
        when(orchestrationService.processQuery(eq("Follow-up"), eq("existing-id")))
                .thenReturn(successResult("Understood."));
        when(conversationService.addMessage(any(), any(), any(), any()))
                .thenReturn(null);

        mockMvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Follow-up\",\"conversationId\":\"existing-id\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("existing-id"));

        verify(conversationService, never()).createConversation();
    }

    @Test
    void getConversation_existingId_returns200() throws Exception {
        ConversationEntity conv = new ConversationEntity();
        conv.setId("conv-123");
        when(conversationService.getConversationWithMessages("conv-123")).thenReturn(conv);

        mockMvc.perform(get("/api/assistant/conversation/conv-123"))
                .andExpect(status().isOk());
    }
}
