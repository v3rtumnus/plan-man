package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.entity.health.MealSlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the real API (costs a fraction of a cent). Runs only with ANTHROPIC_LIVE_TEST=true and
 * ANTHROPIC_API_KEY set, e.g. to check a new model or SDK version.
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_LIVE_TEST", matches = "true")
class LiveMealRecognizerTest {

    @Test
    void recognizesAnAustrianLunch() {
        AnthropicMealRecognizer recognizer = new AnthropicMealRecognizer(System.getenv("ANTHROPIC_API_KEY"), "", new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        for (String model : new String[]{"claude-haiku-5-5", "claude-sonnet-5-5"}) {
            for (String meal : new String[]{"Putenschnitzel mit Erdäpfelsalat und Preiselbeeren",
                    "Acht Schätze vom Chinarestaurant mit Reis"}) {
                MealRecognizer.Result result = recognizer.recognize(model, MealSlot.LUNCH, meal, null);
                System.out.println("LIVE>> " + model + " " + result.latencyMs() + " ms, " + result.kcal() + " kcal: "
                        + result.draft());
                assertThat(result.draft().items()).isNotEmpty();
                assertThat(result.kcal()).isBetween(400, 1800);
            }
        }
    }
}
