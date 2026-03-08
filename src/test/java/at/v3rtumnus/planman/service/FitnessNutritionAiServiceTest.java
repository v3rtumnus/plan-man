package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.fitness.FitnessMealLogRepository;
import at.v3rtumnus.planman.dto.fitness.MealLogDTO;
import at.v3rtumnus.planman.dto.fitness.NutritionExtractionResult;
import at.v3rtumnus.planman.entity.fitness.FitnessMealLog;
import at.v3rtumnus.planman.entity.fitness.FitnessProfile;
import at.v3rtumnus.planman.exception.FitnessAiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FitnessNutritionAiServiceTest {

    @Mock private OpenAiChatModel chatModel;
    @Mock private FitnessService fitnessService;
    @Mock private FitnessHealthService fitnessHealthService;
    @Mock private FitnessMealLogRepository mealLogRepository;

    @InjectMocks
    private FitnessNutritionAiService service;

    private FitnessProfile profile;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "aiTimeoutSeconds", 5);

        profile = new FitnessProfile();
        profile.setId(1L);
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(profile);
        when(fitnessHealthService.recalculateDailyCalorieTarget("alice")).thenReturn(2000);
        when(fitnessHealthService.getProteinTarget("alice")).thenReturn(130);
        when(fitnessHealthService.getCarbsTarget("alice")).thenReturn(220);
    }

    // ===== extractNutrition =====

    @Test
    void extractNutrition_success_returnsParsedValues() {
        String json = "{\"calories\":1740,\"protein_g\":85,\"carbs_g\":210,\"notes\":\"Estimated\"}";
        mockChatResponse(json);

        NutritionExtractionResult result = service.extractNutrition("Breakfast: eggs");

        assertThat(result.getCalories()).isEqualTo(1740);
        assertThat(result.getProteinG()).isEqualTo(85);
        assertThat(result.getCarbsG()).isEqualTo(210);
        assertThat(result.getNotes()).isEqualTo("Estimated");
    }

    @Test
    void extractNutrition_withMarkdownFences_stripsAndParses() {
        String response = "```json\n{\"calories\":500,\"protein_g\":30,\"carbs_g\":60,\"notes\":\"\"}\n```";
        mockChatResponse(response);

        NutritionExtractionResult result = service.extractNutrition("Lunch");

        assertThat(result.getCalories()).isEqualTo(500);
        assertThat(result.getProteinG()).isEqualTo(30);
    }

    @Test
    void extractNutrition_modelThrows_throwsFitnessAiException() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("API down"));

        assertThatThrownBy(() -> service.extractNutrition("food"))
                .isInstanceOf(FitnessAiException.class);
    }

    @Test
    void extractNutrition_emptyResponse_throwsFitnessAiException() {
        mockChatResponse("   ");

        assertThatThrownBy(() -> service.extractNutrition("food"))
                .isInstanceOf(FitnessAiException.class);
    }

    @Test
    void extractNutrition_invalidJson_throwsFitnessAiException() {
        mockChatResponse("not-json");

        assertThatThrownBy(() -> service.extractNutrition("food"))
                .isInstanceOf(FitnessAiException.class);
    }

    // ===== saveMealLog =====

    @Test
    void saveMealLog_newEntry_createsNewLog() {
        String json = "{\"calories\":1200,\"protein_g\":60,\"carbs_g\":150,\"notes\":\"OK\"}";
        mockChatResponse(json);

        when(mealLogRepository.findByFitnessProfileAndLogDate(eq(profile), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        FitnessMealLog saved = new FitnessMealLog();
        saved.setId(1L);
        when(mealLogRepository.save(any())).thenReturn(saved);

        FitnessMealLog result = service.saveMealLog("alice", "Eggs and rice");

        assertThat(result.getId()).isEqualTo(1L);
        ArgumentCaptor<FitnessMealLog> captor = ArgumentCaptor.forClass(FitnessMealLog.class);
        verify(mealLogRepository).save(captor.capture());
        assertThat(captor.getValue().getMealText()).isEqualTo("Eggs and rice");
        assertThat(captor.getValue().getAiCalories()).isEqualTo(1200);
        assertThat(captor.getValue().getLogDate()).isNotNull(); // new entry gets logDate set
    }

    @Test
    void saveMealLog_existingEntry_updatesLog() {
        String json = "{\"calories\":1800,\"protein_g\":90,\"carbs_g\":200,\"notes\":\"Updated\"}";
        mockChatResponse(json);

        FitnessMealLog existing = new FitnessMealLog();
        existing.setId(5L);
        existing.setFitnessProfile(profile);
        existing.setLogDate(LocalDate.now());
        existing.setCreatedAt(LocalDateTime.now());

        when(mealLogRepository.findByFitnessProfileAndLogDate(eq(profile), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));
        when(mealLogRepository.save(any())).thenReturn(existing);

        service.saveMealLog("alice", "Updated meal");

        ArgumentCaptor<FitnessMealLog> captor = ArgumentCaptor.forClass(FitnessMealLog.class);
        verify(mealLogRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(5L); // same entry updated
        assertThat(captor.getValue().getAiCalories()).isEqualTo(1800);
    }

    // ===== getMealLogDTO =====

    @Test
    void getMealLogDTO_noLog_returnsTargetsWithNullActuals() {
        when(mealLogRepository.findByFitnessProfileAndLogDate(eq(profile), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        MealLogDTO result = service.getMealLogDTO("alice", LocalDate.now());

        assertThat(result.getAiCalories()).isNull();
        assertThat(result.getDailyCalorieTarget()).isEqualTo(2000);
        assertThat(result.getTargetProteinG()).isEqualTo(130);
        assertThat(result.getCalorieDelta()).isNull();
    }

    @Test
    void getMealLogDTO_withLog_returnsWithDeltas() {
        FitnessMealLog log = new FitnessMealLog();
        log.setLogDate(LocalDate.now());
        log.setMealText("food");
        log.setAiCalories(2100);
        log.setAiProteinG(120);
        log.setAiCarbsG(250);
        log.setAiNotes("AI notes");

        when(mealLogRepository.findByFitnessProfileAndLogDate(eq(profile), any(LocalDate.class)))
                .thenReturn(Optional.of(log));

        MealLogDTO result = service.getMealLogDTO("alice", LocalDate.now());

        assertThat(result.getAiCalories()).isEqualTo(2100);
        assertThat(result.getCalorieDelta()).isEqualTo(2100 - 2000); // +100
        assertThat(result.getProteinDelta()).isEqualTo(120 - 130);   // -10
        assertThat(result.getCarbsDelta()).isEqualTo(250 - 220);     // +30
    }

    // ===== getMealHistory =====

    @Test
    void getMealHistory_filteredByDays_returnsMappedLogs() {
        FitnessMealLog recent = new FitnessMealLog();
        recent.setLogDate(LocalDate.now().minusDays(2));
        recent.setMealText("recent");
        recent.setAiCalories(1500);

        FitnessMealLog old = new FitnessMealLog();
        old.setLogDate(LocalDate.now().minusDays(20));
        old.setMealText("old");

        when(mealLogRepository.findByFitnessProfileOrderByLogDateDesc(profile))
                .thenReturn(List.of(recent, old));

        List<MealLogDTO> result = service.getMealHistory("alice", 7);

        // Only the recent one is within the last 7 days
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMealText()).isEqualTo("recent");
    }

    private void mockChatResponse(String content) {
        ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage(content))));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }
}
