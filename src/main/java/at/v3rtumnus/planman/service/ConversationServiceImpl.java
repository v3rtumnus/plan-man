package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.ConversationRepository;
import at.v3rtumnus.planman.entity.assistant.ConversationEntity;
import at.v3rtumnus.planman.entity.assistant.MessageEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;

    @Override
    @Transactional
    public ConversationEntity createConversation() {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(UUID.randomUUID().toString());
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());

        ConversationEntity saved = conversationRepository.save(conversation);
        log.info("Created new conversation: {}", saved.getId());

        return saved;
    }

    @Override
    @Transactional
    public ConversationEntity getOrCreateConversation(String id) {
        return conversationRepository.findById(id)
                .orElseGet(() -> {
                    ConversationEntity conversation = new ConversationEntity();
                    conversation.setId(id);
                    conversation.setCreatedAt(LocalDateTime.now());
                    conversation.setUpdatedAt(LocalDateTime.now());
                    return conversationRepository.save(conversation);
                });
    }

    @Override
    @Transactional
    public MessageEntity addMessage(String conversationId, String role, String content, String traceId) {
        ConversationEntity conversation = getOrCreateConversation(conversationId);

        MessageEntity message = new MessageEntity(role, content);
        message.setTraceId(traceId);

        conversation.addMessage(message);
        conversationRepository.save(conversation);

        log.debug("Added {} message to conversation {}: {} chars", role, conversationId, content.length());

        return message;
    }

    @Override
    @Cacheable(value = "conversation-summaries", key = "#id")
    @Transactional(readOnly = true)
    public ConversationEntity getConversationWithMessages(String id) {
        ConversationEntity conversation = conversationRepository.findByIdWithMessages(id);

        if (conversation == null) {
            log.warn("Conversation not found: {}", id);
            return getOrCreateConversation(id);
        }

        log.info("Retrieved conversation {} with {} messages", id, conversation.getMessages().size());

        return conversation;
    }
}
