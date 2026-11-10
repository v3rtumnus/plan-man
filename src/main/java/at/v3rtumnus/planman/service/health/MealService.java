package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.*;
import at.v3rtumnus.planman.dto.health.*;
import at.v3rtumnus.planman.dto.health.ai.MealDraft;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.health.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Meals of the logged-in user: capture with the model, review, favourites and day totals. */
@Service
@Slf4j
public class MealService {

    static final int QUICK_MEAL_LIMIT = 6;

    private final MealRepository mealRepository;
    private final SavedMealRepository savedMealRepository;
    private final MealRecognitionRepository recognitionRepository;
    private final WorkoutRepository workoutRepository;
    private final UserProfileRepository userProfileRepository;
    private final HealthService healthService;
    private final MealRecognizer recognizer;
    private final Clock clock;
    private final String mealModel;
    private final String compareModel;
    private final int compareCount;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public MealService(MealRepository mealRepository, SavedMealRepository savedMealRepository,
                       MealRecognitionRepository recognitionRepository, WorkoutRepository workoutRepository,
                       UserProfileRepository userProfileRepository,
                       HealthService healthService, MealRecognizer recognizer, Clock clock,
                       @Value("${planman.health.ai.meal-model:claude-sonnet-5-5}") String mealModel,
                       @Value("${planman.health.ai.compare-model:}") String compareModel,
                       @Value("${planman.health.ai.compare-count:10}") int compareCount) {
        this.mealRepository = mealRepository;
        this.savedMealRepository = savedMealRepository;
        this.recognitionRepository = recognitionRepository;
        this.workoutRepository = workoutRepository;
        this.userProfileRepository = userProfileRepository;
        this.healthService = healthService;
        this.recognizer = recognizer;
        this.clock = clock;
        this.mealModel = mealModel;
        this.compareModel = compareModel == null || compareModel.isBlank() ? null : compareModel;
        this.compareCount = compareCount;
    }

    public LocalDate today() {
        return healthService.today();
    }

    public MealSlot slotNow() {
        return MealSlot.at(LocalTime.now(clock));
    }

    public boolean recognitionAvailable() {
        return recognizer.available();
    }

    // --- day ---

    public NutritionDay nutritionDay(LocalDate date) {
        HealthDay day = healthService.day(date);
        List<Meal> meals = mealRepository.findByUserUsernameAndEatenOnOrderByCreatedAtAsc(username(), date);
        int kcal = meals.stream().mapToInt(Meal::getKcal).sum();
        BigDecimal protein = meals.stream().map(Meal::getProteinG).reduce(BigDecimal.ZERO, BigDecimal::add);
        int training = workoutRepository.findByUserUsernameAndDoneOnOrderByCreatedAtAsc(username(), date).stream()
                .mapToInt(Workout::getBonusKcal).sum();
        return new NutritionDay(day.getKcalTarget(), training, day.getProteinTargetG(), kcal, protein, meals);
    }

    // --- capture ---

    /** A draft form from the model, not saved yet. */
    @Transactional
    public Recognized recognize(MealSlot slot, String description, String photoJpegBase64) {
        return recognize(slot, healthService.today(), description, photoJpegBase64);
    }

    /** As above, for a meal eaten on {@code date} (today or a past day). */
    @Transactional
    public Recognized recognize(MealSlot slot, LocalDate date, String description, String photoJpegBase64) {
        checkDate(date);
        boolean hasText = description != null && !description.isBlank();
        boolean hasPhoto = photoJpegBase64 != null && !photoJpegBase64.isBlank();
        if (!hasText && !hasPhoto) {
            throw new AiRequestException("Bitte einen Satz schreiben oder ein Foto aufnehmen");
        }
        UserProfile user = currentUser();
        boolean compare = compareModel != null
                && recognitionRepository.countByUserUsernameAndModelBIsNotNull(user.getUsername()) < compareCount;

        MealRecognition recognition = new MealRecognition();
        recognition.setUser(user);
        recognition.setCreatedAt(LocalDateTime.now(clock));
        recognition.setDescription(hasText ? truncate(description.trim(), 1000) : null);
        recognition.setHadPhoto(hasPhoto);

        MealRecognizer.Result shown;
        if (compare) {
            shown = recognizeBoth(recognition, slot, description, photoJpegBase64, user.getUsername());
        } else {
            shown = recognizer.recognize(mealModel, slot, description, photoJpegBase64);
            fill(recognition, true, shown);
        }
        recognition.setShownModel(shown.model());
        MealRecognition saved = recognitionRepository.save(recognition);

        MealForm form = formFrom(shown.draft(), slot);
        form.setDate(date);
        form.setRecognitionId(saved.getId());
        return new Recognized(form, questionFrom(shown.draft(), form.getItems().size()));
    }

