package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.*;
import at.v3rtumnus.planman.dto.health.*;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.health.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/** Goals, day snapshots, pauses and body measurements of the logged-in user. */
@Service
@RequiredArgsConstructor
public class HealthService {

    private final HealthGoalRepository goalRepository;
    private final HealthDayRepository dayRepository;
    private final HealthPauseRepository pauseRepository;
    private final BodyMeasurementRepository measurementRepository;
    private final UserProfileRepository userProfileRepository;
    private final Clock clock;

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // --- goals ---

    public Optional<HealthGoal> currentGoal() {
        return goalRepository.findGoalFor(username(), today());
    }

    public List<HealthGoal> goalHistory() {
        return goalRepository.findByUserUsernameOrderByValidFromDescIdDesc(username());
    }

    /** Closes the open goal and starts a new one today; the old row stays as history. */
    @Transactional
    public HealthGoal changeGoal(GoalForm form) {
        LocalDate today = today();
        if (!form.getTargetDate().isAfter(today)) {
            throw new IllegalArgumentException("Das Zieldatum muss in der Zukunft liegen");
        }
        UserProfile user = currentUser();
        goalRepository.findFirstByUserUsernameAndValidToIsNullOrderByIdDesc(user.getUsername())
                .ifPresent(open -> {
                    open.setValidTo(today);
                    goalRepository.save(open);
                });

        HealthGoal goal = new HealthGoal();
        goal.setUser(user);
        goal.setTitle(form.getTitle().trim());
        goal.setValidFrom(today);
        goal.setKcalTarget(form.getKcalTarget());
        goal.setProteinTargetG(form.getProteinTargetG());
        goal.setFootballBonusKcal(form.getFootballBonusKcal());
        goal.setStepsTarget(form.getStepsTarget());
        goal.setStartWeightKg(form.getStartWeightKg().setScale(1, RoundingMode.HALF_UP));
        goal.setTargetWeightKg(form.getTargetWeightKg().setScale(1, RoundingMode.HALF_UP));
        goal.setTargetDate(form.getTargetDate());
        goal.setPushUpsTarget(form.getPushUpsTarget());
        goal.setCreatedAt(LocalDateTime.now(clock));
        HealthGoal saved = goalRepository.save(goal);

        refreshDay(user, today);
        return saved;
    }

    /** Form for a goal change: the current goal's values, or defaults with the latest weight as start. */
    public GoalForm goalFormForChange() {
        Optional<HealthGoal> goal = currentGoal();
        if (goal.isPresent()) {
            return GoalForm.from(goal.get());
        }
        GoalForm form = new GoalForm();
        WeightTrend trend = weightTrend();
        form.setStartWeightKg(trend.trendKg() != null ? trend.trendKg() : trend.latestKg());
        return form;
    }

