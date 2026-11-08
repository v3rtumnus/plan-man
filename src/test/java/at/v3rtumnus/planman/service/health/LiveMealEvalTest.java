package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.entity.health.MealSlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A small eval over typical meals (calls the real API, ~56 requests, well under $0.50).
 * Ranges are plausible totals from common nutrition tables, not exact truths; the point is
 * to spot gross misses and run-to-run swings. Runs only with ANTHROPIC_EVAL=true.
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_EVAL", matches = "true")
class LiveMealEvalTest {

    record Case(String kind, MealSlot slot, String text, int min, int max) {
    }

    static final List<Case> CASES = List.of(
            new Case("Restaurant", MealSlot.LUNCH, "Acht Schätze vom Chinarestaurant mit Reis", 850, 1300),
            new Case("Restaurant", MealSlot.DINNER, "Pizza Margherita vom Italiener", 800, 1150),
            new Case("Gasthaus", MealSlot.LUNCH, "Wiener Schnitzel vom Schwein mit Pommes im Gasthaus", 1000, 1500),
            new Case("Imbiss", MealSlot.LUNCH, "Döner Kebab im Brot", 600, 950),
            new Case("Fast Food", MealSlot.DINNER, "Big Mac Menü mit mittleren Pommes und Cola", 950, 1250),
            new Case("Imbiss", MealSlot.SNACK, "Leberkässemmel", 450, 700),
            new Case("Kantine", MealSlot.LUNCH, "Kantine: Spaghetti Bolognese", 650, 1000),
            new Case("Selbst, Mengen", MealSlot.DINNER,
                    "Selbst gekocht: 200 g Hühnerbrust in der Pfanne gebraten, 150 g gekochter Reis, Brokkoli", 550, 780),
            new Case("Selbst, ohne Mengen", MealSlot.DINNER,
                    "Linsencurry mit Kokosmilch, selbst gekocht, ein großer Teller, dazu ein Naan", 700, 1100),
            new Case("Frühstück, Mengen", MealSlot.BREAKFAST, "60 g Haferflocken mit 250 ml Milch und einer Banane", 430, 560),
            new Case("Frühstück", MealSlot.BREAKFAST, "2 Semmeln mit Butter und Marmelade", 450, 650),
            new Case("Snack, Mengen", MealSlot.SNACK, "450 g Skyr natur mit einer Handvoll Heidelbeeren", 290, 370),
            new Case("Snack", MealSlot.SNACK, "Ein Apfel", 60, 110),
            new Case("Getränk", MealSlot.DINNER, "Ein großes Bier (0,5 l) und eine kleine Packung Erdnüsse", 400, 650));

    @Test
    void eval() {
        AnthropicMealRecognizer recognizer = new AnthropicMealRecognizer(System.getenv("ANTHROPIC_API_KEY"), "", new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        List<String> rows = new ArrayList<>();
        for (String model : new String[]{"claude-haiku-5-5", "claude-sonnet-5-5"}) {
            int hits = 0;
            long latency = 0;
            int runs = 0;
            for (Case c : CASES) {
                StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%-18s %-20s %-55s %4d–%-4d",
                        model.replace("claude-", ""), c.kind(), c.text(), c.min(), c.max()));
                for (int run = 0; run < 2; run++) {
                    try {
                        MealRecognizer.Result r = recognizer.recognize(model, c.slot(), c.text(), null);
                        boolean ok = r.kcal() >= c.min() && r.kcal() <= c.max();
                        hits += ok ? 1 : 0;
                        latency += r.latencyMs();
                        runs++;
                        line.append(String.format(Locale.ROOT, " | %5d %s %4.1fs q=%s", r.kcal(), ok ? "ok" : "--",
                                r.latencyMs() / 1000.0, r.draft().question() != null && r.draft().question().asked() ? "y" : "n"));
                    } catch (RuntimeException e) {
                        line.append(" | ERROR ").append(e.getMessage());
                    }
                }
                rows.add(line.toString());
            }
            rows.add(String.format(Locale.ROOT, "%s: %d/%d in range, avg %.1f s", model, hits, CASES.size() * 2,
                    runs == 0 ? 0 : latency / 1000.0 / runs));
        }
        rows.forEach(row -> System.out.println("EVAL>> " + row));
    }
}
