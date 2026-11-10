package at.v3rtumnus.planman.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The update queries must work without a caller transaction: the hourly notification job calls
 * them from the scheduler thread (health nudges failed with "No active transaction" on 2026-11-10).
 * {@code @DataJpaTest} wraps each test in a transaction, which would hide that, so it is switched off here.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class NotificationRepositoryTest {

    @MockitoBean
    private CacheManager cacheManager;

    @Autowired
    private NotificationRepository notifications;

    @Test
    void markReadByPeriodKeyPrefixRunsWithoutCallerTransaction() {
        assertThat(notifications.markReadByPeriodKeyPrefix(null, "weight-", LocalDateTime.now())).isZero();
    }

    @Test
    void markAllReadRunsWithoutCallerTransaction() {
        assertThat(notifications.markAllRead("nobody", LocalDateTime.now())).isZero();
    }
}
