package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.notification.Notification;
import at.v3rtumnus.planman.entity.notification.NotificationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByRecipientUsernameAndReadAtIsNull(String username);

    List<Notification> findTop5ByRecipientUsernameAndReadAtIsNullOrderByCreatedAtDesc(String username);

    List<Notification> findByRecipientUsernameAndReadAtIsNullOrderByCreatedAtDesc(String username);

    List<Notification> findTop30ByRecipientUsernameAndReadAtIsNotNullOrderByCreatedAtDesc(String username);

    List<Notification> findByReadAtIsNullOrderByCreatedAtDesc();

    boolean existsByRuleAndRecipientUsernameAndPeriodKey(NotificationRule rule, String username, String periodKey);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipient.username = :username AND n.readAt IS NULL")
    int markAllRead(@Param("username") String username, @Param("now") LocalDateTime now);
}
