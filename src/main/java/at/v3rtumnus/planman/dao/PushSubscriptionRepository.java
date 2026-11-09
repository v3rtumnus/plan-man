package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.notification.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByUserUsernameOrderByCreatedAtDesc(String username);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    long countByUserUsername(String username);
}
