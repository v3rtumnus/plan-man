package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.notification.NotificationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, Long> {

    List<NotificationRule> findByEnabledTrue();
}
