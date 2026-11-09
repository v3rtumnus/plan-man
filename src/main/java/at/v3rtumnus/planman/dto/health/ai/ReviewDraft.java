package at.v3rtumnus.planman.dto.health.ai;

import at.v3rtumnus.planman.entity.health.GoalStatus;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * What Opus returns for one weekly review (structured output). Fields of the goal check are
 * empty / 0 in weeks without a check; "no change" is 0 or "" because the schema has no nulls.
 */
public record ReviewDraft(
        @JsonPropertyDescription("Eine Zeile, höchstens 90 Zeichen, die das Wichtigste der Woche sagt (für die Morgen-Mail)")
        String headline,
        @JsonPropertyDescription("Rückmeldung zur Woche: 3 bis 6 Sätze, Du-Form, konkret mit Zahlen, ein Tipp für die nächste Woche")
        String feedback,
        @JsonPropertyDescription("Nur im Ziel-Check: Einschätzung des Ziels. Ohne Ziel-Check ON_TRACK")
        GoalStatus goalStatus,
        @JsonPropertyDescription("Nur im Ziel-Check: 1 bis 3 Sätze Begründung mit Zahlen, sonst leer")
        String goalReason,
        @JsonPropertyDescription("Nur im Ziel-Check und nur wenn nötig: neues Kalorienziel pro Tag, sonst 0")
        int proposedKcalTarget,
        @JsonPropertyDescription("Nur im Ziel-Check und nur wenn nötig: neues Proteinziel in g pro Tag, sonst 0")
        int proposedProteinTargetG,
        @JsonPropertyDescription("Nur im Ziel-Check und nur wenn nötig: neues Zieldatum als YYYY-MM-DD, sonst leer")
        String proposedTargetDate,
        @JsonPropertyDescription("Begründung des Vorschlags in 1 bis 2 Sätzen, leer ohne Vorschlag")
        String proposalReason) {
}