    /**
     * Haiku vs Sonnet test: both run in parallel; the review alternates which draft it shows so
     * your corrections do not lean towards one model. If one fails, the other is shown.
     */
    private MealRecognizer.Result recognizeBoth(MealRecognition recognition, MealSlot slot, String description,
                                                String photo, String username) {
        CompletableFuture<MealRecognizer.Result> a = CompletableFuture.supplyAsync(
                () -> recognizer.recognize(mealModel, slot, description, photo), executor);
        CompletableFuture<MealRecognizer.Result> b = CompletableFuture.supplyAsync(
                () -> recognizer.recognize(compareModel, slot, description, photo), executor);
        MealRecognizer.Result resultA = join(a);
        MealRecognizer.Result resultB = join(b);
        if (resultA == null && resultB == null) {
            throw firstError(a, b);
        }
        if (resultA == null || resultB == null) {
            MealRecognizer.Result only = resultA != null ? resultA : resultB;
            fill(recognition, true, only);
            return only;
        }
        fill(recognition, true, resultA);
        fill(recognition, false, resultB);
        boolean showA = recognitionRepository.countByUserUsernameAndModelBIsNotNull(username) % 2 == 0;
        return showA ? resultA : resultB;
    }

    /** Both models failed: report billing first, since retrying will not help then. */
    private static AiRequestException firstError(CompletableFuture<?> a, CompletableFuture<?> b) {
        AiRequestException ea = errorOf(a);
        AiRequestException eb = errorOf(b);
        if (eb != null && eb.getReason() == AiRequestException.Reason.BILLING) {
            return eb;
        }
        return ea != null ? ea : eb != null ? eb : new AiRequestException(AiRequestException.Reason.UNAVAILABLE,
                "Die Erkennung ist gerade nicht erreichbar. Bitte nochmal versuchen oder manuell eintragen.", null);
    }

    private static AiRequestException errorOf(CompletableFuture<?> future) {
        try {
            future.join();
            return null;
        } catch (CompletionException e) {
            return e.getCause() instanceof AiRequestException m ? m : null;
        }
    }

    private MealRecognizer.Result join(CompletableFuture<MealRecognizer.Result> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            log.warn("One model of the comparison failed: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            return null;
        }
    }

    private static void fill(MealRecognition recognition, boolean first, MealRecognizer.Result result) {
        if (first) {
            recognition.setModelA(result.model());
            recognition.setResultA(result.rawJson());
            recognition.setKcalA(result.kcal());
            recognition.setLatencyAMs(result.latencyMs());
        } else {
            recognition.setModelB(result.model());
            recognition.setResultB(result.rawJson());
            recognition.setKcalB(result.kcal());
            recognition.setLatencyBMs(result.latencyMs());
        }
    }

    public record Recognized(MealForm form, MealQuestion question) {
    }

    static MealForm formFrom(MealDraft draft, MealSlot slot) {
        MealForm form = new MealForm();
        form.setSlot(slot);
        form.setTitle(draft.title() == null || draft.title().isBlank()
                ? draft.items().getFirst().name() : truncate(draft.title().trim(), 255));
        for (MealDraft.DraftItem item : draft.items()) {
            form.getItems().add(new MealItemForm(truncate(item.name(), 255), Math.max(0, item.grams()),
                    decimal(item.kcalPer100g()), decimal(item.proteinPer100g()), decimal(item.carbsPer100g()),
                    decimal(item.fatPer100g()), item.unsure(),
                    item.portionHint() == null ? null : truncate(item.portionHint(), 255)));
        }
        return form;
    }

    static MealQuestion questionFrom(MealDraft draft, int itemCount) {
        MealDraft.DraftQuestion q = draft.question();
        if (q == null || !q.asked() || q.text() == null || q.text().isBlank() || q.options() == null
                || q.options().size() < 2 || q.itemIndex() < 0 || q.itemIndex() >= itemCount) {
            return null;
        }
        return new MealQuestion(q.text(), q.itemIndex(), q.options().stream()
                .limit(3)
                .map(o -> new MealQuestion.Option(o.label(), decimal(o.kcalPer100g()), decimal(o.proteinPer100g()),
                        decimal(o.carbsPer100g()), decimal(o.fatPer100g())))
                .toList());
    }

    public MealForm manualForm(MealSlot slot, String description) {
        return manualForm(slot, healthService.today(), description);
    }

    public MealForm manualForm(MealSlot slot, LocalDate date, String description) {
        checkDate(date);
        MealForm form = new MealForm();
        form.setSlot(slot);
        form.setDate(date);
        form.setTitle(description == null ? "" : truncate(description.trim(), 255));
        form.getItems().add(MealItemForm.empty());
        return form;
    }

