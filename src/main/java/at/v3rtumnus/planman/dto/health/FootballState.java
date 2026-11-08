package at.v3rtumnus.planman.dto.health;

public enum FootballState {
    /** Not the football weekday and nothing logged: no card. */
    NOT_ASKED,
    /** Football weekday, not answered yet. */
    OPEN,
    PLAYED,
    CANCELLED
}
