package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dto.health.ai.ActivityDraft;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

/** Real API (a few cents at most); runs only with ANTHROPIC_LIVE_TEST=true. */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_LIVE_TEST", matches = "true")
class LiveActivityEstimatorTest {

    @Test
    void estimatesTypicalActivities() {
        ActivityEstimator estimator = new ActivityEstimator(
                new AnthropicGateway(System.getenv("ANTHROPIC_API_KEY"), "", new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(estimator, "model", "claude-sonnet-5-5");
        String[] sentences = {"1 Stunde Tennis Doppel", "2 Stunden Gartenarbeit, Rasen mähen und Hecke schneiden",
                "45 min locker schwimmen", "Skitag", "30 min Joggen, ziemlich flott", "Wandern mit 600 Höhenmetern, 3 Stunden",
                "Yoga 60 min", "Umzug geholfen, Kisten tragen den ganzen Nachmittag"};
        for (String sentence : sentences) {
            ActivityDraft d = estimator.estimate(sentence);
            int minutes = d.durationMin() > 0 ? d.durationMin() : 60;
            System.out.printf("LIVE>> %-55s → %-35s MET %4.1f  %3d min  net +%d kcal%n", sentence, d.title(), d.met(),
                    d.durationMin(), TrainingService.netKcal(d.met(), new BigDecimal("83"), minutes));
        }
    }
}
