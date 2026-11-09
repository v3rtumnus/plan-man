package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dto.health.DaySummary;
import at.v3rtumnus.planman.dto.health.WeekSummary;
import at.v3rtumnus.planman.dto.health.ai.ReviewDraft;
import at.v3rtumnus.planman.entity.health.HealthGoal;
import at.v3rtumnus.planman.entity.health.Workout;
import at.v3rtumnus.planman.entity.health.WorkoutType;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Weekly feedback and the 4-weekly goal check with Opus (structured output, effort medium). */
@Service
public class AnthropicWeeklyReviewer {

    static final String SYSTEM_PROMPT = """
            Du schreibst die wöchentliche Rückmeldung für ein persönliches Ernährungs- und
            Trainingstagebuch. Der Nutzer: Mann, 39 Jahre, 172 cm, Bürojob. Ziel: Fett verlieren und
            Kraft aufbauen. Training: Freeletics Coach (Ziel 2× pro Woche) und meist dienstags Fußball.

            So sind die Zahlen gemeint:
            - Das Kalorienziel ist der Grundwert des Ziels; eingetragenes Training erhöht das Tagesziel
              (Fußball pauschal, Freeletics mit den Netto-kcal der Einheit). Freeletics ist im Grundwert
              nicht enthalten, Fußball zählt nur, wenn eingetragen.
            - Durchschnitte zählen nur Tage mit eingetragenen Mahlzeiten und ohne Pause. Fehlende Tage
              heißen nur "nicht eingetragen", nicht "nichts gegessen".
            - Gewicht nur als 7-Tage-Trend beurteilen, nie einzelne Tage. Die Rate (kg/Woche) ist die
              Steigung über 3 Wochen; negativ heißt abnehmen.
            - Taille wird alle 2 Wochen gemessen, Liegestütze alle 4 Wochen.

            Rückmeldung (headline + feedback):
            - Freundlich und direkt, Du-Form, keine Floskeln, kein Moralisieren, kein Lob für
              Selbstverständliches. Beziehe dich auf konkrete Zahlen der Woche.
            - Nenne, was gut lief, was nicht, und genau einen konkreten Tipp für die nächste Woche
              (z. B. Protein beim Frühstück, Freeletics-Termin fix einplanen).
            - Bei wenigen eingetragenen Tagen: sag es kurz, ohne Vorwurf, und urteile vorsichtig.

            Ziel-Check (nur wenn in der Eingabe "Ziel-Check: ja" steht, sonst goalStatus ON_TRACK,
            goalReason leer, kein Vorschlag):
            - TOO_FAST: die Rate ist schneller als −0,8 kg/Woche oder mehr als doppelt so schnell wie
              die nötige Rate (Muskelverlust droht).
            - STALLED: der Trend ist seit mindestens 3 Wochen flach (zwischen −0,1 und +0,1 kg/Woche
              oder steigend), obwohl abgenommen werden soll, und die Taille sinkt nicht.
            - RECOMPOSITION: Gewicht flach, aber die Taille ist seit Zielbeginn um mindestens 1 cm
              gesunken: weitermachen, nichts ändern.
            - ON_TRACK: alles andere.
            - Einen Vorschlag nur bei TOO_FAST oder STALLED: Kalorienziel um höchstens 200 kcal ändern
              und nie unter 1 700 kcal, oder das Zieldatum verschieben. Protein nur bei klarem Grund.
              Wenn die Ursache eher fehlende Einträge oder Ausreißer sind, keinen Vorschlag, sondern
              das in goalReason sagen.
            - Du schlägst nur vor; der Nutzer entscheidet und kann es in einem Gespräch klären.
            """;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);

    private final AnthropicGateway gateway;
    private final String model;

    public AnthropicWeeklyReviewer(AnthropicGateway gateway,
                                   @Value("${planman.health.ai.review-model:claude-opus-5-5}") String model) {
        this.gateway = gateway;
        this.model = model;
    }

    public boolean available() {
        return gateway.available();
    }

    public String model() {
        return model;
    }

    /**
     * @param earlierWeeks the weeks before, newest first (at most 11)
     * @param goalCheck    also judge the goal itself
     */
    public ReviewDraft review(HealthGoal goal, WeekSummary week, List<WeekSummary> earlierWeeks,
                              List<String> waist, List<String> pushUps, boolean goalCheck) {
        String input = input(goal, week, earlierWeeks, waist, pushUps, goalCheck);
        List<ContentBlockParam> content = List.of(ContentBlockParam.ofText(TextBlockParam.builder().text(input).build()));
        return gateway.structured(model, "review", SYSTEM_PROMPT, content, ReviewDraft.class,
                OutputConfig.Effort.MEDIUM, 16000L, Duration.ofMinutes(3)).value();
    }

    static String input(HealthGoal goal, WeekSummary week, List<WeekSummary> earlierWeeks,
                        List<String> waist, List<String> pushUps, boolean goalCheck) {
        StringBuilder text = new StringBuilder();
        text.append("Ziel: ").append(goal.getTitle()).append(", seit ").append(DATE.format(goal.getValidFrom()))
                .append(". Start ").append(kg(goal.getStartWeightKg())).append(" → Ziel ").append(kg(goal.getTargetWeightKg()))
                .append(" bis ").append(DATE.format(goal.getTargetDate())).append(".\n");
        text.append("Tagesziel ").append(goal.getKcalTarget()).append(" kcal, Fußball +").append(goal.getFootballBonusKcal())
                .append(" kcal, Protein ").append(goal.getProteinTargetG()).append(" g");
        if (goal.getPushUpsTarget() != null) {
            text.append(", Liegestütze-Ziel ").append(goal.getPushUpsTarget());
        }
        text.append(".\n");
        BigDecimal required = requiredRate(goal, week);
        if (required != null) {
            text.append("Nötige Rate ab jetzt: ").append(rate(required)).append(" kg/Woche.\n");
        }
        text.append("Ziel-Check: ").append(goalCheck ? "ja" : "nein").append("\n\n");

        text.append("Woche ").append(DAY.format(week.weekStart())).append("–").append(DATE.format(week.weekEnd())).append(":\n");
        for (DaySummary day : week.days()) {
            text.append("- ").append(dayLine(day)).append("\n");
        }
        text.append("Zusammen: ").append(weekLine(week)).append("\n\n");

        if (!earlierWeeks.isEmpty()) {
            text.append("Vorwochen (neueste zuerst):\n");
            for (WeekSummary earlier : earlierWeeks) {
                text.append("- ").append(DAY.format(earlier.weekStart())).append("–").append(DAY.format(earlier.weekEnd()))
                        .append(": ").append(weekLine(earlier)).append("\n");
            }
            text.append("\n");
        }
        text.append("Taille (cm): ").append(waist.isEmpty() ? "noch nicht gemessen" : String.join(", ", waist)).append("\n");
        text.append("Liegestütze am Stück: ").append(pushUps.isEmpty() ? "noch nicht getestet" : String.join(", ", pushUps)).append("\n");
        return text.toString();
    }

    /** kg per week still needed from the week's trend to reach the target on time; null if unknown or overdue. */
    static BigDecimal requiredRate(HealthGoal goal, WeekSummary week) {
        if (week.weightTrendKg() == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(week.weekEnd(), goal.getTargetDate());
        if (days <= 0) {
            return null;
        }
        return goal.getTargetWeightKg().subtract(week.weightTrendKg())
                .multiply(BigDecimal.valueOf(7))
                .divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    static String dayLine(DaySummary day) {
        StringBuilder line = new StringBuilder(day.dayLabel()).append(" ").append(DAY.format(day.date())).append(": ");
        if (day.paused()) {
            line.append("Pause");
        } else if (day.meals() == 0) {
            line.append("nichts eingetragen");
        } else {
            line.append(day.kcalEaten()).append(day.kcalTarget() != null ? " / " + day.kcalTarget() : "").append(" kcal, ")
                    .append(day.proteinG().setScale(0, RoundingMode.HALF_UP)).append(" g Protein");
        }
        if (day.weightKg() != null) {
            line.append(", Gewicht ").append(kg(day.weightKg()));
        }
        if (!day.workouts().isEmpty()) {
            line.append(", Training: ").append(day.workouts().stream().map(AnthropicWeeklyReviewer::workout)
                    .collect(Collectors.joining("; ")));
        }
        return line.toString();
    }

    private static String workout(Workout w) {
        String text = w.label() + " " + w.getDurationMin() + " min";
        if (w.getType() == WorkoutType.FREELETICS && w.getIntensity() != null) {
            text += " " + w.getIntensity().getLabel();
        }
        return text + " (+" + w.getBonusKcal() + " kcal)";
    }

    static String weekLine(WeekSummary w) {
        StringBuilder line = new StringBuilder();
        line.append(w.loggedDays()).append(" Tage eingetragen");
        if (w.pausedDays() > 0) {
            line.append(", ").append(w.pausedDays()).append(" Tage Pause");
        }
        if (w.kcalAvg() != null) {
            line.append(", Ø ").append(w.kcalAvg()).append(w.kcalTargetAvg() != null ? " / " + w.kcalTargetAvg() : "").append(" kcal");
        }
        if (w.proteinAvgG() != null) {
            line.append(", Ø ").append(w.proteinAvgG().setScale(0, RoundingMode.HALF_UP)).append(" g Protein (Ziel an ")
                    .append(w.proteinDays()).append(" Tagen erreicht)");
        }
        line.append(", Freeletics ").append(w.freeleticsSessions()).append("×, Fußball ").append(w.footballPlayed() ? "ja" : "nein");
        if (w.weightTrendKg() != null) {
            line.append(", Trend ").append(kg(w.weightTrendKg()));
            if (w.weightChangeKg() != null) {
                line.append(" (").append(rate(w.weightChangeKg())).append(" zur Vorwoche)");
            }
        }
        if (w.weeklyRateKg() != null) {
            line.append(", Rate ").append(rate(w.weeklyRateKg())).append(" kg/Woche");
        }
        return line.toString();
    }

    static String measurement(LocalDate date, BigDecimal value) {
        return DAY.format(date) + " " + value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String kg(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " kg";
    }

    private static String rate(BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
