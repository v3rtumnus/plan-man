package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dto.health.ai.MealDraft;
import at.v3rtumnus.planman.entity.health.MealSlot;
import com.anthropic.models.messages.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Meal recognition with Claude and structured outputs (model chosen by MealService). */
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

    private final AnthropicGateway gateway;

    public AnthropicMealRecognizer(AnthropicGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public boolean available() {
        return gateway.available();
    }

    @Override
    public Result recognize(String model, MealSlot slot, String description, String photoJpegBase64) {
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

        AnthropicGateway.Response<MealDraft> response = gateway.structured(model, "meal", SYSTEM_PROMPT, content, MealDraft.class);
        MealDraft draft = response.value();
        if (draft.items() == null || draft.items().isEmpty()) {
            throw new AiRequestException("Keine Lebensmittel erkannt. Bitte genauer beschreiben oder manuell eintragen.");
        }
        return new Result(model, draft, response.rawJson(), response.latencyMs());
    }
}