    public Optional<GoalProgress> goalProgress(HealthGoal goal, WeightTrend trend) {
        BigDecimal current = trend.trendKg() != null ? trend.trendKg() : trend.latestKg();
        if (current == null) {
            return Optional.empty();
        }
        BigDecimal totalChange = goal.getStartWeightKg().subtract(goal.getTargetWeightKg());
        int percent = 0;
        if (totalChange.signum() != 0) {
            BigDecimal done = goal.getStartWeightKg().subtract(current);
            percent = done.multiply(BigDecimal.valueOf(100))
                    .divide(totalChange, 0, RoundingMode.HALF_UP)
                    .max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).intValue();
        }
        long days = ChronoUnit.DAYS.between(goal.getValidFrom(), goal.getTargetDate());
        BigDecimal required = days <= 0 ? null
                : goal.getTargetWeightKg().subtract(goal.getStartWeightKg())
                .multiply(BigDecimal.valueOf(7))
                .divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        return Optional.of(new GoalProgress(current, percent, required, trend.weeklyRateKg()));
    }

    // --- days ---

    /** The day's row, created with the targets of the goal in force on that day if missing. */
    @Transactional
    public HealthDay day(LocalDate date) {
        UserProfile user = currentUser();
        return dayRepository.findByUserUsernameAndDate(user.getUsername(), date)
                .map(day -> day.getGoal() == null ? refreshDay(user, date) : day)
                .orElseGet(() -> refreshDay(user, date));
    }

    /** Same as {@link #day(LocalDate)} for a job without a login (phone data, H8). */
    @Transactional
    public HealthDay day(String username, LocalDate date) {
        UserProfile user = userProfileRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user " + username));
        return dayRepository.findByUserUsernameAndDate(username, date)
                .map(day -> day.getGoal() == null ? refreshDay(user, date) : day)
                .orElseGet(() -> refreshDay(user, date));
    }

    private HealthDay refreshDay(UserProfile user, LocalDate date) {
        HealthDay day = dayRepository.findByUserUsernameAndDate(user.getUsername(), date).orElseGet(() -> {
            HealthDay created = new HealthDay();
            created.setUser(user);
            created.setDate(date);
            return created;
        });
        Optional<HealthGoal> goal = goalRepository.findGoalFor(user.getUsername(), date);
        day.setGoal(goal.orElse(null));
        day.setKcalTarget(goal.map(HealthGoal::getKcalTarget).orElse(null));
        day.setProteinTargetG(goal.map(HealthGoal::getProteinTargetG).orElse(null));
        day.setStepsTarget(goal.map(HealthGoal::getStepsTarget).orElse(null));
        day.setPaused(pauseRepository.findActive(user.getUsername(), date).isPresent());
        return dayRepository.save(day);
    }

    // --- pause ---

    public Optional<HealthPause> activePause() {
        return pauseRepository.findActive(username(), today());
    }

    @Transactional
    public void startPause(String reason) {
        if (activePause().isPresent()) {
            return;
        }
        UserProfile user = currentUser();
        HealthPause pause = new HealthPause();
        pause.setUser(user);
        pause.setStartDate(today());
        pause.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        pauseRepository.save(pause);
        refreshDay(user, today());
    }

    /** Ends the pause with yesterday as its last day; a pause started today is removed. */
    @Transactional
    public void endPause() {
        UserProfile user = currentUser();
        LocalDate today = today();
        activePause().ifPresent(pause -> {
            if (pause.getStartDate().isBefore(today)) {
                pause.setEndDate(today.minusDays(1));
                pauseRepository.save(pause);
            } else {
                pauseRepository.delete(pause);
            }
        });
        refreshDay(user, today);
    }

    // --- measurements ---

    @Transactional
    public BodyMeasurement saveMeasurement(MeasurementType type, LocalDate date, BigDecimal value) {
        if (date.isAfter(today())) {
            throw new IllegalArgumentException("Das Datum darf nicht in der Zukunft liegen");
        }
        if (!type.accepts(value)) {
            throw new IllegalArgumentException(type.getLabel() + ": Wert zwischen " + type.getMin()
                    + " und " + type.getMax() + " erwartet");
        }
        UserProfile user = currentUser();
        BodyMeasurement measurement = measurementRepository
                .findByUserUsernameAndTypeAndMeasuredOn(user.getUsername(), type, date)
                .orElseGet(BodyMeasurement::new);
        measurement.setUser(user);
        measurement.setType(type);
        measurement.setMeasuredOn(date);
        measurement.setMeasuredValue(value.setScale(1, RoundingMode.HALF_UP));
        measurement.setSource(MeasurementSource.MANUAL);
        measurement.setCreatedAt(LocalDateTime.now(clock));
        BodyMeasurement saved = measurementRepository.save(measurement);

        if (dayRepository.findByUserUsernameAndDate(user.getUsername(), date).isEmpty()) {
            refreshDay(user, date);
        }
        return saved;
    }

    public Optional<BodyMeasurement> latest(MeasurementType type) {
        return measurementRepository.findFirstByUserUsernameAndTypeOrderByMeasuredOnDesc(username(), type);
    }

    public Optional<BodyMeasurement> measurementOn(MeasurementType type, LocalDate date) {
        return measurementRepository.findByUserUsernameAndTypeAndMeasuredOn(username(), type, date);
    }

    public WeightTrend weightTrend() {
        LocalDate today = today();
        List<WeightPoint> points = measurementRepository
                .findByUserUsernameAndTypeAndMeasuredOnBetweenOrderByMeasuredOnAsc(username(), MeasurementType.WEIGHT,
                        today.minusDays(WeightTrendCalculator.RATE_WINDOW_DAYS), today)
                .stream()
                .map(m -> new WeightPoint(m.getMeasuredOn(), m.getMeasuredValue()))
                .toList();
        if (points.isEmpty()) {
            return latest(MeasurementType.WEIGHT)
                    .map(m -> new WeightTrend(null, null, m.getMeasuredValue(), m.getMeasuredOn()))
                    .orElse(WeightTrend.EMPTY);
        }
        return WeightTrendCalculator.calculate(points, today);
    }

    public MeasurementStatus status(MeasurementType type, Optional<HealthGoal> goal) {
        List<BodyMeasurement> all = measurementRepository.findByUserUsernameAndTypeOrderByMeasuredOnAsc(username(), type);
        LocalDate since = goal.map(HealthGoal::getValidFrom).orElse(LocalDate.MIN);
        BigDecimal first = all.stream()
                .filter(m -> !m.getMeasuredOn().isBefore(since))
                .findFirst()
                .map(BodyMeasurement::getMeasuredValue)
                .orElse(null);
        Optional<BodyMeasurement> latest = all.isEmpty() ? Optional.empty() : Optional.of(all.getLast());
        boolean due = latest
                .map(m -> ChronoUnit.DAYS.between(m.getMeasuredOn(), today()) >= type.getIntervalDays())
                .orElse(true);
        Integer target = type == MeasurementType.PUSH_UPS ? goal.map(HealthGoal::getPushUpsTarget).orElse(null) : null;
        return new MeasurementStatus(type, first,
                latest.map(BodyMeasurement::getMeasuredValue).orElse(null),
                latest.map(BodyMeasurement::getMeasuredOn).orElse(null),
                due, target);
    }

    private String username() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private UserProfile currentUser() {
        return userProfileRepository.findByUsername(username())
                .orElseThrow(() -> new IllegalStateException("Unknown user " + username()));
    }
}
