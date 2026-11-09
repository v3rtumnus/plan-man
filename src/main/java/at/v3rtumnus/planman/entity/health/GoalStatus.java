package at.v3rtumnus.planman.entity.health;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Result of the 4-weekly goal check. */
@Getter
@RequiredArgsConstructor
public enum GoalStatus {
    ON_TRACK("Im Plan", false),
    /** Weight flat, waist down: muscle gain, keep going. */
    RECOMPOSITION("Gewicht stabil, Taille sinkt: weiter so", false),
    TOO_FAST("Zu schnell", true),
    STALLED("Stagniert", true);

    private final String label;
    private final boolean needsTalk;

    public boolean needsTalk() {
        return needsTalk;
    }
}
