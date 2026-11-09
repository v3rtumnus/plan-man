package at.v3rtumnus.planman.dto.health;

import java.math.BigDecimal;
import java.util.List;

/** Series for the charts on Ziel &amp; Verlauf; dates as ISO strings for the page's JavaScript. */
public record HealthChart(List<Point> weight, List<Point> trend, List<Point> waist, List<Point> pushUps,
                          BigDecimal targetKg, Integer pushUpsTarget, String goalStart, String today) {

    public record Point(String date, BigDecimal value) {
    }
}
