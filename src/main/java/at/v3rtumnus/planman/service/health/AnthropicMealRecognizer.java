package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dto.health.ai.MealDraft;
import at.v3rtumnus.planman.entity.health.MealSlot;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Meal recognition with Claude (Haiku by default) and structured outputs. */
@Service
@Slf4j
public class AnthropicMealRecognizer implements MealRecognizer {

    static final String SYSTEM_PROMPT = """
            Du schätzt Mahlzeiten für ein persönliches Kalorien- und Protein-Tagebuch in Österreich.
            Eingabe: ein deutscher Satz und/oder ein Foto des Tellers, dazu die Tageszeit (Frühstück,
            Jause, Mittag, Abend). Gib die Bestandteile mit realistischen Mengen in Gramm und
            Nährwerten pro 100 g zurück (übliche Werte wie in österreichischen Nährwerttabellen).

            Regeln:
            - Nenne Mengen, die zur Beschreibung passen. Fehlt eine Menge, nimm eine übliche Portion
              für einen erwachsenen Mann und setze unsure auf true.
            - Ein Foto zeigt die Menge: schätze sie am Teller (Tellergröße ~26 cm). Was am Foto nicht
              erkennbar ist (Öl, Butter, Sauce), schätze üblich und markiere es als unsure.
            - Fertigprodukte und Kantinenessen: übliche Rezeptur, keine Diätvarianten annehmen.
            - Restaurant, Lieferservice, Imbiss, Kantine: Portionen wie dort üblich, nicht wie zu
              Hause. Sie sind meist größer und fetter: ein Hauptgericht hat oft 350–450 g, eine
              Beilage Reis oder Nudeln 250–300 g gekocht. Asiatisch gebraten oder in Sauce:
              2–3 EL Öl pro Portion einrechnen (ca. 150–200 kcal/100 g statt 100–130).
              Im Zweifel die übliche Restaurantmenge nehmen, nicht die kleinere.
            - "Reis" ohne Zusatz ist gekochter Reis, nicht gebratener.
            - Zubereitungsfett gehört zum Bestandteil. Paniert und gebacken (Schnitzel, Cordon bleu,
              Backhendl) heißt Panier plus aufgesogenes Fett: meist 200–260 kcal/100 g, nicht die
              Werte des rohen Fleisches.
            - Höchstens eine Rückfrage, und nur, wenn die Antwort die Gesamtkalorien um mehr als
              etwa 15 % ändert (z. B. Zubereitungsart, Sauce ja/nein). Sonst asked=false.
            - portionHint beschreibt die Menge anschaulich (Stück, EL, Schöpfer, Handfläche).
            - Erfinde keine Bestandteile, die weder im Satz noch am Foto vorkommen.
            """;

    /** USD per million input / output tokens (checked 2026-11-08); unknown models count as Sonnet. */
    static final Map<String, double[]> PRICES = Map.of(
            "claude-haiku-5-5", new double[]{0.10, 0.50},
            "claude-sonnet-5-5", new double[]{2.00, 10.00});

    static final String BILLING_MESSAGE = "Das Anthropic-Guthaben ist aufgebraucht oder das Ausgabenlimit erreicht. "
            + "Bitte manuell eintragen und in der Anthropic-Console aufladen.";

    private final AnthropicClient client;
    private final MeterRegistry meters;

