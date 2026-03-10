package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.fitness.AssessmentAnswerDTO;
import at.v3rtumnus.planman.dto.fitness.FitnessPlanDTO;
import at.v3rtumnus.planman.dto.fitness.PersonalRecordDTO;
import at.v3rtumnus.planman.entity.fitness.ActivityLevel;
import at.v3rtumnus.planman.entity.fitness.BiologicalSex;
import at.v3rtumnus.planman.entity.fitness.FitnessProfile;
import at.v3rtumnus.planman.entity.fitness.SessionStatus;
import at.v3rtumnus.planman.entity.fitness.SkipReason;
import at.v3rtumnus.planman.exception.FitnessAiException;
import at.v3rtumnus.planman.service.FitnessAiService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/fitness")
@Slf4j
@AllArgsConstructor
public class FitnessController {

    private final FitnessService fitnessService;
    private final FitnessAiService fitnessAiService;
    private final FitnessHealthService fitnessHealthService;
    private final FitnessNutritionAiService fitnessNutritionAiService;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @GetMapping("/assessment")
    public ModelAndView assessment() {
        FitnessProfile profile = fitnessService.getOrCreateFitnessProfile(currentUsername());
        if (profile.isAssessmentCompleted()) {
            return new ModelAndView("redirect:/fitness/overview");
        }
        return new ModelAndView("fitness/assessment");
    }

    @PostMapping("/assessment")
    public ModelAndView submitAssessment(@RequestParam Map<String, String> params) {
        String username = currentUsername();

        List<AssessmentAnswerDTO> answers = params.entrySet().stream()
                .filter(e -> !e.getKey().equals("_csrf"))
                .map(e -> new AssessmentAnswerDTO(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        fitnessService.saveAssessmentAnswers(username, answers);

        try {
            fitnessAiService.generateInitialPlan(username);
            fitnessService.markAssessmentCompleted(username);
            return new ModelAndView("redirect:/fitness/overview");
        } catch (FitnessAiException e) {
            log.warn("Initial plan generation failed for user {}: {}", username, e.getMessage());
            ModelAndView mav = new ModelAndView("fitness/assessment");
            mav.addObject("error", "KI-Plan-Generierung fehlgeschlagen. Bitte erneut versuchen.");
            return mav;
        }
    }

    @GetMapping("/overview")
    public ModelAndView overview() {
        String username = currentUsername();
        FitnessProfile profile = fitnessService.getOrCreateFitnessProfile(username);
        if (!profile.isAssessmentCompleted()) {
            return new ModelAndView("redirect:/fitness/assessment");
        }

        ModelAndView mav = new ModelAndView("fitness/overview");
        FitnessPlanDTO plan = fitnessService.getActivePlan(username);
        mav.addObject("plan", plan);
        mav.addObject("progress", fitnessService.getProgressStats(username));
        mav.addObject("readiness", fitnessService.getRecoveryReadiness(username));
        mav.addObject("todayActivities", fitnessService.getExternalActivitiesForDate(username, LocalDate.now()));
        mav.addObject("todayMealLog", fitnessNutritionAiService.getMealLogDTO(username, LocalDate.now()));
        var weightHistory = fitnessHealthService.getWeightHistory(username);
        mav.addObject("lastWeight", weightHistory.isEmpty() ? null : weightHistory.get(0));

        if (plan != null) {
            plan.getSessions().stream()
                    .filter(s -> s.getStatus() == null)
                    .findFirst()
                    .ifPresent(s -> mav.addObject("todaySession", s));
        }

        return mav;
    }

    @GetMapping("/plan")
    public ModelAndView plan() {
        String username = currentUsername();
        FitnessProfile profile = fitnessService.getOrCreateFitnessProfile(username);
        if (!profile.isAssessmentCompleted()) {
            return new ModelAndView("redirect:/fitness/assessment");
        }

        ModelAndView mav = new ModelAndView("fitness/plan");
        mav.addObject("plan", fitnessService.getActivePlan(username));
        mav.addObject("skipReasons", SkipReason.values());
        return mav;
    }

    @GetMapping("/session/{sessionId}/active")
    public ModelAndView sessionActive(@PathVariable Long sessionId) {
        ModelAndView mav = new ModelAndView("fitness/session-active");
        FitnessPlanDTO plan = fitnessService.getActivePlan(currentUsername());
        if (plan != null) {
            plan.getSessions().stream()
                    .filter(s -> s.getId().equals(sessionId))
                    .findFirst()
                    .ifPresentOrElse(
                            s -> mav.addObject("planSession", s),
                            () -> mav.setViewName("redirect:/fitness/plan")
                    );
        } else {
            mav.setViewName("redirect:/fitness/plan");
        }
        return mav;
    }

    @GetMapping("/session/{sessionId}")
    public ModelAndView sessionLog(@PathVariable Long sessionId) {
        ModelAndView mav = new ModelAndView("fitness/session-log");
        FitnessPlanDTO plan = fitnessService.getActivePlan(currentUsername());
        if (plan != null) {
            plan.getSessions().stream()
                    .filter(s -> s.getId().equals(sessionId))
                    .findFirst()
                    .ifPresentOrElse(
                            s -> mav.addObject("planSession", s),
                            () -> mav.setViewName("redirect:/fitness/plan")
                    );
        } else {
            mav.setViewName("redirect:/fitness/plan");
        }
        return mav;
    }

    @GetMapping("/history")
    public ModelAndView historyRedirect() {
        return new ModelAndView("redirect:/fitness/aktivitaeten");
    }

    @GetMapping("/aktivitaeten")
    public ModelAndView aktivitaeten() {
        ModelAndView mav = new ModelAndView("fitness/aktivitaeten");
        mav.addObject("sessionLogs", fitnessService.getSessionHistoryWithStats(currentUsername(), 0));
        return mav;
    }

    @GetMapping("/records")
    public ModelAndView recordsRedirect() {
        return new ModelAndView("redirect:/fitness/statistik");
    }

    @GetMapping("/statistik")
    public ModelAndView statistik() {
        String username = currentUsername();
        ModelAndView mav = new ModelAndView("fitness/statistik");
        mav.addObject("progress", fitnessService.getProgressStats(username));
        List<PersonalRecordDTO> records = fitnessService.getPersonalRecords(username);
        Map<String, List<PersonalRecordDTO>> byCategory = records.stream()
                .collect(Collectors.groupingBy(pr -> pr.getCategory().name(), LinkedHashMap::new, Collectors.toList()));
        mav.addObject("recordsByCategory", byCategory);
        mav.addObject("mealHistory", fitnessNutritionAiService.getMealHistoryWithTargets(username, 30));
        mav.addObject("nutritionWeeklySummary", fitnessNutritionAiService.getWeeklyNutritionSummary(username));
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

    @GetMapping("/exercises")
    public ModelAndView exercises() {
        ModelAndView mav = new ModelAndView("fitness/exercises");
        mav.addObject("exercises", fitnessService.getAllExercises());
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
