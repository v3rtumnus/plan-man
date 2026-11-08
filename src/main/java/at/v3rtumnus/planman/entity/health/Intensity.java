package at.v3rtumnus.planman.entity.health;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Intensity {
    LIGHT("leicht"),
    MEDIUM("mittel"),
    HARD("hart");

    private final String label;
}