    public AnthropicMealRecognizer(@Value("${planman.health.ai.api-key:}") String apiKey,
                                   @Value("${planman.health.ai.base-url:}") String baseUrl,
                                   MeterRegistry meters) {
        this.meters = meters;
        if (apiKey == null || apiKey.isBlank()) {
            this.client = null;
            log.info("No Anthropic API key configured: meal recognition is off, manual entry only");
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

    @Override
    public boolean available() {
        return client != null;
    }

    @Override
    public Result recognize(String model, MealSlot slot, String description, String photoJpegBase64) {
        if (client == null) {
            throw new MealRecognitionException(MealRecognitionException.Reason.UNAVAILABLE,
                    "Keine KI konfiguriert: bitte manuell eintragen", null);
        }
        List<ContentBlockParam> content = new ArrayList<>();
        if (photoJpegBase64 != null && !photoJpegBase64.isBlank()) {
            content.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                    .source(Base64ImageSource.builder()
                            .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                            .data(photoJpegBase64)
                            .build())
                    .build()));
        }
        String text = "Tageszeit: " + slot.getLabel() + "\n"
                + (description == null || description.isBlank()
                ? "Keine Beschreibung, nur das Foto."
                : "Beschreibung: " + description.trim());
        content.add(ContentBlockParam.ofText(TextBlockParam.builder().text(text).build()));

        StructuredMessageCreateParams<MealDraft> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(8000L)
                .system(SYSTEM_PROMPT)
                .outputConfig(StructuredOutputConfig.<MealDraft>builder()
                        .effort(OutputConfig.Effort.LOW)
                        .format(MealDraft.class)
                        .build())
                .addUserMessageOfContentBlockParams(content)
                .build();

        long start = System.nanoTime();
        StructuredMessage<MealDraft> response;
        try {
            response = client.messages().create(params);
        } catch (AnthropicException e) {
            boolean billing = isBillingError(e);
            log.warn("Meal recognition with {} failed{}: {}", model, billing ? " (billing)" : "", e.getMessage());
            countError(model, billing ? "billing" : "unavailable");
            if (billing) {
                throw new MealRecognitionException(MealRecognitionException.Reason.BILLING, BILLING_MESSAGE, e);
            }
            throw new MealRecognitionException(MealRecognitionException.Reason.UNAVAILABLE,
                    "Die Erkennung ist gerade nicht erreichbar. Bitte nochmal versuchen oder manuell eintragen.", e);
        }
        countUsage(model, response.usage().inputTokens(), response.usage().outputTokens());
        int latencyMs = (int) ((System.nanoTime() - start) / 1_000_000);

        Optional<StopReason> stop = response.stopReason();
        if (stop.isPresent() && !StopReason.END_TURN.equals(stop.get())) {
            log.warn("Meal recognition with {} stopped with {}", model, stop.get());
            countError(model, "stop_" + stop.get().toString().toLowerCase(Locale.ROOT));
            throw new MealRecognitionException(MealRecognitionException.Reason.UNAVAILABLE,
                    "Die Erkennung hat keine Antwort geliefert. Bitte genauer beschreiben oder manuell eintragen.", null);
        }
        MealDraft draft = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(StructuredTextBlock::text)
                .findFirst()
                .orElseThrow(() -> new MealRecognitionException("Die Erkennung hat keine Antwort geliefert."));
        if (draft.items() == null || draft.items().isEmpty()) {
            throw new MealRecognitionException("Keine Lebensmittel erkannt. Bitte genauer beschreiben oder manuell eintragen.");
        }
        String raw = response.rawMessage().content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .findFirst()
                .orElse(null);
        log.info("Meal recognised with {} in {} ms: {} items, usage in={} out={}", model, latencyMs,
                draft.items().size(), response.usage().inputTokens(), response.usage().outputTokens());
        return new Result(model, draft, raw, latencyMs);
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

    private void countUsage(String model, long inputTokens, long outputTokens) {
        double[] price = PRICES.getOrDefault(model, PRICES.get("claude-sonnet-5-5"));
        Counter.builder("planman.ai.tokens").tag("model", model).tag("type", "input")
                .description("Tokens sent to and received from the Anthropic API")
                .register(meters).increment(inputTokens);
        Counter.builder("planman.ai.tokens").tag("model", model).tag("type", "output")
                .register(meters).increment(outputTokens);
        Counter.builder("planman.ai.cost.usd").tag("model", model)
                .description("Estimated Anthropic API cost from token counts and list prices")
                .register(meters).increment((inputTokens * price[0] + outputTokens * price[1]) / 1_000_000.0);
        Counter.builder("planman.ai.requests").tag("model", model).tag("outcome", "ok")
                .register(meters).increment();
    }

    private void countError(String model, String outcome) {
        Counter.builder("planman.ai.requests").tag("model", model).tag("outcome", outcome)
                .description("Anthropic API requests by outcome (ok, billing, unavailable, stop_*)")
                .register(meters).increment();
    }
}
