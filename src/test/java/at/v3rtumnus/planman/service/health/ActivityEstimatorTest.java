package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dto.health.ai.ActivityDraft;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class ActivityEstimatorTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private ActivityEstimator estimator(WireMockRuntimeInfo wm) {
        ActivityEstimator estimator = new ActivityEstimator(new AnthropicGateway("test-key", wm.getHttpBaseUrl(), meters));
        ReflectionTestUtils.setField(estimator, "model", "claude-sonnet-5-5");
        return estimator;
    }

    private static void answer(String json) {
        String escaped = json.replace("\"", "\\\"");
        stubFor(post("/v1/messages").willReturn(okJson("""
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-sonnet-5-5",
                 "content":[{"type":"text","text":"%s"}],"stop_reason":"end_turn","stop_sequence":null,
                 "usage":{"input_tokens":300,"output_tokens":40}}""".formatted(escaped))));
    }

    @Test
    void estimate_returnsTitleMetAndDuration_andCountsActivityUsage(WireMockRuntimeInfo wm) {
        answer("{\"title\":\"Tennis Doppel\",\"met\":5.0,\"durationMin\":60}");

        ActivityDraft draft = estimator(wm).estimate("1 Stunde Tennis Doppel");

        assertThat(draft.title()).isEqualTo("Tennis Doppel");
        assertThat(draft.met()).isEqualTo(5.0);
        assertThat(draft.durationMin()).isEqualTo(60);
        assertThat(estimator(wm).available()).isTrue();
        assertThat(meters.find("planman.ai.requests").tags("purpose", "activity", "outcome", "ok").counter().count()).isEqualTo(1);
        verify(postRequestedFor(urlEqualTo("/v1/messages"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("claude-sonnet-5-5")))
                .withRequestBody(matchingJsonPath("$.messages[0].content[0].text", equalTo("1 Stunde Tennis Doppel"))));
    }

    @Test
    void estimate_implausibleMet_orEmptyText_fails(WireMockRuntimeInfo wm) {
        answer("{\"title\":\"Schlafen\",\"met\":0.9,\"durationMin\":0}");
        assertThatThrownBy(() -> estimator(wm).estimate("schlafen")).isInstanceOf(AiRequestException.class);
        assertThatThrownBy(() -> estimator(wm).estimate(" ")).isInstanceOf(AiRequestException.class);
    }
}
