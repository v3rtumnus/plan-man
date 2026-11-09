package at.v3rtumnus.planman.service.health;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.RequestOptions;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The one way plan-man calls Claude: structured output into a Java record, billing errors told
 * apart from outages, and token/cost metrics per model and purpose.
 */
@Component
@Slf4j
public class AnthropicGateway {

    /** USD per million input / output tokens (checked 2026-11-08); unknown models count as Sonnet. */
    static final Map<String, double[]> PRICES = Map.of(
            "claude-haiku-5-5", new double[]{0.10, 0.50},
            "claude-sonnet-5-5", new double[]{2.00, 10.00},
            "claude-opus-5-5", new double[]{4.00, 20.00});

    static final String BILLING_MESSAGE = "Das Anthropic-Guthaben ist aufgebraucht oder das Ausgabenlimit erreicht. "
            + "Bitte manuell eintragen und in der Anthropic-Console aufladen.";
    static final String UNAVAILABLE_MESSAGE = "Die KI ist gerade nicht erreichbar. Bitte nochmal versuchen oder manuell eintragen.";

    private final AnthropicClient client;
    private final MeterRegistry meters;

    public AnthropicGateway(@Value("${planman.health.ai.api-key:}") String apiKey,
                            @Value("${planman.health.ai.base-url:}") String baseUrl,
                            MeterRegistry meters) {
        this.meters = meters;
        if (apiKey == null || apiKey.isBlank()) {
            this.client = null;
            log.info("No Anthropic API key configured: AI estimates are off, manual entry only");
            return;
        }
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(45))
                .maxRetries(1);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        this.client = builder.build();
    }

    public boolean available() {
        return client != null;
    }

    public record Response<T>(T value, String rawJson, int latencyMs) {
    }

    /**
     * @param purpose metrics tag, e.g. "meal" or "activity"
     */
    public <T> Response<T> structured(String model, String purpose, String system, List<ContentBlockParam> content,
                                      Class<T> type) {
        return structured(model, purpose, system, content, type, OutputConfig.Effort.LOW, 8000L, null);
    }

    /**
     * @param timeout per-request timeout instead of the client's 45 s (Opus over a month of data
     *                takes longer); null keeps the default
     */
    public <T> Response<T> structured(String model, String purpose, String system, List<ContentBlockParam> content,
                                      Class<T> type, OutputConfig.Effort effort, long maxTokens, Duration timeout) {
        if (client == null) {
            throw new AiRequestException(AiRequestException.Reason.UNAVAILABLE,
                    "Keine KI konfiguriert: bitte manuell eintragen", null);
        }
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(system)
                .outputConfig(StructuredOutputConfig.<T>builder()
                        .effort(effort)
                        .format(type)
                        .build())
                .addUserMessageOfContentBlockParams(content)
                .build();

        long start = System.nanoTime();
        StructuredMessage<T> response;
        try {
            response = timeout == null
                    ? client.messages().create(params)
                    : client.messages().create(params, RequestOptions.builder().timeout(timeout).build());
        } catch (AnthropicException e) {
            boolean billing = isBillingError(e);
            log.warn("Claude {} request with {} failed{}: {}", purpose, model, billing ? " (billing)" : "", e.getMessage());
            count(model, purpose, billing ? "billing" : "unavailable");
            throw new AiRequestException(billing ? AiRequestException.Reason.BILLING : AiRequestException.Reason.UNAVAILABLE,
                    billing ? BILLING_MESSAGE : UNAVAILABLE_MESSAGE, e);
        }
        int latencyMs = (int) ((System.nanoTime() - start) / 1_000_000);
        countUsage(model, purpose, response.usage().inputTokens(), response.usage().outputTokens());

        Optional<StopReason> stop = response.stopReason();
        if (stop.isPresent() && !StopReason.END_TURN.equals(stop.get())) {
            log.warn("Claude {} request with {} stopped with {}", purpose, model, stop.get());
            count(model, purpose, "stop_" + stop.get().toString().toLowerCase(Locale.ROOT));
            throw new AiRequestException(AiRequestException.Reason.UNAVAILABLE,
                    "Die KI hat keine Antwort geliefert. Bitte genauer beschreiben oder manuell eintragen.", null);
        }
        T value = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(StructuredTextBlock::text)
                .findFirst()
                .orElseThrow(() -> new AiRequestException("Die KI hat keine Antwort geliefert."));
        String raw = response.rawMessage().content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .findFirst()
                .orElse(null);
        count(model, purpose, "ok");
        log.info("Claude {} request with {} in {} ms, usage in={} out={}", purpose, model, latencyMs,
                response.usage().inputTokens(), response.usage().outputTokens());
        return new Response<>(value, raw, latencyMs);
    }

    /**
     * Anthropic answers used-up prepaid credits with a 400 "credit balance is too low" and a
     * reached workspace spend limit with "usage limits"; neither gets better by retrying.
     */
    static boolean isBillingError(AnthropicException e) {
        String message = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
        boolean billingText = message.contains("credit balance") || message.contains("usage limit")
                || message.contains("billing");
        if (e instanceof AnthropicServiceException service) {
            int status = service.statusCode();
            return billingText && (status == 400 || status == 402 || status == 403 || status == 429);
        }
        return false;
    }

    private void countUsage(String model, String purpose, long inputTokens, long outputTokens) {
        double[] price = PRICES.getOrDefault(model, PRICES.get("claude-sonnet-5-5"));
        Counter.builder("planman.ai.tokens").tag("model", model).tag("purpose", purpose).tag("type", "input")
                .description("Tokens sent to and received from the Anthropic API")
                .register(meters).increment(inputTokens);
        Counter.builder("planman.ai.tokens").tag("model", model).tag("purpose", purpose).tag("type", "output")
                .register(meters).increment(outputTokens);
        Counter.builder("planman.ai.cost.usd").tag("model", model).tag("purpose", purpose)
                .description("Estimated Anthropic API cost from token counts and list prices")
                .register(meters).increment((inputTokens * price[0] + outputTokens * price[1]) / 1_000_000.0);
    }

    private void count(String model, String purpose, String outcome) {
        Counter.builder("planman.ai.requests").tag("model", model).tag("purpose", purpose).tag("outcome", outcome)
                .description("Anthropic API requests by outcome (ok, billing, unavailable, stop_*)")
                .register(meters).increment();
    }
}
