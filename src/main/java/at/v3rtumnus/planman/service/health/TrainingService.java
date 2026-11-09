package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.*;
import at.v3rtumnus.planman.dto.health.FootballState;
import at.v3rtumnus.planman.dto.health.TrainingWeek;
import at.v3rtumnus.planman.dto.health.WeightTrend;
import at.v3rtumnus.planman.dto.health.ai.ActivityDraft;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.health.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** Workouts and the football question of the logged-in user. */
@Service
public class TrainingService {

    /** Used for the MET formula until a weight is entered. */
    static final BigDecimal DEFAULT_BODY_WEIGHT_KG = new BigDecimal("83");

    private final WorkoutRepository workoutRepository;
    private final HealthDayRepository dayRepository;
    private final UserProfileRepository userProfileRepository;
    private final HealthService healthService;
    private final ActivityEstimator activityEstimator;
    private final Clock clock;
    private final DayOfWeek footballDay;
    private final int footballMinutes;
    private final int freeleticsPerWeek;

    public TrainingService(WorkoutRepository workoutRepository, HealthDayRepository dayRepository, UserProfileRepository userProfileRepository,
                           HealthService healthService, ActivityEstimator activityEstimator, Clock clock,
                           @Value("${planman.health.football-day:TUESDAY}") DayOfWeek footballDay,
                           @Value("${planman.health.football-minutes:70}") int footballMinutes,
                           @Value("${planman.health.freeletics-per-week:2}") int freeleticsPerWeek) {
        this.workoutRepository = workoutRepository;
        this.dayRepository = dayRepository;
        this.userProfileRepository = userProfileRepository;
        this.healthService = healthService;
        this.activityEstimator = activityEstimator;
        this.clock = clock;
        this.footballDay = footballDay;
        this.footballMinutes = footballMinutes;
        this.freeleticsPerWeek = freeleticsPerWeek;
    }

    public LocalDate today() {
        return healthService.today();
    }

    public int footballMinutes() {
        return footballMinutes;
    }

    public boolean aiAvailable() {
        return activityEstimator.available();
    }

    // --- kcal ---

    /** Burn above what you burn anyway (~1 MET), rounded to 10 kcal: what a session adds to the target. */
    static int netKcal(double met, BigDecimal bodyKg, int minutes) {
        return roundTo10(Math.max(0, met - 1) * bodyKg.doubleValue() * minutes / 60.0);
    }

    static int grossKcal(double met, BigDecimal bodyKg, int minutes) {
        return roundTo10(met * bodyKg.doubleValue() * minutes / 60.0);
    }

    private static int roundTo10(double kcal) {
        return (int) Math.round(kcal / 10.0) * 10;
    }

    /** Weight trend, else the latest weight, else 83 kg. */
    public BigDecimal bodyWeightKg() {
        WeightTrend trend = healthService.weightTrend();
        if (trend.trendKg() != null) {
            return trend.trendKg();
        }
        return trend.latestKg() != null ? trend.latestKg() : DEFAULT_BODY_WEIGHT_KG;
    }

    /** Preview for the chips: what a Freeletics session would add. */
    public int freeleticsBonus(int minutes, Intensity intensity) {
        return netKcal(WorkoutType.FREELETICS.met(intensity), bodyWeightKg(), minutes);
    }

    // --- logging ---

    @Transactional
    public Workout logFreeletics(int minutes, Intensity intensity) {
        checkMinutes(minutes);
        double met = WorkoutType.FREELETICS.met(intensity);
        BigDecimal kg = bodyWeightKg();
        return save(WorkoutType.FREELETICS, null, minutes, intensity, met,
                grossKcal(met, kg, minutes), netKcal(met, kg, minutes), KcalSource.MET);
    }

    @Transactional
    public Workout logActivity(WorkoutType type, int minutes) {
        if (type != WorkoutType.WALK && type != WorkoutType.CYCLING) {
            throw new IllegalArgumentException("Nur Gehen oder Rad");
        }
        checkMinutes(minutes);
        double met = type.met(null);
        BigDecimal kg = bodyWeightKg();
        return save(type, null, minutes, null, met, grossKcal(met, kg, minutes), netKcal(met, kg, minutes), KcalSource.MET);
    }

    /** "Etwas anderes": the model picks title and MET; minutes from the form, else from the sentence. */
    @Transactional
    public Workout logOther(String description, Integer minutes) {
        ActivityDraft draft = activityEstimator.estimate(description);
        int duration = minutes != null && minutes > 0 ? minutes : draft.durationMin();
        if (duration <= 0) {
            throw new IllegalArgumentException("Wie lange? Bitte die Dauer in Minuten eintragen.");
        }
        checkMinutes(duration);
        BigDecimal kg = bodyWeightKg();
        String title = draft.title() == null || draft.title().isBlank() ? description.trim() : draft.title().trim();
        return save(WorkoutType.OTHER, title.length() > 255 ? title.substring(0, 255) : title, duration, null,
                draft.met(), grossKcal(draft.met(), kg, duration), netKcal(draft.met(), kg, duration), KcalSource.AI);
    }

