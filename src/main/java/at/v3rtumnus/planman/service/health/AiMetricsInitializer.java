package at.v3rtumnus.planman.service.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Registers the AI metric series of every configured model at startup, so the first call after a
 * deploy shows up in Prometheus' increase() (see {@link AnthropicGateway#track}).
 */
@Component
public class AiMetricsInitializer {

    public AiMetricsInitializer(AnthropicGateway gateway,
                                @Value("${planman.health.ai.meal-model:claude-sonnet-5-5}") String mealModel,
                                @Value("${planman.health.ai.compare-model:}") String compareModel,
                                @Value("${planman.health.ai.activity-model:claude-sonnet-5-5}") String activityModel,
                                @Value("${planman.health.ai.review-model:claude-opus-5-5}") String reviewModel) {
        gateway.track(mealModel, "meal");
        gateway.track(compareModel, "meal");
        gateway.track(activityModel, "activity");
        gateway.track(reviewModel, "review");
    }
}