    public Optional<MealForm> editForm(Long mealId) {
        return mealRepository.findByIdAndUserUsername(mealId, username()).map(meal -> {
            MealForm form = new MealForm();
            form.setMealId(meal.getId());
            form.setSlot(meal.getSlot());
            form.setDate(meal.getEatenOn());
            form.setTitle(meal.getTitle());
            meal.getItems().forEach(item -> form.getItems()
                    .add(MealItemForm.of(item, item.getName(), item.isUnsure(), item.getPortionHint())));
            return form;
        });
    }

    // --- save ---

    @Transactional
    public Meal save(MealForm form) {
        if (form.getDate().isAfter(healthService.today())) {
            throw new IllegalArgumentException("Das Datum darf nicht in der Zukunft liegen");
        }
        UserProfile user = currentUser();
        Meal meal;
        if (form.getMealId() != null) {
            meal = mealRepository.findByIdAndUserUsername(form.getMealId(), user.getUsername())
                    .orElseThrow(() -> new IllegalArgumentException("Mahlzeit nicht gefunden"));
        } else {
            meal = new Meal();
            meal.setUser(user);
            meal.setCreatedAt(LocalDateTime.now(clock));
            if (form.getRecognitionId() != null) {
                recognitionRepository.findByIdAndUserUsername(form.getRecognitionId(), user.getUsername())
                        .ifPresent(meal::setRecognition);
            }
            meal.setSource(meal.getRecognition() != null ? MealSource.AI
                    : form.getBarcode() != null && !form.getBarcode().isBlank() ? MealSource.BARCODE : MealSource.MANUAL);
        }
        meal.setEatenOn(form.getDate());
        meal.setSlot(form.getSlot());
        meal.setTitle(form.getTitle().trim());
        meal.replaceItems(form.getItems().stream().map(MealService::toItem).toList());

        if (form.isRemember()) {
            meal.setSavedMeal(remember(user, form));
        }
        healthService.day(form.getDate());
        return mealRepository.save(meal);
    }

    private SavedMeal remember(UserProfile user, MealForm form) {
        SavedMeal saved = savedMealRepository.findByUserUsernameAndNameIgnoreCase(user.getUsername(), form.getTitle().trim())
                .orElseGet(() -> {
                    SavedMeal created = new SavedMeal();
                    created.setUser(user);
                    created.setCreatedAt(LocalDateTime.now(clock));
                    return created;
                });
        saved.setName(form.getTitle().trim());
        saved.setUsualSlot(form.getSlot());
        saved.setUseCount(saved.getUseCount() + 1);
        saved.setLastUsedAt(LocalDateTime.now(clock));
        saved.replaceItems(form.getItems().stream().map(MealService::toSavedItem).toList());
        return savedMealRepository.save(saved);
    }

    private static MealItem toItem(MealItemForm f) {
        MealItem item = new MealItem();
        item.setName(f.getName().trim());
        item.setGrams(f.getGrams());
        item.setKcalPer100g(scale(f.getKcalPer100g()));
        item.setProteinPer100g(scale(f.getProteinPer100g()));
        item.setCarbsPer100g(scale(f.getCarbsPer100g()));
        item.setFatPer100g(scale(f.getFatPer100g()));
        item.setUnsure(f.isUnsure());
        item.setPortionHint(blankToNull(f.getPortionHint()));
        return item;
    }

    private static SavedMealItem toSavedItem(MealItemForm f) {
        SavedMealItem item = new SavedMealItem();
        item.setName(f.getName().trim());
        item.setGrams(f.getGrams());
        item.setKcalPer100g(scale(f.getKcalPer100g()));
        item.setProteinPer100g(scale(f.getProteinPer100g()));
        item.setCarbsPer100g(scale(f.getCarbsPer100g()));
        item.setFatPer100g(scale(f.getFatPer100g()));
        item.setPortionHint(blankToNull(f.getPortionHint()));
        return item;
    }

    // --- "Schnell wieder" ---

    /** Saved meals, usual slot first, then by use; plus yesterday's meals of the same slot. */
    public List<QuickMeal> quickMeals(MealSlot slot) {
        return quickMeals(slot, healthService.today());
    }

