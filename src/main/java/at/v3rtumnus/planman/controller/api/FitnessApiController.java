package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.fitness.FitnessHealthProfileDTO;
import at.v3rtumnus.planman.dto.fitness.MealTextRequestDTO;
import at.v3rtumnus.planman.dto.fitness.WeightLogDTO;
import at.v3rtumnus.planman.entity.fitness.FitnessMealLog;
import at.v3rtumnus.planman.entity.fitness.FitnessWeightLog;
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
    private final FitnessHealthService fitnessHealthService;
    private final FitnessNutritionAiService fitnessNutritionAiService;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
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

    @DeleteMapping(value = "/reset", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody ResponseEntity<Void> reset() {
        fitnessService.resetFitnessData(currentUsername());
        return ResponseEntity.ok().build();
    }
}
