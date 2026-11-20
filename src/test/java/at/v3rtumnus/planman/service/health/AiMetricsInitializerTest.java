package at.v3rtumnus.planman.service.health;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class AiMetricsInitializerTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @Test
    void startup_registersEveryConfiguredSeriesAtZero_andSkipsBlankModel() {
        new AiMetricsInitializer(new AnthropicGateway("", "", meters),
                "claude-sonnet-5-5", "", "claude-sonnet-5-5", "claude-opus-5-5");

        Counter reviewCost = meters.find("planman.ai.cost.usd").tags("model", "claude-opus-5-5", "purpose", "review").counter();
        assertThat(reviewCost).isNotNull();
        assertThat(reviewCost.count()).isZero();
        for (String purpose : new String[]{"meal", "activity"}) {
            assertThat(meters.find("planman.ai.tokens").tags("model", "claude-sonnet-5-5", "purpose", purpose, "type", "input").counter()).isNotNull();
            assertThat(meters.find("planman.ai.tokens").tags("model", "claude-sonnet-5-5", "purpose", purpose, "type", "output").counter()).isNotNull();
            for (String outcome : AnthropicGateway.TRACKED_OUTCOMES) {
                assertThat(meters.find("planman.ai.requests").tags("purpose", purpose, "outcome", outcome).counter()).isNotNull();
            }
        }
        // the blank compare model adds nothing: 3 model/purpose pairs × (2 tokens + 1 cost + 3 requests)
        assertThat(meters.getMeters()).hasSize(3 * 6);
    }

    @Test
    void callAfterStartup_incrementsThePreRegisteredSeries(WireMockRuntimeInfo wm) {
        AnthropicGateway gateway = new AnthropicGateway("test-key", wm.getHttpBaseUrl(), meters);
        new AiMetricsInitializer(gateway, "claude-sonnet-5-5", "", "claude-sonnet-5-5", "claude-opus-5-5");
        stubFor(post("/v1/messages").willReturn(okJson("""
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-sonnet-5-5",
                 "content":[{"type":"text","text":"{\\"title\\":\\"Laufen\\",\\"met\\":8.0,\\"durationMin\\":30}"}],
                 "stop_reason":"end_turn","stop_sequence":null,"usage":{"input_tokens":1000000,"output_tokens":0}}""")));
        ActivityEstimator estimator = new ActivityEstimator(gateway);
        ReflectionTestUtils.setField(estimator, "model", "claude-sonnet-5-5");

        estimator.estimate("30 min laufen");

        assertThat(meters.find("planman.ai.cost.usd").tags("purpose", "activity").counters()).hasSize(1);
        assertThat(meters.find("planman.ai.cost.usd").tags("purpose", "activity").counter().count()).isEqualTo(2.0);
        assertThat(meters.find("planman.ai.requests").tags("purpose", "activity", "outcome", "ok").counter().count()).isEqualTo(1);
    }
}