    /** Football adds the goal's flat bonus: the daily burn already assumes two-thirds of it. */
    @Transactional
    public Workout logFootball() {
        LocalDate today = healthService.today();
        UserProfile user = currentUser();
        workoutRepository.findByUserUsernameAndDoneOnOrderByCreatedAtAsc(user.getUsername(), today).stream()
                .filter(w -> w.getType() == WorkoutType.FOOTBALL)
                .forEach(workoutRepository::delete);
        HealthDay day = healthService.day(today);
        day.setFootballCancelled(false);
        dayRepository.save(day);
        int bonus = healthService.currentGoal().map(HealthGoal::getFootballBonusKcal).orElse(0);
        double met = WorkoutType.FOOTBALL.met(Intensity.MEDIUM);
        return save(WorkoutType.FOOTBALL, null, footballMinutes, Intensity.MEDIUM, met,
                grossKcal(met, bodyWeightKg(), footballMinutes), bonus, KcalSource.MET);
    }

    @Transactional
    public void cancelFootball() {
        resetFootball();
        HealthDay day = healthService.day(healthService.today());
        day.setFootballCancelled(true);
        dayRepository.save(day);
    }

    /** "Ändern": back to the open question. */
    @Transactional
    public void resetFootball() {
        LocalDate today = healthService.today();
        workoutRepository.findByUserUsernameAndDoneOnOrderByCreatedAtAsc(username(), today).stream()
                .filter(w -> w.getType() == WorkoutType.FOOTBALL)
                .forEach(workoutRepository::delete);
        HealthDay day = healthService.day(today);
        day.setFootballCancelled(false);
        dayRepository.save(day);
    }

    @Transactional
    public void delete(Long workoutId) {
        workoutRepository.findByIdAndUserUsername(workoutId, username()).ifPresent(workoutRepository::delete);
    }

    private Workout save(WorkoutType type, String title, int minutes, Intensity intensity, double met,
                         int kcal, int bonus, KcalSource source) {
        LocalDate today = healthService.today();
        healthService.day(today);
        Workout workout = new Workout();
        workout.setUser(currentUser());
        workout.setDoneOn(today);
        workout.setType(type);
        workout.setTitle(title);
        workout.setDurationMin(minutes);
        workout.setIntensity(intensity);
        workout.setMet(BigDecimal.valueOf(met).setScale(1, RoundingMode.HALF_UP));
        workout.setKcal(kcal);
        workout.setBonusKcal(bonus);
        workout.setKcalSource(source);
        workout.setCreatedAt(LocalDateTime.now(clock));
        return workoutRepository.save(workout);
    }

    private static void checkMinutes(int minutes) {
        if (minutes < 1 || minutes > 600) {
            throw new IllegalArgumentException("Bitte eine Dauer zwischen 1 und 600 Minuten angeben");
        }
    }

    // --- reading ---

    public List<Workout> recent() {
        return workoutRepository.findTop10ByUserUsernameOrderByDoneOnDescCreatedAtDesc(username());
    }

    public List<Workout> workoutsOn(LocalDate date) {
        return workoutRepository.findByUserUsernameAndDoneOnOrderByCreatedAtAsc(username(), date);
    }

    /** The football card on Heute: asked on the football weekday, shown any day once answered. */
    public FootballState footballState(LocalDate date) {
        boolean played = workoutsOn(date).stream().anyMatch(w -> w.getType() == WorkoutType.FOOTBALL);
        if (played) {
            return FootballState.PLAYED;
        }
        boolean cancelled = dayRepository.findByUserUsernameAndDate(username(), date)
                .map(HealthDay::isFootballCancelled).orElse(false);
        if (cancelled) {
            return FootballState.CANCELLED;
        }
        return date.getDayOfWeek() == footballDay ? FootballState.OPEN : FootballState.NOT_ASKED;
    }

    public TrainingWeek week() {
        LocalDate today = healthService.today();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Workout> workouts = workoutRepository.findByUserUsernameAndDoneOnBetweenOrderByDoneOnAscCreatedAtAsc(
                username(), monday, monday.plusDays(6));
        int freeletics = (int) workouts.stream().filter(w -> w.getType() == WorkoutType.FREELETICS).count();
        boolean football = workouts.stream().anyMatch(w -> w.getType() == WorkoutType.FOOTBALL);
        LocalDate footballDate = monday.plusDays(footballDay.getValue() - 1L);
        boolean cancelled = !football && dayRepository.findByUserUsernameAndDate(username(), footballDate)
                .map(HealthDay::isFootballCancelled).orElse(false);
        return new TrainingWeek(freeletics, freeleticsPerWeek,
                football ? FootballState.PLAYED : cancelled ? FootballState.CANCELLED : FootballState.OPEN);
    }

    private String username() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private UserProfile currentUser() {
        return userProfileRepository.findByUsername(username())
                .orElseThrow(() -> new IllegalStateException("Unknown user " + username()));
    }
}
