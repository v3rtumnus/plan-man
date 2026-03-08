package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.fitness.*;
import at.v3rtumnus.planman.entity.fitness.*;
import at.v3rtumnus.planman.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FitnessApiController.class)
class FitnessApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private FitnessService fitnessService;
    @MockitoBean private FitnessAiService fitnessAiService;
    @MockitoBean private FitnessHealthService fitnessHealthService;
    @MockitoBean private FitnessNutritionAiService fitnessNutritionAiService;
    @MockitoBean private PlanManUserDetailsService userDetailsService;
    @MockitoBean private DataSource dataSource;
    @MockitoBean private CacheManager cacheManager;

    // ===== POST /api/fitness/session-log =====

    @Test
    @WithMockUser
    void saveSessionLog_validRequest_returns200() throws Exception {
        FitnessSessionLog log = new FitnessSessionLog();
        log.setId(1L);
        when(fitnessService.saveSessionLog(anyString(), any())).thenReturn(log);

        mockMvc.perform(post("/api/fitness/session-log").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionType\":\"BODYWEIGHT\",\"logDate\":\"2026-03-01\"}"))
                .andExpect(status().isOk());

        verify(fitnessService).saveSessionLog(anyString(), any(SessionLogDTO.class));
    }

    // ===== POST /api/fitness/session/{id}/switch-type =====

    @Test
    @WithMockUser
    void switchSessionType_success_returns200() throws Exception {
        doNothing().when(fitnessService).switchSessionType(anyString(), eq(5L));

        mockMvc.perform(post("/api/fitness/session/5/switch-type").with(csrf()))
                .andExpect(status().isOk());

        verify(fitnessService).switchSessionType(anyString(), eq(5L));
    }

    // ===== POST /api/fitness/session/{id}/skip =====

    @Test
    @WithMockUser
    void skipSession_validRequest_returns200() throws Exception {
        FitnessSessionLog log = new FitnessSessionLog();
        log.setId(2L);
        when(fitnessService.skipSession(anyString(), eq(10L), any(SkipReason.class), anyString()))
                .thenReturn(log);

        mockMvc.perform(post("/api/fitness/session/10/skip").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SICK\",\"notes\":\"feeling ill\"}"))
                .andExpect(status().isOk());

        verify(fitnessService).skipSession(anyString(), eq(10L), eq(SkipReason.SICK), eq("feeling ill"));
    }

    @Test
    @WithMockUser
    void skipSession_otherSportReason_passes() throws Exception {
        when(fitnessService.skipSession(anyString(), anyLong(), any(), any()))
                .thenReturn(new FitnessSessionLog());

        mockMvc.perform(post("/api/fitness/session/1/skip").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER_SPORT\",\"notes\":\"cycling\"}"))
                .andExpect(status().isOk());
    }

    // ===== GET /api/fitness/progress =====

    @Test
    @WithMockUser
    void getProgress_returns200WithData() throws Exception {
        FitnessProgressDTO dto = new FitnessProgressDTO(
                List.of("KW 10/2026"), List.of(3), List.of(5.0), List.of(3.5), 10, 2, 20.5);
        when(fitnessService.getProgressStats(anyString())).thenReturn(dto);

        mockMvc.perform(get("/api/fitness/progress"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // ===== POST /api/fitness/regenerate-plan =====

    @Test
    @WithMockUser
    void regeneratePlan_withNotes_calls200() throws Exception {
        FitnessPlan plan = new FitnessPlan();
        plan.setId(1L);
        plan.setVersion(2);
        plan.setGeneratedAt(LocalDateTime.now());
        plan.setGenerationReason(PlanGenerationReason.MANUAL);
        when(fitnessService.regeneratePlan(anyString(), anyString())).thenReturn(plan);

        mockMvc.perform(post("/api/fitness/regenerate-plan").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userNotes\":\"More running please\"}"))
                .andExpect(status().isOk());

        verify(fitnessService).regeneratePlan(anyString(), eq("More running please"));
    }

    @Test
    @WithMockUser
    void regeneratePlan_noBody_usesNullNotes() throws Exception {
        FitnessPlan plan = new FitnessPlan();
        plan.setId(1L);
        plan.setVersion(1);
        plan.setGeneratedAt(LocalDateTime.now());
        when(fitnessService.regeneratePlan(anyString(), isNull())).thenReturn(plan);

        mockMvc.perform(post("/api/fitness/regenerate-plan").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(fitnessService).regeneratePlan(anyString(), isNull());
    }

    // ===== DELETE /api/fitness/reset =====

    @Test
    @WithMockUser
    void reset_callsServiceAndReturns200() throws Exception {
        mockMvc.perform(delete("/api/fitness/reset").with(csrf()))
                .andExpect(status().isOk());

        verify(fitnessService).resetFitnessData(anyString());
    }

    // ===== POST /api/fitness/weight =====

    @Test
    @WithMockUser
    void logWeight_returns200() throws Exception {
        FitnessWeightLog wl = new FitnessWeightLog();
        wl.setId(1L);
        wl.setWeightKg(BigDecimal.valueOf(82.5));
        wl.setLogDate(LocalDate.now());
        when(fitnessHealthService.logWeight(anyString(), any(), any())).thenReturn(wl);

        mockMvc.perform(post("/api/fitness/weight").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\":82.5,\"notes\":\"morning\"}"))
                .andExpect(status().isOk());

        verify(fitnessHealthService).logWeight(anyString(),
                eq(BigDecimal.valueOf(82.5)), eq("morning"));
    }

    @Test
    @WithMockUser
    void logWeight_noNotes_passes() throws Exception {
        FitnessWeightLog wl = new FitnessWeightLog();
        wl.setId(2L);
        wl.setWeightKg(BigDecimal.valueOf(80.0));
        wl.setLogDate(LocalDate.now());
        when(fitnessHealthService.logWeight(anyString(), any(), any())).thenReturn(wl);

        mockMvc.perform(post("/api/fitness/weight").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\":80.0}"))
                .andExpect(status().isOk());
    }

    // ===== POST /api/fitness/health-profile =====

    @Test
    @WithMockUser
    void saveHealthProfile_returns200() throws Exception {
        mockMvc.perform(post("/api/fitness/health-profile").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"heightCm\":175,\"birthYear\":1990,\"biologicalSex\":\"MALE\"," +
                                 "\"activityLevel\":\"MODERATELY_ACTIVE\",\"targetWeightKg\":78.0}"))
                .andExpect(status().isOk());

        verify(fitnessHealthService).saveHealthProfile(anyString(), any(FitnessHealthProfileDTO.class));
    }

    // ===== POST /api/fitness/nutrition/meal =====

    @Test
    @WithMockUser
    void saveMeal_returns200() throws Exception {
        FitnessMealLog ml = new FitnessMealLog();
        ml.setId(1L);
        ml.setLogDate(LocalDate.now());
        ml.setAiCalories(1500);
        when(fitnessNutritionAiService.saveMealLog(anyString(), anyString())).thenReturn(ml);

        mockMvc.perform(post("/api/fitness/nutrition/meal").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mealText\":\"Eggs and toast\"}"))
                .andExpect(status().isOk());

        verify(fitnessNutritionAiService).saveMealLog(anyString(), eq("Eggs and toast"));
    }
}
