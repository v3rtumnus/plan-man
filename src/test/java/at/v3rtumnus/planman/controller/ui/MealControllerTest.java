package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.health.*;
import at.v3rtumnus.planman.entity.health.Meal;
import at.v3rtumnus.planman.entity.health.MealSlot;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import at.v3rtumnus.planman.service.health.MealRecognitionException;
import at.v3rtumnus.planman.service.health.MealService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MealController.class)
@WithMockUser
class MealControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MealService mealService;
    @MockitoBean
    private PlanManUserDetailsService userDetailsService;
    @MockitoBean
    private DataSource dataSource;
    @MockitoBean
    private CacheManager cacheManager;

    private MealForm draft;

    @BeforeEach
    void setUp() {
        draft = new MealForm();
        draft.setSlot(MealSlot.LUNCH);
        draft.setDate(TODAY);
        draft.setTitle("Putenschnitzel mit Erdäpfelsalat");
        draft.setRecognitionId(7L);
        draft.getItems().add(new MealItemForm("Putenschnitzel, paniert", 180, new BigDecimal("238.9"),
                new BigDecimal("23.4"), BigDecimal.TEN, new BigDecimal("12"), true, "≈ 1 Stück"));

        when(mealService.today()).thenReturn(TODAY);
        when(mealService.slotNow()).thenReturn(MealSlot.LUNCH);
        when(mealService.recognitionAvailable()).thenReturn(true);
        when(mealService.quickMeals(any())).thenReturn(List.of(
                new QuickMeal(3L, null, "Frühstücksjause", "31× gegessen", 460, new BigDecimal("28.0")),
                new QuickMeal(null, 9L, "Wie gestern Mittag", "Spaghetti", 780, new BigDecimal("38.0"))));
        when(mealService.nutritionDay(any())).thenReturn(
                new NutritionDay(2000, 160, 500, new BigDecimal("30.0"), List.of()));
    }

    @Test
    void capture_rendersSlotsAndQuickMeals() throws Exception {
        mockMvc.perform(get("/health/meals/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("health/meal-capture"))
                .andExpect(model().attribute("slot", MealSlot.LUNCH))
                .andExpect(content().string(containsString("Schnell wieder")))
                .andExpect(content().string(containsString("31× gegessen · 460 kcal")))
                .andExpect(content().string(containsString("Wie gestern Mittag")))
                .andExpect(content().string(containsString("Erkennen")))
                .andExpect(content().string(containsString("health-meal.js")));
    }

    @Test
    void capture_withoutKey_saysManualOnly() throws Exception {
        when(mealService.recognitionAvailable()).thenReturn(false);

        mockMvc.perform(get("/health/meals/new").param("slot", "DINNER").param("photo", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("slot", MealSlot.DINNER))
                .andExpect(content().string(containsString("Erkennung ist aus")));
    }

    @Test
    void recognize_showsReviewWithQuestionAndDayEffect() throws Exception {
        MealQuestion question = new MealQuestion("Wie wurde das Schnitzel gebacken?", 0, List.of(
                new MealQuestion.Option("Öl", new BigDecimal("238.9"), new BigDecimal("23.4"), BigDecimal.TEN, BigDecimal.ONE),
                new MealQuestion.Option("Butterschmalz", new BigDecimal("253"), new BigDecimal("23.4"), BigDecimal.TEN, BigDecimal.ONE)));
        when(mealService.recognize(eq(MealSlot.LUNCH), eq("Schnitzel"), eq("QUJD")))
                .thenReturn(new MealService.Recognized(draft, question));

        mockMvc.perform(post("/health/meals/recognize")
                        .param("slot", "LUNCH")
                        .param("description", "Schnitzel")
                        .param("photo", "data:image/jpeg;base64,QUJD"))
                .andExpect(status().isOk())
                .andExpect(view().name("health/meal-review"))
                .andExpect(model().attribute("kcalBefore", 500))
                .andExpect(content().string(containsString("Wie wurde das Schnitzel gebacken?")))
                .andExpect(content().string(containsString("Butterschmalz")))
                .andExpect(content().string(containsString("Menge geschätzt, bitte prüfen")))
                .andExpect(content().string(containsString("Putenschnitzel, paniert")))
                .andExpect(content().string(containsString("data-kcal-target=\"2000\"")));
    }

    @Test
    void recognize_failure_returnsToCaptureWithText() throws Exception {
        when(mealService.recognize(any(), any(), any())).thenThrow(new MealRecognitionException("Gerade nicht erreichbar"));

        mockMvc.perform(post("/health/meals/recognize").param("slot", "LUNCH").param("description", "Gulasch"))
                .andExpect(status().isOk())
                .andExpect(view().name("health/meal-capture"))
                .andExpect(model().attribute("description", "Gulasch"))
                .andExpect(content().string(containsString("Gerade nicht erreichbar")));
    }

    @Test
    void recognize_oversizedPhoto_isRefused() throws Exception {
        mockMvc.perform(post("/health/meals/recognize").param("slot", "LUNCH")
                        .param("photo", "A".repeat(MealController.MAX_PHOTO_BASE64 + 1)))
                .andExpect(status().isOk())
                .andExpect(view().name("health/meal-capture"))
                .andExpect(content().string(containsString("zu groß")));
        verify(mealService, never()).recognize(any(), any(), any());
    }

    @Test
    void manual_rendersEmptyReview() throws Exception {
        MealForm form = new MealForm();
        form.setSlot(MealSlot.DINNER);
        form.setDate(TODAY);
        form.setTitle("Gulasch");
        form.getItems().add(MealItemForm.empty());
        when(mealService.manualForm(MealSlot.DINNER, "Gulasch")).thenReturn(form);

        mockMvc.perform(get("/health/meals/manual").param("slot", "DINNER").param("description", "Gulasch"))
                .andExpect(status().isOk())
                .andExpect(view().name("health/meal-review"))
                .andExpect(content().string(containsString("Zutaten · Menge")))
                .andExpect(content().string(not(containsString("Mahlzeit löschen"))));
    }

    @Test
    void edit_rendersDeleteButton_andUnknownRedirects() throws Exception {
        draft.setMealId(5L);
        when(mealService.editForm(5L)).thenReturn(Optional.of(draft));
        when(mealService.editForm(6L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/health/meals/5/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mahlzeit bearbeiten")))
                .andExpect(content().string(containsString("Mahlzeit löschen")));
        mockMvc.perform(get("/health/meals/6/edit"))
                .andExpect(redirectedUrl("/health"));
    }

    @Test
    void save_valid_redirectsToToday() throws Exception {
        mockMvc.perform(post("/health/meals")
                        .param("slot", "LUNCH")
                        .param("date", "2026-11-10")
                        .param("title", "Skyr")
                        .param("remember", "true")
                        .param("items[0].name", "Skyr")
                        .param("items[0].grams", "250")
                        .param("items[0].kcalPer100g", "63")
                        .param("items[0].proteinPer100g", "11")
                        .param("items[0].carbsPer100g", "4")
                        .param("items[0].fatPer100g", "0.2"))
                .andExpect(redirectedUrl("/health"))
                .andExpect(flash().attribute("successMessage", "Skyr gespeichert"));
        verify(mealService).save(argThat(form -> form.isRemember() && form.getItems().getFirst().getGrams() == 250));
    }

    @Test
    void save_invalid_showsReviewAgain() throws Exception {
        mockMvc.perform(post("/health/meals").param("slot", "LUNCH").param("date", "2026-11-10").param("title", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("health/meal-review"))
                .andExpect(content().string(containsString("Bitte Name und Menge")));
        verify(mealService, never()).save(any());
    }

    @Test
    void save_rejectedByService_showsMessage() throws Exception {
        when(mealService.save(any())).thenThrow(new IllegalArgumentException("Das Datum darf nicht in der Zukunft liegen"));

        mockMvc.perform(post("/health/meals")
                        .param("slot", "LUNCH").param("date", "2026-11-10").param("title", "Skyr")
                        .param("items[0].name", "Skyr").param("items[0].grams", "250")
                        .param("items[0].kcalPer100g", "63").param("items[0].proteinPer100g", "11")
                        .param("items[0].carbsPer100g", "4").param("items[0].fatPer100g", "0.2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nicht in der Zukunft")));
    }

    @Test
    void quick_logsSavedOrRepeat() throws Exception {
        Meal meal = new Meal();
        meal.setTitle("Frühstücksjause");
        when(mealService.logSaved(3L, MealSlot.BREAKFAST)).thenReturn(meal);
        Meal copy = new Meal();
        copy.setTitle("Spaghetti");
        when(mealService.logRepeat(9L, MealSlot.LUNCH)).thenReturn(copy);

        mockMvc.perform(post("/health/meals/quick").param("slot", "BREAKFAST").param("savedMealId", "3"))
                .andExpect(redirectedUrl("/health"))
                .andExpect(flash().attribute("successMessage", "Frühstücksjause eingetragen"));
        mockMvc.perform(post("/health/meals/quick").param("slot", "LUNCH").param("mealId", "9"))
                .andExpect(flash().attribute("successMessage", "Spaghetti eingetragen"));

        when(mealService.logSaved(4L, MealSlot.LUNCH)).thenThrow(new IllegalArgumentException("nicht gefunden"));
        mockMvc.perform(post("/health/meals/quick").param("slot", "LUNCH").param("savedMealId", "4"))
                .andExpect(flash().attribute("errorMessage", "nicht gefunden"));
    }

    @Test
    void delete_andDeleteSaved() throws Exception {
        mockMvc.perform(post("/health/meals/5/delete"))
                .andExpect(redirectedUrl("/health"));
        verify(mealService).delete(5L);

        mockMvc.perform(post("/health/meals/saved/3/delete").param("slot", "LUNCH"))
                .andExpect(redirectedUrl("/health/meals/new?slot=LUNCH"));
        verify(mealService).deleteSaved(3L);
    }
}
