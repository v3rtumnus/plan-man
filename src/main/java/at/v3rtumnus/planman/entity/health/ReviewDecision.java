package at.v3rtumnus.planman.entity.health;

/** What you did with a goal check. */
public enum ReviewDecision {
    /** "Gespräch führen": a Claude Code session is worth it. */
    TALK,
    /** "Passt so": no change. */
    KEEP,
    /** The proposed targets became a new goal. */
    ACCEPTED
}
