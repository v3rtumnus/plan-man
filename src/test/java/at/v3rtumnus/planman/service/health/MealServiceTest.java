package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.*;
import at.v3rtumnus.planman.dto.health.*;
import at.v3rtumnus.planman.dto.health.ai.MealDraft;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.health.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MealServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final String HAIKU = "claude-haiku-5-5";
    private static final String SONNET = "claude-sonnet-5-5";

    @MockitoBean
    private CacheManager cacheManager;

    @Autowired
    private TestEntityManager em;
    @Autowired
    private HealthGoalRepository goalRepository;
    @Autowired
    private HealthDayRepository dayRepository;
    @Autowired
    private HealthPauseRepository pauseRepository;
    @Autowired
    private BodyMeasurementRepository measurementRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private MealRepository mealRepository;
    @Autowired
    private SavedMealRepository savedMealRepository;
    @Autowired
    private MealRecognitionRepository recognitionRepository;
    @Autowired
    private WorkoutRepository workoutRepository;

    private FakeRecognizer recognizer;
    private LocalTime now = LocalTime.of(12, 40);

    @BeforeEach
    void setUp() {
        for (String name : List.of("michael", "other")) {
            UserProfile user = new UserProfile();
            user.setUsername(name);
            user.setPassword("pw");
            em.persist(user);
        }
        loginAs("michael");
        recognizer = new FakeRecognizer();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private MealService service(String compareModel) {
        Clock clock = Clock.fixed(TODAY.atTime(now).atZone(ZONE).toInstant(), ZONE);
        HealthService health = new HealthService(goalRepository, dayRepository, pauseRepository, measurementRepository,
                userProfileRepository, clock);
        return new MealService(mealRepository, savedMealRepository, recognitionRepository, workoutRepository, userProfileRepository,
                health, recognizer, clock, HAIKU, compareModel, 2);
    }

    private MealService service() {
        return service("");
    }

    // --- recognition ---

    @Test
    void recognize_buildsFormAndQuestionAndLogsRecognition() {
        MealService.Recognized recognized = service().recognize(MealSlot.LUNCH, "Putenschnitzel mit Erdäpfelsalat", null);

        MealForm form = recognized.form();
        assertThat(form.getTitle()).isEqualTo("Putenschnitzel mit Erdäpfelsalat");
        assertThat(form.getSlot()).isEqualTo(MealSlot.LUNCH);
        assertThat(form.getDate()).isEqualTo(TODAY);
        assertThat(form.getItems()).hasSize(2);
        assertThat(form.getItems().getFirst().getGrams()).isEqualTo(180);
        assertThat(form.getItems().getFirst().getKcalPer100g()).isEqualByComparingTo("238.9");
        assertThat(form.getItems().getFirst().isUnsure()).isTrue();
        assertThat(recognized.question()).isNotNull();
        assertThat(recognized.question().itemIndex()).isZero();
        assertThat(recognized.question().options()).hasSize(2);

        MealRecognition logged = recognitionRepository.findById(form.getRecognitionId()).orElseThrow();
        assertThat(logged.getModelA()).isEqualTo(HAIKU);
        assertThat(logged.getModelB()).isNull();
        assertThat(logged.getShownModel()).isEqualTo(HAIKU);
        assertThat(logged.getKcalA()).isEqualTo(430 + 230);
        assertThat(logged.getDescription()).isEqualTo("Putenschnitzel mit Erdäpfelsalat");
        assertThat(recognizer.models).containsExactly(HAIKU);
    }

    @Test
    void recognize_withoutTextAndPhoto_fails() {
        assertThatThrownBy(() -> service().recognize(MealSlot.LUNCH, "  ", null))
                .isInstanceOf(AiRequestException.class);
    }

    @Test
    void recognize_photoOnly_isPassedOn() {
        service().recognize(MealSlot.DINNER, null, "abc");
        assertThat(recognizer.photos).containsExactly("abc");
        assertThat(recognitionRepository.findAll().getFirst().isHadPhoto()).isTrue();
    }

    @Test
    void recognize_compareMode_runsBothAndAlternatesShownModel_thenStops() {
        MealService service = service(SONNET);

        service.recognize(MealSlot.LUNCH, "Schnitzel", null);
        service.recognize(MealSlot.LUNCH, "Schnitzel", null);
        service.recognize(MealSlot.LUNCH, "Schnitzel", null);

        List<MealRecognition> all = recognitionRepository.findAll();
        assertThat(all).hasSize(3);
        assertThat(all.get(0).getModelB()).isEqualTo(SONNET);
        assertThat(all.get(0).getShownModel()).isEqualTo(HAIKU);
        assertThat(all.get(1).getShownModel()).isEqualTo(SONNET);
        // compare-count 2 reached: back to the meal model only
        assertThat(all.get(2).getModelB()).isNull();
        assertThat(all.get(2).getShownModel()).isEqualTo(HAIKU);
    }

    @Test
    void recognize_compareMode_oneModelFails_showsTheOther() {
        recognizer.failing.add(SONNET);
        service(SONNET).recognize(MealSlot.LUNCH, "Schnitzel", null);

        MealRecognition logged = recognitionRepository.findAll().getFirst();
        assertThat(logged.getShownModel()).isEqualTo(HAIKU);
        assertThat(logged.getModelB()).isNull();
    }

    @Test
    void recognize_compareMode_bothFail_throws() {
        recognizer.failing.add(SONNET);
        recognizer.failing.add(HAIKU);
        assertThatThrownBy(() -> service(SONNET).recognize(MealSlot.LUNCH, "Schnitzel", null))
                .isInstanceOf(AiRequestException.class);
    }

    @Test
    void recognize_compareMode_bothFail_reportsBillingFirst() {
        recognizer.failing.add(HAIKU);
        recognizer.billing.add(SONNET);
        assertThatThrownBy(() -> service(SONNET).recognize(MealSlot.LUNCH, "Schnitzel", null))
                .isInstanceOfSatisfying(AiRequestException.class,
                        e -> assertThat(e.getReason()).isEqualTo(AiRequestException.Reason.BILLING));
    }

    @Test
    void questionFrom_ignoresIncompleteQuestions() {
        MealDraft.DraftItem item = new MealDraft.DraftItem("Brot", 50, 250, 8, 48, 1, false, null);
        MealDraft.DraftOption option = new MealDraft.DraftOption("a", 1, 1, 1, 1);
        assertThat(MealService.questionFrom(new MealDraft("Brot", List.of(item), null), 1)).isNull();
        assertThat(MealService.questionFrom(new MealDraft("Brot", List.of(item),
                new MealDraft.DraftQuestion(false, "x", 0, List.of(option, option))), 1)).isNull();
        assertThat(MealService.questionFrom(new MealDraft("Brot", List.of(item),
                new MealDraft.DraftQuestion(true, "x", 3, List.of(option, option))), 1)).isNull();
        assertThat(MealService.questionFrom(new MealDraft("Brot", List.of(item),
                new MealDraft.DraftQuestion(true, "x", 0, List.of(option))), 1)).isNull();
    }

    @Test
    void formFrom_withoutTitle_usesFirstItem() {
        MealDraft draft = new MealDraft(" ", List.of(new MealDraft.DraftItem("Apfel", 150, 52, 0.3, 14, 0.2, false, "1 Stück")), null);
        MealForm form = MealService.formFrom(draft, MealSlot.SNACK);
        assertThat(form.getTitle()).isEqualTo("Apfel");
        assertThat(form.getItems().getFirst().getPortionHint()).isEqualTo("1 Stück");
    }

    @Test
    void slotNow_followsTheClock() {
        assertThat(service().slotNow()).isEqualTo(MealSlot.LUNCH);
        now = LocalTime.of(7, 0);
        assertThat(service().slotNow()).isEqualTo(MealSlot.BREAKFAST);
    }

    // --- save ---

    @Test
    void save_aiMeal_linksRecognitionAndStoresTotals() {
        MealService service = service();
        MealForm form = service.recognize(MealSlot.LUNCH, "Schnitzel", null).form();
        form.getItems().getFirst().setGrams(200);

        Meal meal = service.save(form);

        assertThat(meal.getSource()).isEqualTo(MealSource.AI);
        assertThat(meal.getRecognition()).isNotNull();
        assertThat(meal.getKcal()).isEqualTo(478 + 230);
        // 23.35 g/100 g is stored as 23.4: 2 × 23.4 + 4.0
        assertThat(meal.getProteinG()).isEqualByComparingTo("50.8");
        assertThat(meal.getItems()).extracting(MealItem::getPosition).containsExactly(0, 1);
        assertThat(meal.getSavedMeal()).isNull();
        assertThat(dayRepository.findByUserUsernameAndDate("michael", TODAY)).isPresent();
    }

    @Test
    void save_remember_createsAndThenUpdatesTheFavourite() {
        MealService service = service();
        MealForm form = manual("Frühstücksjause", 300);
        form.setRemember(true);
        service.save(form);
        MealForm again = manual("frühstücksjause", 400);
        again.setRemember(true);
        service.save(again);

        List<SavedMeal> saved = savedMealRepository.findByUserUsername("michael");
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getUseCount()).isEqualTo(2);
        assertThat(saved.getFirst().getItems()).hasSize(1);
        assertThat(saved.getFirst().kcal()).isEqualTo(400);
    }

    @Test
    void save_editReplacesItems_andOnlyOwnMeals() {
        MealService service = service();
        Meal meal = service.save(manual("Müsli", 300));
        MealForm edit = service.editForm(meal.getId()).orElseThrow();
        assertThat(edit.getItems()).hasSize(1);
        edit.getItems().getFirst().setGrams(50);
        edit.getItems().add(new MealItemForm("Milch", 200, new BigDecimal("64"), new BigDecimal("3.4"),
                new BigDecimal("4.8"), new BigDecimal("3.5"), false, null));

        Meal updated = service.save(edit);

        assertThat(updated.getId()).isEqualTo(meal.getId());
        assertThat(updated.getItems()).hasSize(2);
        assertThat(updated.getKcal()).isEqualTo(150 + 128);
        assertThat(mealRepository.count()).isEqualTo(1);

        loginAs("other");
        assertThat(service.editForm(meal.getId())).isEmpty();
        edit.setMealId(meal.getId());
        assertThatThrownBy(() -> service.save(edit)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void save_futureDate_isRejected() {
        MealForm form = manual("Müsli", 300);
        form.setDate(TODAY.plusDays(1));
        assertThatThrownBy(() -> service().save(form)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manualForm_hasOneEmptyItem() {
        MealForm form = service().manualForm(MealSlot.DINNER, " Gulasch ");
        assertThat(form.getTitle()).isEqualTo("Gulasch");
        assertThat(form.getDate()).isEqualTo(TODAY);
        assertThat(form.getItems()).hasSize(1);
    }

    // --- day and quick meals ---

    @Test
    void nutritionDay_sumsOwnMealsAgainstTargets() {
        HealthGoal goal = new HealthGoal();
        goal.setUser(userProfileRepository.findByUsername("michael").orElseThrow());
        goal.setTitle("Ziel");
        goal.setValidFrom(TODAY.minusDays(5));
        goal.setKcalTarget(2000);
        goal.setProteinTargetG(160);
        goal.setStepsTarget(6000);
        goal.setStartWeightKg(new BigDecimal("83.0"));
        goal.setTargetWeightKg(new BigDecimal("77.0"));
        goal.setTargetDate(TODAY.plusMonths(4));
        goal.setCreatedAt(LocalDateTime.now());
        goalRepository.save(goal);

        MealService service = service();
        service.save(manual("Müsli", 500));
        service.save(manual("Brot", 300));
        loginAs("other");
        service.save(manual("Fremd", 900));
        loginAs("michael");

        NutritionDay day = service.nutritionDay(TODAY);
        assertThat(day.kcalEaten()).isEqualTo(800);
        assertThat(day.kcalLeft()).isEqualTo(1200);
        assertThat(day.proteinEatenG()).isEqualByComparingTo("20.0");
        assertThat(day.proteinLeftG()).isEqualTo(140);
        assertThat(day.kcalPercent()).isEqualTo(40);
        assertThat(day.meals()).extracting(Meal::getTitle).containsExactly("Müsli", "Brot");
    }

    @Test
    void quickMeals_yesterdaySameSlotFirst_thenFavouritesBySlotAndUse() {
        MealService service = service();
        MealForm yesterday = manual("Spaghetti", 780);
        yesterday.setDate(TODAY.minusDays(1));
        service.save(yesterday);

        MealForm breakfast = manual("Frühstücksjause", 460);
        breakfast.setSlot(MealSlot.BREAKFAST);
        breakfast.setRemember(true);
        service.save(breakfast);
        service.save(breakfast);
        MealForm lunch = manual("Skyr + Banane", 260);
        lunch.setRemember(true);
        service.save(lunch);

        List<QuickMeal> quick = service.quickMeals(MealSlot.LUNCH);

        assertThat(quick).extracting(QuickMeal::name)
                .containsExactly("Wie gestern Mittag", "Skyr + Banane", "Frühstücksjause");
        assertThat(quick.getFirst().detail()).isEqualTo("Spaghetti");
        assertThat(quick.get(2).detail()).isEqualTo("2× gegessen");
    }

    @Test
    void logSaved_copiesItemsAndCountsUse() {
        MealService service = service();
        MealForm form = manual("Skyr", 260);
        form.setRemember(true);
        service.save(form);
        SavedMeal saved = savedMealRepository.findByUserUsername("michael").getFirst();

        Meal logged = service.logSaved(saved.getId(), MealSlot.SNACK);

        assertThat(logged.getSource()).isEqualTo(MealSource.SAVED);
        assertThat(logged.getSlot()).isEqualTo(MealSlot.SNACK);
        assertThat(logged.getKcal()).isEqualTo(260);
        assertThat(savedMealRepository.findById(saved.getId()).orElseThrow().getUseCount()).isEqualTo(2);
        loginAs("other");
        assertThatThrownBy(() -> service.logSaved(saved.getId(), MealSlot.SNACK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void logRepeat_copiesAnEarlierMealToToday() {
        MealService service = service();
        MealForm form = manual("Spaghetti", 780);
        form.setDate(TODAY.minusDays(1));
        Meal source = service.save(form);

        Meal copy = service.logRepeat(source.getId(), MealSlot.LUNCH);

        assertThat(copy.getId()).isNotEqualTo(source.getId());
        assertThat(copy.getEatenOn()).isEqualTo(TODAY);
        assertThat(copy.getSource()).isEqualTo(MealSource.REPEAT);
        assertThat(copy.getKcal()).isEqualTo(780);
    }

    @Test
    void delete_onlyOwnMeals_andDeleteSavedKeepsLoggedMeals() {
        MealService service = service();
        MealForm form = manual("Skyr", 260);
        form.setRemember(true);
        Meal meal = service.save(form);
        SavedMeal saved = savedMealRepository.findByUserUsername("michael").getFirst();

        loginAs("other");
        service.delete(meal.getId());
        service.deleteSaved(saved.getId());
        assertThat(mealRepository.count()).isEqualTo(1);
        assertThat(savedMealRepository.count()).isEqualTo(1);

        loginAs("michael");
        service.deleteSaved(saved.getId());
        em.flush();
        em.clear();
        assertThat(savedMealRepository.count()).isZero();
        assertThat(mealRepository.findById(meal.getId()).orElseThrow().getSavedMeal()).isNull();
        service.delete(meal.getId());
        assertThat(mealRepository.count()).isZero();
    }

    /** One item with exactly `kcal` kcal and 2.5 g protein per 100 kcal. */
    private static MealForm manual(String title, int kcal) {
        MealForm form = new MealForm();
        form.setTitle(title);
        form.setSlot(MealSlot.LUNCH);
        form.setDate(TODAY);
        form.getItems().add(new MealItemForm(title, 100, new BigDecimal(kcal), new BigDecimal(kcal).divide(new BigDecimal(40)),
                BigDecimal.TEN, BigDecimal.ONE, false, null));
        return form;
    }

    /** Returns a fixed schnitzel draft; models listed in `failing` throw. */
    static class FakeRecognizer implements MealRecognizer {
        final List<String> models = new ArrayList<>();
        final List<String> photos = new ArrayList<>();
        final Set<String> failing = new HashSet<>();
        final Set<String> billing = new HashSet<>();

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public synchronized Result recognize(String model, MealSlot slot, String description, String photo) {
            models.add(model);
            if (photo != null) {
                photos.add(photo);
            }
            if (billing.contains(model)) {
                throw new AiRequestException(AiRequestException.Reason.BILLING, "Guthaben", null);
            }
            if (failing.contains(model)) {
                throw new AiRequestException(AiRequestException.Reason.UNAVAILABLE, "down", null);
            }
            MealDraft draft = new MealDraft("Putenschnitzel mit Erdäpfelsalat", List.of(
                    new MealDraft.DraftItem("Putenschnitzel, paniert", 180, 238.9, 23.35, 10, 12, true, "≈ 1 Stück"),
                    new MealDraft.DraftItem("Erdäpfelsalat", 200, 115, 2, 15, 5, false, "≈ 1 Schöpfer")),
                    new MealDraft.DraftQuestion(true, "Wie wurde das Schnitzel gebacken?", 0, List.of(
                            new MealDraft.DraftOption("Öl", 238.9, 23.35, 10, 12),
                            new MealDraft.DraftOption("Butterschmalz", 253, 23.35, 10, 13.5))));
            return new Result(model, draft, "{\"title\":\"x\"}", 1200);
        }
    }

    @Test
    void pastDay_recognizeManualQuickAndDelete() {
        MealService service = service();
        LocalDate past = TODAY.minusDays(3);

        assertThat(service.recognize(MealSlot.LUNCH, past, "Putenschnitzel", null).form().getDate()).isEqualTo(past);
        assertThat(service.manualForm(MealSlot.DINNER, past, "Gulasch").getDate()).isEqualTo(past);
        assertThatThrownBy(() -> service.manualForm(MealSlot.DINNER, TODAY.plusDays(1), "x"))
                .isInstanceOf(IllegalArgumentException.class);

        MealForm form = manual("Spaghetti", 780);
        form.setDate(past.minusDays(1));
        form.setSlot(MealSlot.LUNCH);
        Meal source = service.save(form);
        assertThat(service.quickMeals(MealSlot.LUNCH, past)).extracting(QuickMeal::name).contains("Wie am Vortag Mittag");

        Meal copy = service.logRepeat(source.getId(), MealSlot.LUNCH, past);
        assertThat(copy.getEatenOn()).isEqualTo(past);
        assertThat(dayRepository.findByUserUsernameAndDate("michael", past)).isPresent();
        assertThatThrownBy(() -> service.logRepeat(source.getId(), MealSlot.LUNCH, TODAY.plusDays(2)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(service.delete(copy.getId())).contains(past);
        assertThat(service.delete(copy.getId())).isEmpty();
    }
}
