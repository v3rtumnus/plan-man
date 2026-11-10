package at.v3rtumnus.planman.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the notification entities' columns to the ones changelogs 1_37 and 1_39 create (see HealthSchemaTest). */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class NotificationSchemaTest {

    private static final Map<String, Set<String>> CHANGELOG = Map.of(
            "notification_rule", Set.of("id", "name", "rule_type", "parameters_json", "targeting_mode", "enabled", "created_at"),
            "notification_rule_target_user", Set.of("rule_id", "user_profile_id"),
            "notification", Set.of("id", "rule_id", "user_profile_id", "title", "message", "severity", "url",
                    "actions_json", "push", "period_key", "created_at", "read_at", "pushed_at"),
            "push_subscription", Set.of("id", "user_profile_id", "endpoint", "p256dh", "auth", "device_label",
                    "created_at", "last_success_at", "last_failure_at", "last_error"),
            "job_status", Set.of("name", "last_success_at", "last_failure_at", "last_error"),
            "price_anchor", Set.of("isin", "anchor_date", "anchor_price_eur", "last_notified_on"));

    @MockitoBean
    private CacheManager cacheManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void entityColumnsMatchTheChangelog() {
        CHANGELOG.forEach((table, expected) -> {
            List<String> columns = jdbc.queryForList(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(TABLE_NAME) = ?", String.class, table);
            Set<String> actual = columns.stream().map(c -> c.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            assertThat(actual).as(table).isEqualTo(expected);
        });
    }
}
