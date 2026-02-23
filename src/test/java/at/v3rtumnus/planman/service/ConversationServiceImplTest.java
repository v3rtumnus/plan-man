package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.ConversationRepository;
import at.v3rtumnus.planman.entity.assistant.ConversationEntity;
import at.v3rtumnus.planman.entity.assistant.MessageEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private ConversationServiceImpl service;

    // --- createConversation ---

    @Test
    void createConversation_savesEntityWithUUIDAndTimestamp() {
        ArgumentCaptor<ConversationEntity> captor = ArgumentCaptor.forClass(ConversationEntity.class);
        when(conversationRepository.save(any(ConversationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConversationEntity result = service.createConversation();

        verify(conversationRepository).save(captor.capture());
        ConversationEntity saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).hasSize(36); // UUID format
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(result).isNotNull();
    }

    // --- getOrCreateConversation ---

    @Test
    void getOrCreateConversation_existingId_returnsExisting() {
        ConversationEntity existing = new ConversationEntity();
        existing.setId("existing-id");

        when(conversationRepository.findById("existing-id")).thenReturn(Optional.of(existing));

        ConversationEntity result = service.getOrCreateConversation("existing-id");

        assertThat(result.getId()).isEqualTo("existing-id");
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void getOrCreateConversation_nonExistentId_createsNew() {
        when(conversationRepository.findById("new-id")).thenReturn(Optional.empty());
        when(conversationRepository.save(any(ConversationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConversationEntity result = service.getOrCreateConversation("new-id");

        assertThat(result.getId()).isEqualTo("new-id");
        verify(conversationRepository).save(any(ConversationEntity.class));
    }

    // --- addMessage ---

    @Test
    void addMessage_addsMessageWithCorrectRoleAndContent() {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId("conv-1");

        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MessageEntity msg = service.addMessage("conv-1", "user", "Hello!", "trace-123");

        assertThat(msg.getRole()).isEqualTo("user");
        assertThat(msg.getContent()).isEqualTo("Hello!");
        assertThat(msg.getTraceId()).isEqualTo("trace-123");
        assertThat(conversation.getMessages()).hasSize(1);
    }

    @Test
    void addMessage_createsConversationIfNotFound() {
        when(conversationRepository.findById("missing")).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MessageEntity msg = service.addMessage("missing", "assistant", "Hi!", null);

        assertThat(msg.getRole()).isEqualTo("assistant");
        verify(conversationRepository, atLeast(2)).save(any());
    }

    // --- getConversationWithMessages ---

    @Test
    void getConversationWithMessages_existingConversation_returnsIt() {
        ConversationEntity existing = new ConversationEntity();
        existing.setId("conv-xyz");

        when(conversationRepository.findByIdWithMessages("conv-xyz")).thenReturn(existing);

        ConversationEntity result = service.getConversationWithMessages("conv-xyz");

        assertThat(result.getId()).isEqualTo("conv-xyz");
    }

    @Test
    void getConversationWithMessages_notFound_createsNew() {
        when(conversationRepository.findByIdWithMessages("ghost")).thenReturn(null);
        when(conversationRepository.findById("ghost")).thenReturn(Optional.empty());
        when(conversationRepository.save(any(ConversationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConversationEntity result = service.getConversationWithMessages("ghost");

        assertThat(result).isNotNull();
    }
}
