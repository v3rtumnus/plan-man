package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dto.health.WeightPoint;
import at.v3rtumnus.planman.dto.health.WeightTrend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Smooths daily weight swings: the trend is the mean of the last 7 days, the weekly rate is
 * the least-squares slope over the last 3 weeks. Single days never count on their own.
 */
public final class WeightTrendCalculator {

    static final int TREND_WINDOW_DAYS = 7;
    static final int RATE_WINDOW_DAYS = 21;
    static final int RATE_MIN_POINTS = 4;
    static final int RATE_MIN_SPAN_DAYS = 7;

    private WeightTrendCalculator() {
    }

    public static WeightTrend calculate(List<WeightPoint> points, LocalDate today) {
        List<WeightPoint> sorted = points.stream()
                .filter(p -> !p.date().isAfter(today))
                .sorted(Comparator.comparing(WeightPoint::date))
                .toList();
        if (sorted.isEmpty()) {
            return WeightTrend.EMPTY;
        }
        WeightPoint latest = sorted.getLast();
        return new WeightTrend(trend(sorted, today), weeklyRate(sorted, today), latest.kg(), latest.date());
    }

    static BigDecimal trend(List<WeightPoint> sorted, LocalDate day) {
        LocalDate from = day.minusDays(TREND_WINDOW_DAYS - 1L);
        List<BigDecimal> window = sorted.stream()
                .filter(p -> !p.date().isBefore(from) && !p.date().isAfter(day))
                .map(WeightPoint::kg)
                .toList();
        if (window.isEmpty()) {
            return null;
        }
        BigDecimal sum = window.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(window.size()), 1, RoundingMode.HALF_UP);
    }

    static BigDecimal weeklyRate(List<WeightPoint> sorted, LocalDate today) {
        LocalDate from = today.minusDays(RATE_WINDOW_DAYS - 1L);
        List<WeightPoint> window = sorted.stream().filter(p -> !p.date().isBefore(from)).toList();
        if (window.size() < RATE_MIN_POINTS
                || ChronoUnit.DAYS.between(window.getFirst().date(), window.getLast().date()) < RATE_MIN_SPAN_DAYS) {
            return null;
        }
        double n = window.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (WeightPoint p : window) {
            double x = ChronoUnit.DAYS.between(from, p.date());
            double y = p.kg().doubleValue();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double slopePerDay = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
        return BigDecimal.valueOf(slopePerDay * 7).setScale(2, RoundingMode.HALF_UP);
    }
}
