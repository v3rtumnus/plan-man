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

/**
 * Tests run on H2 with Hibernate's own schema, not the Liquibase changelog. This pins the
 * column names Hibernate expects to the ones changelog 1_34 creates in MariaDB, so a
 * naming-strategy surprise (modelB → "modelb") fails here instead of in production.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MealSchemaTest {

    /** Columns as created by db.changelog_1_34.xml (checked against MariaDB on 2026-11-08). */
    private static final Map<String, Set<String>> CHANGELOG = Map.of(
            "meal", Set.of("id", "user_profile_id", "eaten_on", "slot", "title", "source", "kcal", "protein_g",
                    "carbs_g", "fat_g", "saved_meal_id", "meal_recognition_id", "created_at"),
            "meal_item", Set.of("id", "meal_id", "position", "name", "grams", "kcal_per_100g", "protein_per_100g",
                    "carbs_per_100g", "fat_per_100g", "unsure", "portion_hint"),
            "meal_recognition", Set.of("id", "user_profile_id", "created_at", "description", "had_photo", "model_a",
                    "result_a", "kcal_a", "latency_a_ms", "model_b", "result_b", "kcal_b", "latency_b_ms", "shown_model"),
            "saved_meal", Set.of("id", "user_profile_id", "name", "usual_slot", "use_count", "last_used_at", "created_at"),
            "saved_meal_item", Set.of("id", "saved_meal_id", "position", "name", "grams", "kcal_per_100g",
                    "protein_per_100g", "carbs_per_100g", "fat_per_100g", "portion_hint"));

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
