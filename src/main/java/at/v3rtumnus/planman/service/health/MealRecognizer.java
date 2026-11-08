package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dto.health.ai.MealDraft;
import at.v3rtumnus.planman.entity.health.MealSlot;

/** Turns a sentence and/or photo into an editable meal draft. */
public interface MealRecognizer {

    /** False when no API key is configured; capture then offers manual entry only. */
    boolean available();

    /**
     * @param photoJpegBase64 a JPEG, already shrunk in the browser, or null
     */
    Result recognize(String model, MealSlot slot, String description, String photoJpegBase64);

    record Result(String model, MealDraft draft, String rawJson, int latencyMs) {
        public int kcal() {
            return draft.items().stream()
                    .mapToInt(i -> (int) Math.round(i.kcalPer100g() * i.grams() / 100.0))
                    .sum();
        }
    }
}
