package at.v3rtumnus.planman.dto.health.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** What the model returns for one capture: the JSON schema of the structured output. */
public record MealDraft(
        @JsonPropertyDescription("Kurzer deutscher Name der Mahlzeit, z. B. \"Putenschnitzel mit Erdäpfelsalat\"")
        String title,
        @JsonPropertyDescription("Die einzelnen Bestandteile, Hauptkomponente zuerst")
        List<DraftItem> items,
        @JsonPropertyDescription("Höchstens eine Rückfrage, nur wenn die Antwort die Kalorien deutlich ändert")
        DraftQuestion question) {

    public record DraftItem(
            @JsonPropertyDescription("Deutscher Name, österreichische Begriffe (Erdäpfel, Topfen, Semmel)")
            String name,
            @JsonPropertyDescription("Geschätzte Menge in Gramm (Getränke: ml als Gramm)")
            int grams,
            double kcalPer100g,
            double proteinPer100g,
            double carbsPer100g,
            double fatPer100g,
            @JsonPropertyDescription("true, wenn die Menge nur grob geschätzt ist und geprüft werden sollte")
            boolean unsure,
            @JsonPropertyDescription("Anschauliche Portionsangabe, z. B. \"≈ 1 Stück, handgroß\" oder \"≈ 2 EL\"")
            String portionHint) {
    }

    public record DraftQuestion(
            @JsonPropertyDescription("false, wenn keine Rückfrage nötig ist; dann text leer und options leer")
            boolean asked,
            @JsonPropertyDescription("Die Frage, kurz, z. B. \"Wie wurde das Schnitzel gebacken?\"")
            String text,
            @JsonPropertyDescription("Index (0-basiert) des Bestandteils in items, den die Antwort betrifft")
            int itemIndex,
            @JsonPropertyDescription("2 bis 3 Antworten; jede mit den vollständigen Werten pro 100 g für diesen Bestandteil. Die erste entspricht der Schätzung in items")
            List<DraftOption> options) {
    }

    public record DraftOption(
            String label,
            double kcalPer100g,
            double proteinPer100g,
            double carbsPer100g,
            double fatPer100g) {
    }
}
