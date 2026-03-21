package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.entity.fitness.ActivityLevel;
import at.v3rtumnus.planman.entity.fitness.BiologicalSex;
import at.v3rtumnus.planman.service.FitnessHealthService;
import at.v3rtumnus.planman.service.FitnessNutritionAiService;
import at.v3rtumnus.planman.service.FitnessService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDate;

@Controller
@RequestMapping("/fitness")
@Slf4j
@AllArgsConstructor
public class FitnessController {

    private final FitnessService fitnessService;
    private final FitnessHealthService fitnessHealthService;
    private final FitnessNutritionAiService fitnessNutritionAiService;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @GetMapping({"", "/", "/overview"})
    public ModelAndView overview() {
        String username = currentUsername();
        ModelAndView mav = new ModelAndView("fitness/overview");
        mav.addObject("weightHistory", fitnessHealthService.getWeightHistory(username));
        mav.addObject("dailyCalorieTarget", fitnessHealthService.recalculateDailyCalorieTarget(username));
        mav.addObject("todayMealLog", fitnessNutritionAiService.getMealLogDTO(username, LocalDate.now()));
        mav.addObject("nutritionWeeklySummary", fitnessNutritionAiService.getWeeklyNutritionSummary(username));
        mav.addObject("healthProfile", fitnessHealthService.getHealthProfile(username));
        return mav;
    }

    @GetMapping("/weight")
    public ModelAndView weight() {
        String username = currentUsername();
        ModelAndView mav = new ModelAndView("fitness/weight");
        mav.addObject("weightHistory", fitnessHealthService.getWeightHistory(username));
        mav.addObject("healthProfile", fitnessHealthService.getHealthProfile(username));
        mav.addObject("dailyCalorieTarget", fitnessHealthService.recalculateDailyCalorieTarget(username));
        mav.addObject("activityLevels", ActivityLevel.values());
        mav.addObject("biologicalSexValues", BiologicalSex.values());
        return mav;
    }

    @GetMapping("/nutrition")
    public ModelAndView nutrition(@RequestParam(required = false) String date) {
        String username = currentUsername();
        LocalDate logDate = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        ModelAndView mav = new ModelAndView("fitness/nutrition");
        mav.addObject("mealLog", fitnessNutritionAiService.getMealLogDTO(username, logDate));
        mav.addObject("logDate", logDate);
        return mav;
    }
}
