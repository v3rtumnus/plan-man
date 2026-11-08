package at.v3rtumnus.planman.dto.health;

/** "Diese Woche: Freeletics 1 von 2 · Fußball offen" (Monday to Sunday). */
public record TrainingWeek(int freeletics, int freeleticsTarget, FootballState football) {

    public String footballLabel() {
        return switch (football) {
            case PLAYED -> "Fußball gespielt";
            case CANCELLED -> "Fußball fällt aus";
            default -> "Fußball offen";
        };
    }
}
