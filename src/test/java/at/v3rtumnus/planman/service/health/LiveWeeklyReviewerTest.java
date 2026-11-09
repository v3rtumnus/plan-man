package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dto.health.ai.ReviewDraft;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Real API with Opus (a few cents); runs only with ANTHROPIC_LIVE_TEST=true. Checks the schema is accepted. */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_LIVE_TEST", matches = "true")
class LiveWeeklyReviewerTest {

    @Test
    void writesFeedbackAndGoalCheck() {
        AnthropicWeeklyReviewer reviewer = new AnthropicWeeklyReviewer(
                new AnthropicGateway(System.getenv("ANTHROPIC_API_KEY"), "", new SimpleMeterRegistry()), "claude-opus-5-5");
        ReviewDraft draft = reviewer.review(AnthropicWeeklyReviewerTest.goal(),
                AnthropicWeeklyReviewerTest.week(AnthropicWeeklyReviewerTest.MONDAY, new BigDecimal("82.4")),
                List.of(AnthropicWeeklyReviewerTest.week(AnthropicWeeklyReviewerTest.MONDAY.minusWeeks(1), new BigDecimal("82.5"))),
                List.of("12.10. 97", "02.11. 96,5"), List.of("06.11. 14"), true);
        System.out.println("LIVE>> " + draft);
        assertThat(draft.feedback()).isNotBlank();
        assertThat(draft.goalStatus()).isNotNull();
    }
}
