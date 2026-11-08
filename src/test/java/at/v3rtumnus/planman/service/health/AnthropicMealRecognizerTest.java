package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.entity.health.MealSlot;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@WireMockTest
class AnthropicMealRecognizerTest {

    private static final String DRAFT = """
            {"title":"Skyr mit Banane","items":[
              {"name":"Skyr natur","grams":250,"kcalPer100g":63,"proteinPer100g":11,"carbsPer100g":4,"fatPer100g":0.2,"unsure":false,"portionHint":"1 Becher"},
              {"name":"Banane","grams":120,"kcalPer100g":93,"proteinPer100g":1.1,"carbsPer100g":20,"fatPer100g":0.2,"unsure":true,"portionHint":"1 Stück"}],
             "question":{"asked":false,"text":"","itemIndex":0,"options":[]}}""";

    private static String message(String stopReason, String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-5-5",
                 "content":[{"type":"text","text":"%s"}],
                 "stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":120,"output_tokens":80}}""".formatted(escaped, stopReason);
    }

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private AnthropicMealRecognizer recognizer(WireMockRuntimeInfo wm) {
        return new AnthropicMealRecognizer(new AnthropicGateway("test-key", wm.getHttpBaseUrl(), meters));
    }

    private double counter(String name, String... tags) {
        var counter = meters.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void withoutKey_isUnavailableAndRefuses() {
        AnthropicMealRecognizer recognizer = new AnthropicMealRecognizer(new AnthropicGateway("", "", meters));
        assertThat(recognizer.available()).isFalse();
        assertThatThrownBy(() -> recognizer.recognize("m", MealSlot.LUNCH, "x", null))
                .isInstanceOf(AiRequestException.class);
    }

    @Test
    void recognize_sendsTextAndPhotoAndParsesTheDraft(WireMockRuntimeInfo wm) {
        stubFor(post("/v1/messages").willReturn(okJson(message("end_turn", DRAFT))));

        MealRecognizer.Result result = recognizer(wm).recognize("claude-haiku-5-5", MealSlot.SNACK, "Skyr und Banane", "QUJD");

        assertThat(recognizer(wm).available()).isTrue();
        assertThat(result.model()).isEqualTo("claude-haiku-5-5");
        assertThat(result.draft().title()).isEqualTo("Skyr mit Banane");
        assertThat(result.draft().items()).hasSize(2);
        assertThat(result.draft().items().get(1).unsure()).isTrue();
        assertThat(result.draft().question().asked()).isFalse();
        assertThat(result.kcal()).isEqualTo(158 + 112);
        assertThat(result.rawJson()).contains("Skyr natur");
        assertThat(counter("planman.ai.tokens", "model", "claude-haiku-5-5", "purpose", "meal", "type", "input")).isEqualTo(120);
        assertThat(counter("planman.ai.tokens", "model", "claude-haiku-5-5", "purpose", "meal", "type", "output")).isEqualTo(80);
        assertThat(counter("planman.ai.cost.usd", "model", "claude-haiku-5-5", "purpose", "meal")).isCloseTo(0.000052, within(1e-9));
        assertThat(counter("planman.ai.requests", "model", "claude-haiku-5-5", "purpose", "meal", "outcome", "ok")).isEqualTo(1);

        var request = getAllServeEvents().getFirst().getRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        String body = request.getBodyAsString();
        assertThat(JsonPath.<String>read(body, "$.model")).isEqualTo("claude-haiku-5-5");
        assertThat(JsonPath.<String>read(body, "$.output_config.effort")).isEqualTo("low");
        assertThat(JsonPath.<String>read(body, "$.output_config.format.type")).isEqualTo("json_schema");
        assertThat(JsonPath.<String>read(body, "$.messages[0].content[0].type")).isEqualTo("image");
        assertThat(JsonPath.<String>read(body, "$.messages[0].content[0].source.media_type")).isEqualTo("image/jpeg");
        assertThat(JsonPath.<String>read(body, "$.messages[0].content[1].text"))
                .contains("Tageszeit: Jause").contains("Skyr und Banane");
        assertThat(body).doesNotContain("temperature");
    }

    @Test
    void recognize_photoOnly_saysSo(WireMockRuntimeInfo wm) {
        stubFor(post("/v1/messages").willReturn(okJson(message("end_turn", DRAFT))));

        recognizer(wm).recognize("claude-haiku-5-5", MealSlot.LUNCH, null, "QUJD");

        verify(postRequestedFor(urlEqualTo("/v1/messages"))
                .withRequestBody(matchingJsonPath("$.messages[0].content[1].text", containing("nur das Foto"))));
    }

    @Test
    void recognize_truncatedAnswer_fails(WireMockRuntimeInfo wm) {
        stubFor(post("/v1/messages").willReturn(okJson(message("max_tokens", DRAFT))));

        assertThatThrownBy(() -> recognizer(wm).recognize("claude-haiku-5-5", MealSlot.LUNCH, "x", null))
                .isInstanceOf(AiRequestException.class);
    }

    @Test
    void recognize_noItems_fails(WireMockRuntimeInfo wm) {
        stubFor(post("/v1/messages").willReturn(okJson(message("end_turn",
                "{\"title\":\"\",\"items\":[],\"question\":{\"asked\":false,\"text\":\"\",\"itemIndex\":0,\"options\":[]}}"))));

        assertThatThrownBy(() -> recognizer(wm).recognize("claude-haiku-5-5", MealSlot.LUNCH, "x", null))
                .isInstanceOf(AiRequestException.class)
                .hasMessageContaining("Keine Lebensmittel");
    }

    @Test
    void recognize_apiError_fails(WireMockRuntimeInfo wm) {
        stubFor(post("/v1/messages").willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad\"}}")));

        assertThatThrownBy(() -> recognizer(wm).recognize("claude-haiku-5-5", MealSlot.LUNCH, "x", null))
                .isInstanceOf(AiRequestException.class)
                .hasMessageContaining("nicht erreichbar");
    }

    @Test
    void recognize_creditsUsedUp_isABillingError(WireMockRuntimeInfo wm) {
        stubFor(post("/v1/messages").willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":"
                        + "\"Your credit balance is too low to access the Anthropic API. Please go to Plans & Billing to upgrade or purchase credits.\"}}")));

        assertThatThrownBy(() -> recognizer(wm).recognize("claude-sonnet-5-5", MealSlot.LUNCH, "x", null))
                .isInstanceOfSatisfying(AiRequestException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(AiRequestException.Reason.BILLING);
                    assertThat(e.getMessage()).contains("Guthaben");
                });
        assertThat(counter("planman.ai.requests", "model", "claude-sonnet-5-5", "purpose", "meal", "outcome", "billing")).isEqualTo(1);
    }

    @Test
    void recognize_spendLimitReached_isABillingError(WireMockRuntimeInfo wm) {
        stubFor(post("/v1/messages").willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":"
                        + "\"You have reached your specified API usage limits. You will regain access on 2026-12-01.\"}}")));

        assertThatThrownBy(() -> recognizer(wm).recognize("claude-sonnet-5-5", MealSlot.LUNCH, "x", null))
                .isInstanceOfSatisfying(AiRequestException.class,
                        e -> assertThat(e.getReason()).isEqualTo(AiRequestException.Reason.BILLING));
    }

    @Test
    void recognize_otherBadRequest_isUnavailableAndCounted(WireMockRuntimeInfo wm) {
        stubFor(post("/v1/messages").willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad\"}}")));

        assertThatThrownBy(() -> recognizer(wm).recognize("claude-sonnet-5-5", MealSlot.LUNCH, "x", null))
                .isInstanceOfSatisfying(AiRequestException.class,
                        e -> assertThat(e.getReason()).isEqualTo(AiRequestException.Reason.UNAVAILABLE));
        assertThat(counter("planman.ai.requests", "model", "claude-sonnet-5-5", "purpose", "meal", "outcome", "unavailable")).isEqualTo(1);
    }

    @Test
    void recognize_truncated_isCountedAsStop(WireMockRuntimeInfo wm) {
        stubFor(post("/v1/messages").willReturn(okJson(message("max_tokens", DRAFT))));

        assertThatThrownBy(() -> recognizer(wm).recognize("claude-sonnet-5-5", MealSlot.LUNCH, "x", null))
                .isInstanceOf(AiRequestException.class);
        assertThat(counter("planman.ai.requests", "model", "claude-sonnet-5-5", "purpose", "meal", "outcome", "stop_max_tokens")).isEqualTo(1);
    }
}
