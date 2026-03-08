package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.fitness.FitnessMealLogRepository;
import at.v3rtumnus.planman.dao.fitness.FitnessProfileRepository;
import at.v3rtumnus.planman.dto.fitness.MealLogDTO;
import at.v3rtumnus.planman.dto.fitness.NutritionExtractionResult;
import at.v3rtumnus.planman.entity.fitness.FitnessMealLog;
import at.v3rtumnus.planman.entity.fitness.FitnessProfile;
import at.v3rtumnus.planman.exception.FitnessAiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitnessNutritionAiService {

    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FitnessService fitnessService;
    private final FitnessHealthService fitnessHealthService;
    private final FitnessMealLogRepository mealLogRepository;

    @Value("${fitness.ai.timeout-seconds:30}")
    private int aiTimeoutSeconds;

    public NutritionExtractionResult extractNutrition(String mealText) {
        String prompt = """
                Analysiere die folgende Mahlzeitbeschreibung und schätze Kalorien und Makronährstoffe.
                Beschreibung: %s

                Antworte ausschließlich als gültiges JSON (kein Markdown):
                {
                  "calories": 1740,
                  "protein_g": 85,
                  "carbs_g": 210,
                  "fat_g": 65,
                  "notes": "Frühstück ~380 kcal, Mittagessen ~520 kcal, ..."
                }
                Wenn ungenau: Schätzung mit Hinweis im notes-Feld.
                """.formatted(mealText);

        String response = callAiWithTimeout(prompt);
        return parseNutritionResponse(response);
    }

    @Transactional
    public FitnessMealLog saveMealLog(String username, String mealText) {
        FitnessProfile profile = fitnessService.getOrCreateFitnessProfile(username);
        LocalDate today = LocalDate.now();

        NutritionExtractionResult nutrition = extractNutrition(mealText);

        Optional<FitnessMealLog> existing = mealLogRepository.findByFitnessProfileAndLogDate(profile, today);
        FitnessMealLog mealLog = existing.orElse(new FitnessMealLog());

        if (mealLog.getId() == null) {
            mealLog.setFitnessProfile(profile);
            mealLog.setLogDate(today);
            mealLog.setCreatedAt(LocalDateTime.now());
        }

        mealLog.setMealText(mealText);
        mealLog.setAiCalories(nutrition.getCalories());
        mealLog.setAiProteinG(nutrition.getProteinG());
        mealLog.setAiCarbsG(nutrition.getCarbsG());
        mealLog.setAiFatG(nutrition.getFatG());
        mealLog.setAiNotes(nutrition.getNotes());
        mealLog.setUpdatedAt(LocalDateTime.now());

        log.info("Saved meal log for user {} ({}kcal estimated)", username, nutrition.getCalories());
        return mealLogRepository.save(mealLog);
    }

    public MealLogDTO getMealLogDTO(String username, LocalDate date) {
        FitnessProfile profile = fitnessService.getOrCreateFitnessProfile(username);
        Optional<FitnessMealLog> logOpt = mealLogRepository.findByFitnessProfileAndLogDate(profile, date);

        Integer calorieTarget = fitnessHealthService.recalculateDailyCalorieTarget(username);
        Integer proteinTarget = fitnessHealthService.getProteinTarget(username);
        Integer carbsTarget = fitnessHealthService.getCarbsTarget(username);
        Integer fatTarget = fitnessHealthService.getFatTarget(username);

        if (logOpt.isEmpty()) {
            return new MealLogDTO(date, null, null, null, null, null, null,
                    calorieTarget, proteinTarget, carbsTarget, fatTarget, null, null, null, null);
        }

        FitnessMealLog log = logOpt.get();
        Integer calorieDelta = (log.getAiCalories() != null && calorieTarget != null)
                ? log.getAiCalories() - calorieTarget : null;
        Integer proteinDelta = (log.getAiProteinG() != null && proteinTarget != null)
                ? log.getAiProteinG() - proteinTarget : null;
        Integer carbsDelta = (log.getAiCarbsG() != null && carbsTarget != null)
                ? log.getAiCarbsG() - carbsTarget : null;
        Integer fatDelta = (log.getAiFatG() != null && fatTarget != null)
                ? log.getAiFatG() - fatTarget : null;

        return new MealLogDTO(
                log.getLogDate(), log.getMealText(), log.getAiCalories(),
                log.getAiProteinG(), log.getAiCarbsG(), log.getAiFatG(), log.getAiNotes(),
                calorieTarget, proteinTarget, carbsTarget, fatTarget,
                calorieDelta, proteinDelta, carbsDelta, fatDelta
        );
    }

    public List<MealLogDTO> getMealHistory(String username, int days) {
        FitnessProfile profile = fitnessService.getOrCreateFitnessProfile(username);
        LocalDate cutoff = LocalDate.now().minusDays(days);
        return mealLogRepository.findByFitnessProfileOrderByLogDateDesc(profile).stream()
                .filter(l -> !l.getLogDate().isBefore(cutoff))
                .map(l -> new MealLogDTO(l.getLogDate(), l.getMealText(), l.getAiCalories(),
                        l.getAiProteinG(), l.getAiCarbsG(), l.getAiFatG(), l.getAiNotes(),
                        null, null, null, null, null, null, null, null))
                .collect(Collectors.toList());
    }

    public List<MealLogDTO> getMealHistoryWithTargets(String username, int days) {
        FitnessProfile profile = fitnessService.getOrCreateFitnessProfile(username);
        LocalDate cutoff = LocalDate.now().minusDays(days);
        Integer calorieTarget = fitnessHealthService.recalculateDailyCalorieTarget(username);
        Integer proteinTarget = fitnessHealthService.getProteinTarget(username);
        Integer carbsTarget = fitnessHealthService.getCarbsTarget(username);
        Integer fatTarget = fitnessHealthService.getFatTarget(username);
        return mealLogRepository.findByFitnessProfileOrderByLogDateDesc(profile).stream()
                .filter(l -> !l.getLogDate().isBefore(cutoff))
                .map(l -> new MealLogDTO(l.getLogDate(), l.getMealText(), l.getAiCalories(),
                        l.getAiProteinG(), l.getAiCarbsG(), l.getAiFatG(), l.getAiNotes(),
                        calorieTarget, proteinTarget, carbsTarget, fatTarget,
                        null, null, null, null))
                .collect(Collectors.toList());
    }

    private String callAiWithTimeout(String prompt) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                ChatClient.builder(chatModel).build()
                        .prompt()
                        .user(prompt)
                        .call()
                        .content()
        );
        try {
            String response = future.get(aiTimeoutSeconds, TimeUnit.SECONDS);
            if (response == null || response.isBlank()) {
                throw new FitnessAiException("KI-Kalorienextraktion fehlgeschlagen: empty response");
            }
            return response;
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new FitnessAiException("KI-Kalorienextraktion fehlgeschlagen: timeout", e);
        } catch (FitnessAiException e) {
            throw e;
        } catch (Exception e) {
            throw new FitnessAiException("KI-Kalorienextraktion fehlgeschlagen", e);
        }
    }

    private NutritionExtractionResult parseNutritionResponse(String response) {
        String json = extractJson(response);
        try {
            AiNutritionResponse parsed = objectMapper.readValue(json, AiNutritionResponse.class);
            return new NutritionExtractionResult(parsed.calories, parsed.proteinG, parsed.carbsG, parsed.fatG, parsed.notes);
        } catch (Exception e) {
            log.error("Failed to parse nutrition AI response: {}", json, e);
            throw new FitnessAiException("Failed to parse nutrition response", e);
        }
    }

    private String extractJson(String response) {
        if (response == null) return "{}";
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AiNutritionResponse {
        @JsonProperty("calories") Integer calories;
        @JsonProperty("protein_g") Integer proteinG;
        @JsonProperty("carbs_g") Integer carbsG;
        @JsonProperty("fat_g") Integer fatG;
        @JsonProperty("notes") String notes;
    }
}
