package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.assistant.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {

    @Query("SELECT c FROM ConversationEntity c LEFT JOIN FETCH c.messages WHERE c.id = :id")
    ConversationEntity findByIdWithMessages(String id);
}
