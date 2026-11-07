package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dto.health.WeightPoint;
import at.v3rtumnus.planman.dto.health.WeightTrend;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeightTrendCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private static WeightPoint point(int daysAgo, String kg) {
        return new WeightPoint(TODAY.minusDays(daysAgo), new BigDecimal(kg));
    }

    @Test
    void emptyInput_returnsEmptyTrend() {
        assertThat(WeightTrendCalculator.calculate(List.of(), TODAY)).isEqualTo(WeightTrend.EMPTY);
    }

    @Test
    void trend_isMeanOfLastSevenDays() {
        WeightTrend trend = WeightTrendCalculator.calculate(List.of(
                point(10, "90.0"),   // outside the 7-day window
                point(6, "83.0"),
                point(3, "82.0"),
                point(0, "82.3")), TODAY);

        assertThat(trend.trendKg()).isEqualByComparingTo("82.4");
        assertThat(trend.latestKg()).isEqualByComparingTo("82.3");
        assertThat(trend.latestDate()).isEqualTo(TODAY);
    }

    @Test
    void trend_isNullWhenLastWeightIsOlderThanAWeek() {
        WeightTrend trend = WeightTrendCalculator.calculate(List.of(point(9, "83.0")), TODAY);

        assertThat(trend.trendKg()).isNull();
        assertThat(trend.latestKg()).isEqualByComparingTo("83.0");
    }

    @Test
    void weeklyRate_followsSteadyLoss() {
        List<WeightPoint> points = new ArrayList<>();
        for (int day = 20; day >= 0; day--) {
            // -0.4 kg per week, plus a daily up/down swing that should not change the slope much
            double kg = 83.0 - (20 - day) * 0.4 / 7 + (day % 2 == 0 ? 0.3 : -0.3);
            points.add(new WeightPoint(TODAY.minusDays(day), BigDecimal.valueOf(kg)));
        }

        WeightTrend trend = WeightTrendCalculator.calculate(points, TODAY);

        assertThat(trend.weeklyRateKg().doubleValue()).isBetween(-0.5, -0.3);
    }

    @Test
    void weeklyRate_needsFourPointsOverAtLeastAWeek() {
        WeightTrend fewPoints = WeightTrendCalculator.calculate(List.of(
                point(10, "83.0"), point(5, "82.6"), point(0, "82.4")), TODAY);
        WeightTrend shortSpan = WeightTrendCalculator.calculate(List.of(
                point(5, "83.0"), point(4, "82.8"), point(2, "82.6"), point(0, "82.4")), TODAY);

        assertThat(fewPoints.weeklyRateKg()).isNull();
        assertThat(shortSpan.weeklyRateKg()).isNull();
    }

    @Test
    void futurePoints_areIgnored() {
        WeightTrend trend = WeightTrendCalculator.calculate(List.of(
                point(1, "82.0"), new WeightPoint(TODAY.plusDays(1), new BigDecimal("70.0"))), TODAY);

        assertThat(trend.trendKg()).isEqualByComparingTo("82.0");
        assertThat(trend.latestDate()).isEqualTo(TODAY.minusDays(1));
    }
}
