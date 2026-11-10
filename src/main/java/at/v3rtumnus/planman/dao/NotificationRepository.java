package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.notification.Notification;
import at.v3rtumnus.planman.entity.notification.NotificationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByRecipientUsernameAndReadAtIsNull(String username);

    List<Notification> findTop5ByRecipientUsernameAndReadAtIsNullOrderByCreatedAtDesc(String username);

    List<Notification> findByRecipientUsernameAndReadAtIsNullOrderByCreatedAtDesc(String username);

    List<Notification> findTop30ByRecipientUsernameAndReadAtIsNotNullOrderByCreatedAtDesc(String username);

    List<Notification> findByReadAtIsNullOrderByCreatedAtDesc();

    long countByRuleAndCreatedAtAfter(NotificationRule rule, LocalDateTime after);

    long countByRuleAndCreatedAtAfterAndReadAtIsNotNull(NotificationRule rule, LocalDateTime after);

    /** Pushes of one rule family (e.g. "HEALTH_") a user got since a time: the daily push cap. */
    long countByRecipientUsernameAndPushTrueAndCreatedAtGreaterThanEqualAndRuleRuleTypeStartingWith(
            String username, LocalDateTime since, String ruleTypePrefix);

    boolean existsByRuleAndRecipientUsernameAndPeriodKey(NotificationRule rule, String username, String periodKey);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipient.username = :username AND n.readAt IS NULL")
    int markAllRead(@Param("username") String username, @Param("now") LocalDateTime now);

    /**
     * Marks every user's unread notifications of a rule whose period key starts with a prefix.
     * Opens its own transaction: the health nudges call it from the scheduler thread, where none is active.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.rule = :rule AND n.periodKey LIKE CONCAT(:prefix, '%') AND n.readAt IS NULL")
    int markReadByPeriodKeyPrefix(@Param("rule") NotificationRule rule, @Param("prefix") String prefix,
                                  @Param("now") LocalDateTime now);
}
