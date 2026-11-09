package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.health.FootballState;
import at.v3rtumnus.planman.dto.health.TrainingWeek;
import at.v3rtumnus.planman.entity.health.Intensity;
import at.v3rtumnus.planman.entity.health.Workout;
import at.v3rtumnus.planman.entity.health.WorkoutType;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import at.v3rtumnus.planman.service.health.AiRequestException;
import at.v3rtumnus.planman.service.health.TrainingService;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TrainingController.class)
@WithMockUser
class TrainingControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrainingService trainingService;
    @MockitoBean
    private PlanManUserDetailsService userDetailsService;
    @MockitoBean
    private DataSource dataSource;
    @MockitoBean
    private CacheManager cacheManager;

    private static Workout workout(WorkoutType type, String title, int minutes, Intensity intensity, int bonus) {
        Workout w = new Workout();
        w.setId(3L);
        w.setType(type);
        w.setTitle(title);
        w.setDoneOn(TODAY.minusDays(1));
        w.setDurationMin(minutes);
        w.setIntensity(intensity);
        w.setBonusKcal(bonus);
        return w;
    }

    @BeforeEach
    void setUp() {
        when(trainingService.today()).thenReturn(TODAY);
        when(trainingService.week()).thenReturn(new TrainingWeek(1, 2, FootballState.OPEN));
        when(trainingService.recent()).thenReturn(List.of(workout(WorkoutType.FREELETICS, null, 35, Intensity.MEDIUM, 290)));
        when(trainingService.footballState(TODAY)).thenReturn(FootballState.OPEN);
        when(trainingService.footballMinutes()).thenReturn(70);
        when(trainingService.bodyWeightKg()).thenReturn(new BigDecimal("82.6"));
        when(trainingService.aiAvailable()).thenReturn(true);
    }

    @Test
    void training_rendersWeekChipsFootballAndRecent() throws Exception {
        mockMvc.perform(get("/health/training"))
                .andExpect(status().isOk())
                .andExpect(view().name("health/training"))
                .andExpect(content().string(containsString("Diese Woche: Freeletics 1 von 2 · Fußball offen")))
                .andExpect(content().string(containsString("data-body-kg=\"82.6\"")))
                .andExpect(content().string(containsString("50 min")))
                .andExpect(content().string(containsString("70 min gespielt")))
                .andExpect(content().string(containsString("Freeletics · 35 min · mittel")))
                .andExpect(content().string(containsString("+290 kcal")))
                .andExpect(content().string(containsString(">Mo<")))
                .andExpect(content().string(containsString("health-training.js")))
                .andExpect(content().string(containsString("aria-current=\"page\">Training")));
    }

    @Test
    void training_footballPlayedOrCancelled_offersChange() throws Exception {
        when(trainingService.footballState(TODAY)).thenReturn(FootballState.PLAYED);
        mockMvc.perform(get("/health/training"))
                .andExpect(content().string(containsString("Heute 70 min eingetragen")));
        when(trainingService.footballState(TODAY)).thenReturn(FootballState.CANCELLED);
        mockMvc.perform(get("/health/training"))
                .andExpect(content().string(containsString("Fällt heute aus")));
    }

    @Test
    void freeletics_logsAndShowsBonus() throws Exception {
        when(trainingService.logFreeletics(30, Intensity.MEDIUM)).thenReturn(workout(WorkoutType.FREELETICS, null, 30, Intensity.MEDIUM, 250));

        mockMvc.perform(post("/health/training/freeletics").param("minutes", "30").param("intensity", "MEDIUM"))
                .andExpect(redirectedUrl("/health/training"))
                .andExpect(flash().attribute("successMessage", "Freeletics eingetragen: +250 kcal fürs Tagesziel"));
    }

    @Test
    void activity_invalid_showsError() throws Exception {
        when(trainingService.logActivity(WorkoutType.WALK, 0)).thenThrow(new IllegalArgumentException("Bitte eine Dauer"));

        mockMvc.perform(post("/health/training/activity").param("type", "WALK").param("minutes", "0"))
                .andExpect(flash().attribute("errorMessage", "Bitte eine Dauer"));
    }

    @Test
    void other_logsOrKeepsTextOnAiError() throws Exception {
        when(trainingService.logOther("Tennis", 60)).thenReturn(workout(WorkoutType.OTHER, "Tennis Doppel", 60, null, 330));
        mockMvc.perform(post("/health/training/other").param("description", "Tennis").param("minutes", "60"))
                .andExpect(flash().attribute("successMessage", "Tennis Doppel eingetragen: +330 kcal fürs Tagesziel"));

        when(trainingService.logOther(eq("Golf"), isNull()))
                .thenThrow(new AiRequestException(AiRequestException.Reason.BILLING, "Guthaben aufgebraucht", null));
        mockMvc.perform(post("/health/training/other").param("description", "Golf"))
                .andExpect(redirectedUrl("/health/training"))
                .andExpect(flash().attribute("errorMessage", "Guthaben aufgebraucht"))
                .andExpect(flash().attribute("otherDescription", "Golf"));

        when(trainingService.logOther(eq("Skitag"), isNull()))
                .thenThrow(new IllegalArgumentException("Wie lange? Bitte die Dauer in Minuten eintragen."));
        mockMvc.perform(post("/health/training/other").param("description", "Skitag"))
                .andExpect(flash().attribute("errorMessage", "Wie lange? Bitte die Dauer in Minuten eintragen."))
                .andExpect(flash().attribute("otherDescription", "Skitag"));
    }

    @Test
    void football_answers_returnToTheAskingPage() throws Exception {
        when(trainingService.logFootball()).thenReturn(workout(WorkoutType.FOOTBALL, null, 70, Intensity.MEDIUM, 250));

        mockMvc.perform(post("/health/training/football").param("answer", "played").param("returnTo", "/health"))
                .andExpect(redirectedUrl("/health"))
                .andExpect(flash().attribute("successMessage", "Fußball eingetragen: +250 kcal"));
        mockMvc.perform(post("/health/training/football").param("answer", "cancelled"))
                .andExpect(redirectedUrl("/health/training"));
        verify(trainingService).cancelFootball();
        mockMvc.perform(post("/health/training/football").param("answer", "reset").param("returnTo", "https://evil.example"))
                .andExpect(redirectedUrl("/health/training"));
        verify(trainingService).resetFootball();
    }

    @Test
    void delete_removesWorkout() throws Exception {
        mockMvc.perform(post("/health/training/3/delete")).andExpect(redirectedUrl("/health/training"));
        verify(trainingService).delete(3L);
    }
}
