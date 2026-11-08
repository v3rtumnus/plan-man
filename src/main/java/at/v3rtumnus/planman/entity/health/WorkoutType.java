package at.v3rtumnus.planman.entity.health;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Training types with MET values per intensity (health.md § 4, MET table). */
@Getter
@RequiredArgsConstructor
public enum WorkoutType {
    FREELETICS("Freeletics", 5, 7, 9),
    FOOTBALL("Fußball", 6, 8, 10),
    WALK("Spaziergang", 3.5, 3.5, 3.5),
    CYCLING("Radfahren", 6, 6, 6),
    /** MET from the model (one sentence) or typed in. */
    OTHER("Sonstiges", 0, 0, 0);

    private final String label;
    private final double metLight;
    private final double metMedium;
    private final double metHard;

    public double met(Intensity intensity) {
        return switch (intensity == null ? Intensity.MEDIUM : intensity) {
            case LIGHT -> metLight;
            case MEDIUM -> metMedium;
            case HARD -> metHard;
        };
    }
}
