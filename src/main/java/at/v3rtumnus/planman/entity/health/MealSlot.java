package at.v3rtumnus.planman.entity.health;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalTime;

@Getter
@RequiredArgsConstructor
public enum MealSlot {
    BREAKFAST("Frühstück"),
    SNACK("Jause"),
    LUNCH("Mittag"),
    DINNER("Abend");

    private final String label;

    /** The slot a meal logged at this time most likely belongs to. */
    public static MealSlot at(LocalTime time) {
        if (time.isBefore(LocalTime.of(10, 30))) {
            return BREAKFAST;
        }
        if (time.isBefore(LocalTime.of(11, 30))) {
            return SNACK;
        }
        if (time.isBefore(LocalTime.of(14, 30))) {
            return LUNCH;
        }
        if (time.isBefore(LocalTime.of(17, 30))) {
            return SNACK;
        }
        return DINNER;
    }
}
