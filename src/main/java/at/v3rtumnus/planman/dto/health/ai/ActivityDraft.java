package at.v3rtumnus.planman.dto.health.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** The model's estimate for one activity described in a sentence. */
public record ActivityDraft(
        @JsonPropertyDescription("Kurzer deutscher Name der Aktivität, z. B. \"Tennis Doppel\" oder \"Gartenarbeit\"")
        String title,
        @JsonPropertyDescription("MET-Wert nach dem Compendium of Physical Activities, passend zur beschriebenen Intensität")
        double met,
        @JsonPropertyDescription("Dauer in Minuten, wenn im Satz genannt, sonst 0")
        int durationMin) {
}
