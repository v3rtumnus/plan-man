package at.v3rtumnus.planman.entity.health;

/** Where the Opus text of a weekly review stands. */
public enum FeedbackStatus {
    /** Not asked yet, or the last try failed: the daily job tries again during the next week. */
    PENDING,
    DONE,
    /** Paused for most of the week or today: no feedback, as promised by the Pause switch. */
    PAUSED,
    /** Fewer than 3 logged days: nothing to judge. */
    TOO_FEW_DAYS,
    /** No API key or the week is over a week old: numbers only. */
    SKIPPED
}
