package at.v3rtumnus.planman.entity.health;

/** How a meal got into the log. */
public enum MealSource {
    /** Recognised by the model from a sentence and/or photo, then reviewed. */
    AI,
    /** One tap on a saved meal ("Schnell wieder"). */
    SAVED,
    /** One tap on "wie gestern": a copy of an earlier meal. */
    REPEAT,
    /** Entered by hand on the review form. */
    MANUAL,
    /** Scanned barcode (Open Food Facts or your saved correction), then reviewed (H7). */
    BARCODE
}
