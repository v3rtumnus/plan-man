package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dto.health.ai.MealDraft;
import at.v3rtumnus.planman.entity.health.MealSlot;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
            - Zubereitungsfett gehört zum Bestandteil (paniertes Schnitzel inkl. Fett vom Backen).
            - Höchstens eine Rückfrage, und nur, wenn die Antwort die Gesamtkalorien um mehr als
              etwa 15 % ändert (z. B. Zubereitungsart, Sauce ja/nein). Sonst asked=false.
            - portionHint beschreibt die Menge anschaulich (Stück, EL, Schöpfer, Handfläche).
            - Erfinde keine Bestandteile, die weder im Satz noch am Foto vorkommen.
            """;

    private final AnthropicClient client;

    public AnthropicMealRecognizer(@Value("${planman.health.ai.api-key:}") String apiKey,
                                   @Value("${planman.health.ai.base-url:}") String baseUrl) {
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
            throw new MealRecognitionException("Keine KI konfiguriert: bitte manuell eintragen");
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
            log.warn("Meal recognition with {} failed: {}", model, e.getMessage());
            throw new MealRecognitionException("Die Erkennung ist gerade nicht erreichbar. Bitte nochmal versuchen oder manuell eintragen.", e);
        }
        int latencyMs = (int) ((System.nanoTime() - start) / 1_000_000);

        Optional<StopReason> stop = response.stopReason();
        if (stop.isPresent() && !StopReason.END_TURN.equals(stop.get())) {
            log.warn("Meal recognition with {} stopped with {}", model, stop.get());
            throw new MealRecognitionException("Die Erkennung hat keine Antwort geliefert. Bitte genauer beschreiben oder manuell eintragen.");
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
}
