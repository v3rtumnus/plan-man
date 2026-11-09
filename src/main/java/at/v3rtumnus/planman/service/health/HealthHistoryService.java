package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.*;
import at.v3rtumnus.planman.dto.health.*;
import at.v3rtumnus.planman.entity.health.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only views over days and weeks for an explicit user: the week screen, the weekly review
 * job and the digest run without a login, so nothing here reads the security context.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthHistoryService {

    private final HealthGoalRepository goalRepository;
    private final HealthDayRepository dayRepository;
    private final HealthPauseRepository pauseRepository;
    private final MealRepository mealRepository;
    private final WorkoutRepository workoutRepository;
    private final BodyMeasurementRepository measurementRepository;
    private final Clock clock;

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    // --- days ---

    public List<DaySummary> days(String username, LocalDate from, LocalDate to) {
        List<HealthGoal> goals = goalRepository.findByUserUsernameOrderByValidFromDescIdDesc(username);
        Map<LocalDate, HealthDay> snapshots = dayRepository.findByUserUsernameAndDateBetween(username, from, to).stream()
                .collect(Collectors.toMap(HealthDay::getDate, d -> d, (a, b) -> a));
        List<HealthPause> pauses = pauseRepository.findOverlapping(username, from, to);
        Map<LocalDate, List<Meal>> meals = mealRepository.findByUserUsernameAndEatenOnBetween(username, from, to).stream()
                .collect(Collectors.groupingBy(Meal::getEatenOn));
        Map<LocalDate, List<Workout>> workouts = workoutRepository
                .findByUserUsernameAndDoneOnBetweenOrderByDoneOnAscCreatedAtAsc(username, from, to).stream()
                .collect(Collectors.groupingBy(Workout::getDoneOn));
        Map<LocalDate, BigDecimal> weights = measurementRepository
                .findByUserUsernameAndTypeAndMeasuredOnBetweenOrderByMeasuredOnAsc(username, MeasurementType.WEIGHT, from, to)
                .stream().collect(Collectors.toMap(BodyMeasurement::getMeasuredOn, BodyMeasurement::getMeasuredValue, (a, b) -> b));

        List<DaySummary> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            LocalDate d = date;
            HealthDay snapshot = snapshots.get(d);
            Optional<HealthGoal> goal = goalOn(goals, d);
            Integer kcalTarget = snapshot != null && snapshot.getKcalTarget() != null
                    ? snapshot.getKcalTarget() : goal.map(HealthGoal::getKcalTarget).orElse(null);
            Integer proteinTarget = snapshot != null && snapshot.getProteinTargetG() != null
                    ? snapshot.getProteinTargetG() : goal.map(HealthGoal::getProteinTargetG).orElse(null);
            List<Meal> dayMeals = meals.getOrDefault(d, List.of());
            List<Workout> dayWorkouts = workouts.getOrDefault(d, List.of());
            result.add(new DaySummary(d, kcalTarget,
                    dayWorkouts.stream().mapToInt(Workout::getBonusKcal).sum(),
                    proteinTarget,
                    dayMeals.stream().mapToInt(Meal::getKcal).sum(),
                    dayMeals.stream().map(Meal::getProteinG).reduce(BigDecimal.ZERO, BigDecimal::add),
                    dayMeals.size(),
                    weights.get(d),
                    pauses.stream().anyMatch(p -> p.covers(d)),
                    dayWorkouts));
        }
        return result;
    }

    static Optional<HealthGoal> goalOn(List<HealthGoal> goals, LocalDate date) {
        return goals.stream()
                .filter(g -> !g.getValidFrom().isAfter(date) && (g.getValidTo() == null || g.getValidTo().isAfter(date)))
                .findFirst();
    }

    // --- weeks ---

    public WeekSummary week(String username, LocalDate monday) {
        LocalDate sunday = monday.plusDays(6);
        List<DaySummary> days = days(username, monday, sunday);
        List<DaySummary> logged = days.stream().filter(DaySummary::logged).toList();
        int pausedDays = (int) days.stream().filter(DaySummary::paused).count();

        Integer kcalAvg = logged.isEmpty() ? null
                : (int) Math.round(logged.stream().mapToInt(DaySummary::kcalEaten).average().orElse(0));
        Integer kcalTargetAvg = logged.stream().anyMatch(d -> d.kcalTarget() != null)
                ? (int) Math.round(logged.stream().filter(d -> d.kcalTarget() != null)
                .mapToInt(DaySummary::kcalTarget).average().orElse(0))
                : null;
        BigDecimal proteinAvg = logged.isEmpty() ? null
                : logged.stream().map(DaySummary::proteinG).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(logged.size()), 1, RoundingMode.HALF_UP);
        Integer proteinTarget = days.getLast().proteinTargetG();
        int proteinDays = (int) logged.stream().filter(DaySummary::proteinReached).count();

        List<Workout> workouts = days.stream().flatMap(d -> d.workouts().stream()).toList();
        int freeletics = (int) workouts.stream().filter(w -> w.getType() == WorkoutType.FREELETICS).count();
        boolean football = workouts.stream().anyMatch(w -> w.getType() == WorkoutType.FOOTBALL);
        int trainingKcal = workouts.stream().mapToInt(Workout::getBonusKcal).sum();

        List<WeightPoint> weights = weightPoints(username, monday.minusDays(WeightTrendCalculator.RATE_WINDOW_DAYS), sunday);
        BigDecimal trendEnd = WeightTrendCalculator.trend(weights, sunday);
        BigDecimal trendBefore = WeightTrendCalculator.trend(weights, monday.minusDays(1));
        BigDecimal change = trendEnd != null && trendBefore != null ? trendEnd.subtract(trendBefore) : null;
        BigDecimal rate = WeightTrendCalculator.weeklyRate(weights, sunday);

        List<HealthGoal> goals = goalRepository.findByUserUsernameOrderByValidFromDescIdDesc(username);
        LocalDate goalStart = goalOn(goals, sunday).map(HealthGoal::getValidFrom).orElse(LocalDate.MIN);
        List<BodyMeasurement> waist = measurementRepository
                .findByUserUsernameAndTypeOrderByMeasuredOnAsc(username, MeasurementType.WAIST).stream()
                .filter(m -> !m.getMeasuredOn().isAfter(sunday))
                .toList();
        BigDecimal waistNow = waist.isEmpty() ? null : waist.getLast().getMeasuredValue();
        BigDecimal waistStart = waist.stream().filter(m -> !m.getMeasuredOn().isBefore(goalStart))
                .findFirst().map(BodyMeasurement::getMeasuredValue).orElse(null);
        BigDecimal waistChange = waistNow != null && waistStart != null ? waistNow.subtract(waistStart) : null;
        Integer pushUps = measurementRepository
                .findByUserUsernameAndTypeOrderByMeasuredOnAsc(username, MeasurementType.PUSH_UPS).stream()
                .filter(m -> !m.getMeasuredOn().isAfter(sunday))
                .reduce((a, b) -> b)
                .map(m -> m.getMeasuredValue().intValue())
                .orElse(null);

        return new WeekSummary(monday, days, logged.size(), pausedDays, kcalAvg, kcalTargetAvg, proteinAvg,
                proteinTarget, proteinDays, freeletics, football, trainingKcal, trendEnd, change, rate,
                waistNow, waistChange, pushUps);
    }

    private List<WeightPoint> weightPoints(String username, LocalDate from, LocalDate to) {
        return measurementRepository
                .findByUserUsernameAndTypeAndMeasuredOnBetweenOrderByMeasuredOnAsc(username, MeasurementType.WEIGHT, from, to)
                .stream()
                .map(m -> new WeightPoint(m.getMeasuredOn(), m.getMeasuredValue()))
                .toList();
    }

    // --- charts ---

    /** Every weight, waist and push-up value, plus the 7-day trend for each day from the first weight on. */
    public HealthChart chart(String username) {
        LocalDate today = today();
        List<WeightPoint> weights = measurementRepository
                .findByUserUsernameAndTypeOrderByMeasuredOnAsc(username, MeasurementType.WEIGHT).stream()
                .filter(m -> !m.getMeasuredOn().isAfter(today))
                .map(m -> new WeightPoint(m.getMeasuredOn(), m.getMeasuredValue()))
                .toList();
        List<HealthChart.Point> trend = new ArrayList<>();
        if (!weights.isEmpty()) {
            for (LocalDate d = weights.getFirst().date(); !d.isAfter(today); d = d.plusDays(1)) {
                BigDecimal value = WeightTrendCalculator.trend(weights, d);
                if (value != null) {
                    trend.add(new HealthChart.Point(d.toString(), value));
                }
            }
        }
        Optional<HealthGoal> goal = goalRepository.findGoalFor(username, today);
        return new HealthChart(
                weights.stream().map(p -> new HealthChart.Point(p.date().toString(), p.kg())).toList(),
                trend,
                points(username, MeasurementType.WAIST),
                points(username, MeasurementType.PUSH_UPS),
                goal.map(HealthGoal::getTargetWeightKg).orElse(null),
                goal.map(HealthGoal::getPushUpsTarget).orElse(null),
                goal.map(g -> g.getValidFrom().toString()).orElse(null),
                today.toString());
    }

    private List<HealthChart.Point> points(String username, MeasurementType type) {
        return measurementRepository.findByUserUsernameAndTypeOrderByMeasuredOnAsc(username, type).stream()
                .map(m -> new HealthChart.Point(m.getMeasuredOn().toString(), m.getMeasuredValue()))
                .toList();
    }

    // --- trend for one user ---

    public WeightTrend weightTrend(String username, LocalDate day) {
        List<WeightPoint> points = weightPoints(username, day.minusDays(WeightTrendCalculator.RATE_WINDOW_DAYS), day);
        return points.isEmpty() ? WeightTrend.EMPTY : WeightTrendCalculator.calculate(points, day);
    }
}
