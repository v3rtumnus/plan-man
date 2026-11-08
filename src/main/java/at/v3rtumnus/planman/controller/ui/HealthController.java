package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.health.GoalForm;
import at.v3rtumnus.planman.dto.health.MeasurementForm;
import at.v3rtumnus.planman.dto.health.MeasurementStatus;
import at.v3rtumnus.planman.dto.health.WeightTrend;
import at.v3rtumnus.planman.entity.health.HealthGoal;
import at.v3rtumnus.planman.entity.health.MeasurementType;
import at.v3rtumnus.planman.service.health.HealthService;
import at.v3rtumnus.planman.service.health.MealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Controller
@RequestMapping("/health")
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN);
    private static final Set<String> RETURN_PATHS = Set.of("/health", "/health/goal");

    private final HealthService healthService;
    private final MealService mealService;

    @GetMapping
    public ModelAndView today() {
        LocalDate today = healthService.today();
        Optional<HealthGoal> goal = healthService.currentGoal();
        WeightTrend trend = healthService.weightTrend();

        ModelAndView mav = new ModelAndView("health/today");
        mav.addObject("todayLabel", DAY_FORMAT.format(today));
        mav.addObject("today", today);
        mav.addObject("day", healthService.day(today));
        mav.addObject("nutrition", mealService.nutritionDay(today));
        mav.addObject("slotNow", mealService.slotNow());
        mav.addObject("goal", goal.orElse(null));
        mav.addObject("pause", healthService.activePause().orElse(null));
        mav.addObject("trend", trend);
        mav.addObject("progress", goal.flatMap(g -> healthService.goalProgress(g, trend)).orElse(null));
        mav.addObject("weightToday", healthService.measurementOn(MeasurementType.WEIGHT, today)
                .map(m -> m.getMeasuredValue()).orElse(null));
        mav.addObject("weightPrefill", trend.latestKg());
        mav.addObject("dueMeasurements", List.of(MeasurementType.WAIST, MeasurementType.PUSH_UPS).stream()
                .map(type -> healthService.status(type, goal))
                .filter(MeasurementStatus::due)
                .toList());
        return mav;
    }

    @GetMapping("/goal")
    public ModelAndView goal(@RequestParam(name = "edit", defaultValue = "false") boolean edit) {
        Optional<HealthGoal> goal = healthService.currentGoal();
        WeightTrend trend = healthService.weightTrend();

        ModelAndView mav = new ModelAndView("health/goal");
        mav.addObject("goal", goal.orElse(null));
        mav.addObject("trend", trend);
        mav.addObject("progress", goal.flatMap(g -> healthService.goalProgress(g, trend)).orElse(null));
        mav.addObject("waist", healthService.status(MeasurementType.WAIST, goal));
        mav.addObject("pushUps", healthService.status(MeasurementType.PUSH_UPS, goal));
        mav.addObject("history", healthService.goalHistory());
        mav.addObject("pause", healthService.activePause().orElse(null));
        mav.addObject("today", healthService.today());
        if (!mav.getModel().containsKey("goalForm")) {
            mav.addObject("goalForm", healthService.goalFormForChange());
        }
        mav.addObject("editGoal", edit || goal.isEmpty());
        return mav;
    }

    @PostMapping("/goal")
    public ModelAndView changeGoal(@Valid @ModelAttribute("goalForm") GoalForm form, BindingResult result,
                                   RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                healthService.changeGoal(form);
                redirect.addFlashAttribute("successMessage", "Neues Ziel gespeichert");
                return new ModelAndView("redirect:/health/goal");
            } catch (IllegalArgumentException e) {
                result.reject("goal", e.getMessage());
            }
        }
        ModelAndView mav = goal(true);
        mav.addObject("goalForm", form);
        mav.addObject("errorMessage", result.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst().orElse("Bitte die Eingaben prüfen"));
        mav.addObject("editGoal", true);
        return mav;
    }

    @PostMapping("/pause")
    public String pause(@RequestParam("active") boolean active,
                        @RequestParam(name = "reason", required = false) String reason,
                        RedirectAttributes redirect) {
        if (active) {
            healthService.startPause(reason);
            redirect.addFlashAttribute("successMessage", "Pause ist aktiv");
        } else {
            healthService.endPause();
            redirect.addFlashAttribute("successMessage", "Pause beendet");
        }
        return "redirect:/health/goal";
    }

    @GetMapping("/measure")
    public ModelAndView measureForm(@RequestParam(name = "type", defaultValue = "WEIGHT") MeasurementType type) {
        LocalDate today = healthService.today();
        BigDecimal prefill = healthService.measurementOn(type, today)
                .or(() -> healthService.latest(type))
                .map(m -> m.getMeasuredValue())
                .orElse(null);

        ModelAndView mav = new ModelAndView("health/measure");
        mav.addObject("type", type);
        mav.addObject("types", MeasurementType.values());
        mav.addObject("form", new MeasurementForm(type, today, prefill));
        mav.addObject("today", today);
        mav.addObject("returnTo", type == MeasurementType.WEIGHT ? "/health" : "/health/goal");
        return mav;
    }

    @PostMapping("/measurements")
    public String saveMeasurement(@Valid @ModelAttribute MeasurementForm form, BindingResult result,
                                  @RequestParam(name = "returnTo", defaultValue = "/health") String returnTo,
                                  RedirectAttributes redirect) {
        String target = RETURN_PATHS.contains(returnTo) ? returnTo : "/health";
        if (result.hasErrors()) {
            redirect.addFlashAttribute("errorMessage", "Bitte Datum und Wert angeben");
            return "redirect:" + target;
        }
        try {
            healthService.saveMeasurement(form.getType(), form.getDate(), form.getValue());
            redirect.addFlashAttribute("successMessage", form.getType().getLabel() + " gespeichert");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + target;
    }
}
