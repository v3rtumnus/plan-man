package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.fitness.*;
import at.v3rtumnus.planman.entity.fitness.FitnessMealLog;
import at.v3rtumnus.planman.entity.fitness.FitnessPlan;
import at.v3rtumnus.planman.entity.fitness.FitnessSessionLog;
import at.v3rtumnus.planman.entity.fitness.FitnessWeightLog;
import at.v3rtumnus.planman.entity.fitness.SkipReason;
import at.v3rtumnus.planman.service.FitnessAiService;
import at.v3rtumnus.planman.service.FitnessHealthService;
import at.v3rtumnus.planman.service.FitnessNutritionAiService;
import at.v3rtumnus.planman.service.FitnessService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/api/fitness")
@Slf4j
@AllArgsConstructor
public class FitnessApiController {

    private final FitnessService fitnessService;
    private final FitnessAiService fitnessAiService;
    private final FitnessHealthService fitnessHealthService;
    private final FitnessNutritionAiService fitnessNutritionAiService;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @PostMapping(value = "/session-log", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody FitnessSessionLog saveSessionLog(@RequestBody SessionLogDTO dto) {
        return fitnessService.saveSessionLog(currentUsername(), dto);
    }

    @PostMapping(value = "/session/{sessionId}/switch-type", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody ResponseEntity<Void> switchSessionType(@PathVariable Long sessionId) {
        fitnessService.switchSessionType(currentUsername(), sessionId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/session/{sessionId}/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody FitnessSessionLog markSessionComplete(@PathVariable Long sessionId,
            @RequestBody(required = false) MarkDoneRequestDTO request) {
        Integer rating = request != null ? request.getDifficultyRating() : null;
        String notes = request != null ? request.getNotes() : null;
        return fitnessService.markSessionComplete(currentUsername(), sessionId, rating, notes);
    }

    @PostMapping(value = "/session/{sessionId}/skip", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody FitnessSessionLog skipSession(
            @PathVariable Long sessionId,
            @RequestBody SkipSessionRequestDTO request) {
        SkipReason reason = SkipReason.valueOf(request.getReason());
        return fitnessService.skipSession(currentUsername(), sessionId, reason, request.getNotes());
    }

    @GetMapping(value = "/progress", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody FitnessProgressDTO getProgress() {
        return fitnessService.getProgressStats(currentUsername());
    }

    @PostMapping(value = "/regenerate-plan", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody FitnessPlan regeneratePlan(
            @RequestBody(required = false) RegeneratePlanRequestDTO request) {
        String userNotes = request != null ? request.getUserNotes() : null;
        return fitnessService.regeneratePlan(currentUsername(), userNotes);
    }

    @DeleteMapping(value = "/reset", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody ResponseEntity<Void> reset() {
        fitnessService.resetFitnessData(currentUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/weight", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody FitnessWeightLog logWeight(@RequestBody WeightLogDTO dto) {
        return fitnessHealthService.logWeight(currentUsername(), dto.getWeightKg(), dto.getNotes());
    }

    @PostMapping(value = "/health-profile", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody ResponseEntity<Void> saveHealthProfile(@RequestBody FitnessHealthProfileDTO dto) {
        fitnessHealthService.saveHealthProfile(currentUsername(), dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/nutrition/meal", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody FitnessMealLog saveMeal(@RequestBody MealTextRequestDTO dto) {
        return fitnessNutritionAiService.saveMealLog(currentUsername(), dto.getMealText());
    }
}
