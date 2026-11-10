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
 * column names Hibernate expects to the ones the health changelogs create in MariaDB, so a
 * naming-strategy surprise (modelB → "modelb") fails here instead of in production.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class HealthSchemaTest {

    /**
     * Columns as created by the health changelogs 1_33–1_35 (1_33/1_34 checked against MariaDB on
     * 2026-11-08; 1_35 adds workout and health_day.football_cancelled and drops
     * health_goal.freeletics_bonus_kcal; 1_36 drops exercise and exercise_weight again).
     */
    private static final Map<String, Set<String>> CHANGELOG = Map.of(
            "health_day", Set.of("id", "user_profile_id", "day_date", "health_goal_id", "kcal_target", "protein_target_g",
                    "steps_target", "steps", "resting_hr", "sleep_minutes", "paused", "football_cancelled"),
            "health_goal", Set.of("id", "user_profile_id", "title", "valid_from", "valid_to", "kcal_target",
                    "protein_target_g", "football_bonus_kcal", "steps_target", "start_weight_kg", "target_weight_kg",
                    "target_date", "push_ups_target", "created_at"),
            "workout", Set.of("id", "user_profile_id", "done_on", "type", "title", "duration_min", "intensity", "met",
                    "kcal", "bonus_kcal", "kcal_source", "created_at"),
            "meal", Set.of("id", "user_profile_id", "eaten_on", "slot", "title", "source", "kcal", "protein_g",
                    "carbs_g", "fat_g", "saved_meal_id", "meal_recognition_id", "created_at"),
            "meal_item", Set.of("id", "meal_id", "position", "name", "grams", "kcal_per_100g", "protein_per_100g",
                    "carbs_per_100g", "fat_per_100g", "unsure", "portion_hint"),
            "meal_recognition", Set.of("id", "user_profile_id", "created_at", "description", "had_photo", "model_a",
                    "result_a", "kcal_a", "latency_a_ms", "model_b", "result_b", "kcal_b", "latency_b_ms", "shown_model"),
            "saved_meal", Set.of("id", "user_profile_id", "name", "usual_slot", "use_count", "last_used_at", "created_at"),
            "saved_meal_item", Set.of("id", "saved_meal_id", "position", "name", "grams", "kcal_per_100g",
                    "protein_per_100g", "carbs_per_100g", "fat_per_100g", "portion_hint"),
            // 1_38
            "weekly_review", Set.of("id", "user_profile_id", "week_start", "logged_days", "paused_days", "kcal_avg",
                    "kcal_target_avg", "protein_avg_g", "protein_target_g", "protein_days", "freeletics_sessions",
                    "football_played", "training_kcal", "weight_trend_kg", "weight_change_kg", "weekly_rate_kg", "waist_cm",
                    "waist_change_cm", "push_ups", "goal_check", "goal_status", "headline", "feedback", "goal_reason",
                    "proposed_kcal_target", "proposed_protein_target_g", "proposed_target_date", "proposal_reason",
                    "feedback_status", "model", "feedback_at", "error", "decision", "decided_at", "created_at"),
            // 1_41
            "food", Set.of("barcode", "name", "brand", "kcal_per_100g", "protein_per_100g", "carbs_per_100g",
                    "fat_per_100g", "serving_g", "serving_hint", "source", "updated_at"));

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
        for (String dropped : List.of("exercise", "exercise_weight")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = ?",
                    Integer.class, dropped)).as(dropped).isZero();
        }
    }
}