    /** As above for a day being filled in: "wie gestern" means the day before {@code date}. */
    public List<QuickMeal> quickMeals(MealSlot slot, LocalDate date) {
        String username = username();
        List<QuickMeal> result = new ArrayList<>();
        String dayBefore = date.equals(healthService.today()) ? "Wie gestern " : "Wie am Vortag ";
        mealRepository.findByUserUsernameAndEatenOnAndSlotOrderByCreatedAtAsc(username, date.minusDays(1), slot)
                .forEach(meal -> result.add(new QuickMeal(null, meal.getId(), dayBefore + slot.getLabel(),
                        meal.getTitle(), meal.getKcal(), meal.getProteinG())));
        savedMealRepository.findByUserUsername(username).stream()
                .sorted(Comparator.comparing((SavedMeal s) -> s.getUsualSlot() != slot)
                        .thenComparing(SavedMeal::getUseCount, Comparator.reverseOrder())
                        .thenComparing(SavedMeal::getLastUsedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(QUICK_MEAL_LIMIT)
                .forEach(s -> result.add(new QuickMeal(s.getId(), null, s.getName(),
                        s.getUseCount() > 1 ? s.getUseCount() + "× gegessen" : null, s.kcal(), s.protein())));
        return result;
    }

    @Transactional
    public Meal logSaved(Long savedMealId, MealSlot slot) {
        return logSaved(savedMealId, slot, healthService.today());
    }

    @Transactional
    public Meal logSaved(Long savedMealId, MealSlot slot, LocalDate date) {
        checkDate(date);
        UserProfile user = currentUser();
        SavedMeal saved = savedMealRepository.findByIdAndUserUsername(savedMealId, user.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Gespeicherte Mahlzeit nicht gefunden"));
        saved.setUseCount(saved.getUseCount() + 1);
        saved.setLastUsedAt(LocalDateTime.now(clock));
        Meal meal = newMeal(user, date, slot, saved.getName(), MealSource.SAVED);
        meal.setSavedMeal(saved);
        meal.replaceItems(saved.getItems().stream().map(s -> {
            MealItem item = copy(s);
            item.setPortionHint(s.getPortionHint());
            return item;
        }).toList());
        return mealRepository.save(meal);
    }

    @Transactional
    public Meal logRepeat(Long mealId, MealSlot slot) {
        return logRepeat(mealId, slot, healthService.today());
    }

    @Transactional
    public Meal logRepeat(Long mealId, MealSlot slot, LocalDate date) {
        checkDate(date);
        UserProfile user = currentUser();
        Meal source = mealRepository.findByIdAndUserUsername(mealId, user.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Mahlzeit nicht gefunden"));
        Meal meal = newMeal(user, date, slot, source.getTitle(), MealSource.REPEAT);
        meal.replaceItems(source.getItems().stream().map(s -> {
            MealItem item = copy(s);
            item.setPortionHint(s.getPortionHint());
            return item;
        }).toList());
        return mealRepository.save(meal);
    }

    private Meal newMeal(UserProfile user, LocalDate date, MealSlot slot, String title, MealSource source) {
        healthService.day(date);
        Meal meal = new Meal();
        meal.setUser(user);
        meal.setEatenOn(date);
        meal.setSlot(slot);
        meal.setTitle(title);
        meal.setSource(source);
        meal.setCreatedAt(LocalDateTime.now(clock));
        return meal;
    }

    private static MealItem copy(FoodValues source) {
        MealItem item = new MealItem();
        item.setName(source.getName());
        item.setGrams(source.getGrams());
        item.setKcalPer100g(source.getKcalPer100g());
        item.setProteinPer100g(source.getProteinPer100g());
        item.setCarbsPer100g(source.getCarbsPer100g());
        item.setFatPer100g(source.getFatPer100g());
        return item;
    }

    // --- delete ---

    /** Returns the day the meal was on, so the page can go back to it. */
    @Transactional
    public Optional<LocalDate> delete(Long mealId) {
        Optional<Meal> meal = mealRepository.findByIdAndUserUsername(mealId, username());
        meal.ifPresent(mealRepository::delete);
        return meal.map(Meal::getEatenOn);
    }

    private void checkDate(LocalDate date) {
        if (date.isAfter(healthService.today())) {
            throw new IllegalArgumentException("Das Datum darf nicht in der Zukunft liegen");
        }
    }

    /** Removes a favourite; meals logged from it stay. */
    @Transactional
    public void deleteSaved(Long savedMealId) {
        if (savedMealRepository.findByIdAndUserUsername(savedMealId, username()).isEmpty()) {
            return;
        }
        // bulk update clears the persistence context, so the favourite is loaded again to delete it
        mealRepository.detachSavedMeal(savedMealId);
        savedMealRepository.findById(savedMealId).ifPresent(savedMealRepository::delete);
    }

    // --- helpers ---

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(Math.max(0, value)).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private String username() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private UserProfile currentUser() {
        return userProfileRepository.findByUsername(username())
                .orElseThrow(() -> new IllegalStateException("Unknown user " + username()));
    }
}
