package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.fitness.*;
import at.v3rtumnus.planman.entity.fitness.*;
import at.v3rtumnus.planman.exception.FitnessAiException;
import at.v3rtumnus.planman.service.*;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FitnessController.class)
class FitnessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private FitnessService fitnessService;
    @MockitoBean private FitnessAiService fitnessAiService;
    @MockitoBean private FitnessHealthService fitnessHealthService;
    @MockitoBean private FitnessNutritionAiService fitnessNutritionAiService;
    @MockitoBean private PlanManUserDetailsService userDetailsService;
    @MockitoBean private DataSource dataSource;
    @MockitoBean private CacheManager cacheManager;

    private FitnessProfile completedProfile;
    private FitnessProfile uncompletedProfile;

    @BeforeEach
    void setUp() {
        completedProfile = new FitnessProfile();
        completedProfile.setId(1L);
        completedProfile.setAssessmentCompleted(true);

        uncompletedProfile = new FitnessProfile();
        uncompletedProfile.setId(2L);
        uncompletedProfile.setAssessmentCompleted(false);

        // Safe defaults for all service methods
        when(fitnessService.getOrCreateFitnessProfile(anyString())).thenReturn(completedProfile);
        when(fitnessService.getActivePlan(anyString())).thenReturn(null);
        when(fitnessService.getProgressStats(anyString())).thenReturn(
                new FitnessProgressDTO(List.of(), List.of(), List.of(), List.of(), 0, 0, 0.0));
        when(fitnessService.getSessionHistory(anyString(), anyInt())).thenReturn(List.of());
        when(fitnessHealthService.getWeightHistory(anyString())).thenReturn(List.of());
        when(fitnessHealthService.getHealthProfile(anyString())).thenReturn(new FitnessHealthProfileDTO());
        when(fitnessHealthService.recalculateDailyCalorieTarget(anyString())).thenReturn(null);
        when(fitnessNutritionAiService.getMealLogDTO(anyString(), any())).thenReturn(new MealLogDTO());
        when(fitnessService.getAllExercises()).thenReturn(List.of());
    }

    // ===== GET /fitness/assessment =====

    @Test
    @WithMockUser
    void assessment_notCompleted_returnsForm() throws Exception {
        when(fitnessService.getOrCreateFitnessProfile(anyString())).thenReturn(uncompletedProfile);

        mockMvc.perform(get("/fitness/assessment"))
                .andExpect(status().isOk())
                .andExpect(view().name("fitness/assessment"));
    }

    @Test
    @WithMockUser
    void assessment_alreadyCompleted_redirectsToOverview() throws Exception {
        mockMvc.perform(get("/fitness/assessment"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/fitness/overview"));
    }

    // ===== POST /fitness/assessment =====

    @Test
    @WithMockUser
    void submitAssessment_success_redirectsToOverview() throws Exception {
        mockMvc.perform(post("/fitness/assessment")
                        .param("training_days", "3")
                        .param("focus_area", "CORE")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/fitness/overview"));

        verify(fitnessService).saveAssessmentAnswers(anyString(), anyList());
        verify(fitnessAiService).generateInitialPlan(anyString());
    }

    @Test
    @WithMockUser
    void submitAssessment_aiFails_returnsFormWithError() throws Exception {
        doThrow(new FitnessAiException("AI timeout")).when(fitnessAiService).generateInitialPlan(anyString());

        mockMvc.perform(post("/fitness/assessment")
                        .param("training_days", "3")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("fitness/assessment"))
                .andExpect(model().attributeExists("error"));
    }

    // ===== GET /fitness/overview =====

    @Test
    @WithMockUser
    void overview_notCompleted_redirectsToAssessment() throws Exception {
        when(fitnessService.getOrCreateFitnessProfile(anyString())).thenReturn(uncompletedProfile);

        mockMvc.perform(get("/fitness/overview"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/fitness/assessment"));
    }

    @Test
    @WithMockUser
    void overview_completed_returnsView() throws Exception {
        mockMvc.perform(get("/fitness/overview"))
                .andExpect(status().isOk())
                .andExpect(view().name("fitness/overview"));
    }

    @Test
    @WithMockUser
    void overview_withActivePlan_addsTodaySession() throws Exception {
        FitnessPlanSessionDTO pending = new FitnessPlanSessionDTO();
        pending.setId(1L);
        pending.setSessionType(SessionType.BODYWEIGHT);
        pending.setStatus(null); // pending

        FitnessPlanDTO plan = new FitnessPlanDTO(1L, 1, LocalDateTime.now(), true,
                PlanGenerationReason.INITIAL, "AI notes", List.of(pending));
        when(fitnessService.getActivePlan(anyString())).thenReturn(plan);

        mockMvc.perform(get("/fitness/overview"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("todaySession"));
    }

    // ===== GET /fitness/plan =====

    @Test
    @WithMockUser
    void plan_notCompleted_redirectsToAssessment() throws Exception {
        when(fitnessService.getOrCreateFitnessProfile(anyString())).thenReturn(uncompletedProfile);

        mockMvc.perform(get("/fitness/plan"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/fitness/assessment"));
    }

    @Test
    @WithMockUser
    void plan_completed_returnsView() throws Exception {
        mockMvc.perform(get("/fitness/plan"))
                .andExpect(status().isOk())
                .andExpect(view().name("fitness/plan"))
                .andExpect(model().attributeExists("skipReasons"));
    }

    // ===== GET /fitness/session/{id}/active =====

    @Test
    @WithMockUser
    void sessionActive_sessionFound_returnsView() throws Exception {
        FitnessPlanSessionDTO session = new FitnessPlanSessionDTO();
        session.setId(5L);
        session.setSessionType(SessionType.BODYWEIGHT);
        session.setExercises(List.of());

        FitnessPlanDTO plan = new FitnessPlanDTO(1L, 1, LocalDateTime.now(), true,
                PlanGenerationReason.INITIAL, null, List.of(session));
        when(fitnessService.getActivePlan(anyString())).thenReturn(plan);

        mockMvc.perform(get("/fitness/session/5/active"))
                .andExpect(status().isOk())
                .andExpect(view().name("fitness/session-active"));
    }

    @Test
    @WithMockUser
    void sessionActive_noPlan_redirectsToPlan() throws Exception {
        when(fitnessService.getActivePlan(anyString())).thenReturn(null);

        mockMvc.perform(get("/fitness/session/99/active"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/fitness/plan"));
    }

    @Test
    @WithMockUser
    void sessionActive_sessionNotInPlan_redirectsToPlan() throws Exception {
        FitnessPlanDTO plan = new FitnessPlanDTO(1L, 1, LocalDateTime.now(), true,
                PlanGenerationReason.INITIAL, null, List.of());
        when(fitnessService.getActivePlan(anyString())).thenReturn(plan);

        mockMvc.perform(get("/fitness/session/99/active"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/fitness/plan"));
    }

    // ===== GET /fitness/session/{id} =====

    @Test
    @WithMockUser
    void sessionLog_sessionFound_returnsView() throws Exception {
        FitnessPlanSessionDTO session = new FitnessPlanSessionDTO();
        session.setId(7L);
        session.setSessionType(SessionType.RUNNING);
        session.setExercises(List.of());

        FitnessPlanDTO plan = new FitnessPlanDTO(1L, 1, LocalDateTime.now(), true,
                PlanGenerationReason.INITIAL, null, List.of(session));
        when(fitnessService.getActivePlan(anyString())).thenReturn(plan);

        mockMvc.perform(get("/fitness/session/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("fitness/session-log"));
    }

    @Test
    @WithMockUser
    void sessionLog_noPlan_redirectsToPlan() throws Exception {
        when(fitnessService.getActivePlan(anyString())).thenReturn(null);

        mockMvc.perform(get("/fitness/session/99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/fitness/plan"));
    }

    // ===== GET /fitness/history =====

    @Test
    @WithMockUser
    void history_returnsViewWithLogs() throws Exception {
        mockMvc.perform(get("/fitness/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("fitness/history"))
                .andExpect(model().attributeExists("sessionLogs"));
    }

    // ===== GET /fitness/weight =====

    @Test
    @WithMockUser
    void weight_returnsViewWithAttributes() throws Exception {
        mockMvc.perform(get("/fitness/weight"))
                .andExpect(status().isOk())
                .andExpect(view().name("fitness/weight"))
                .andExpect(model().attributeExists("weightHistory"))
                .andExpect(model().attributeExists("activityLevels"))
                .andExpect(model().attributeExists("biologicalSexValues"));
    }

    // ===== GET /fitness/nutrition =====

    // ===== GET /fitness/exercises =====

    @Test
    @WithMockUser
    void exercises_returnsViewWithExerciseList() throws Exception {
        FitnessExercise ex = new FitnessExercise();
        ex.setId(1L);
        ex.setName("Push-Up");
        ex.setCategory(ExerciseCategory.UPPER_BODY);
        ex.setTrackingType(ExerciseTrackingType.SETS_REPS);
        ex.setEquipment(Equipment.NONE);
        ex.setDifficulty(2);
        when(fitnessService.getAllExercises()).thenReturn(List.of(ex));

        mockMvc.perform(get("/fitness/exercises"))
                .andExpect(status().isOk())
                .andExpect(view().name("fitness/exercises"))
                .andExpect(model().attributeExists("exercises"));
    }

    // ===== GET /fitness/nutrition =====

    @Test
    @WithMockUser
    void nutrition_noDateParam_usesToday() throws Exception {
        mockMvc.perform(get("/fitness/nutrition"))
                .andExpect(status().isOk())
                .andExpect(view().name("fitness/nutrition"))
                .andExpect(model().attributeExists("logDate"));

        verify(fitnessNutritionAiService).getMealLogDTO(anyString(), eq(LocalDate.now()));
    }

    @Test
    @WithMockUser
    void nutrition_withDateParam_usesProvidedDate() throws Exception {
        mockMvc.perform(get("/fitness/nutrition").param("date", "2026-02-15"))
                .andExpect(status().isOk())
                .andExpect(view().name("fitness/nutrition"));

        verify(fitnessNutritionAiService).getMealLogDTO(anyString(), eq(LocalDate.of(2026, 2, 15)));
    }
}
