package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.entity.assistant.ConversationEntity;
import at.v3rtumnus.planman.entity.assistant.MessageEntity;

public interface ConversationService {

    ConversationEntity createConversation();

    ConversationEntity getOrCreateConversation(String id);

    MessageEntity addMessage(String conversationId, String role, String content, String traceId);

    ConversationEntity getConversationWithMessages(String id);
}
