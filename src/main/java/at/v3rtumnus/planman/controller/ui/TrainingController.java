package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.entity.health.Intensity;
import at.v3rtumnus.planman.entity.health.Workout;
import at.v3rtumnus.planman.entity.health.WorkoutType;
import at.v3rtumnus.planman.service.health.AiRequestException;
import at.v3rtumnus.planman.service.health.TrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Training: Freeletics in one tap, football and other activities. */
@Controller
@RequestMapping("/health/training")
@RequiredArgsConstructor
public class TrainingController {

    static final List<Integer> FREELETICS_MINUTES = List.of(20, 30, 40, 50);
    static final List<Integer> ACTIVITY_MINUTES = List.of(20, 30, 45, 60, 90);
    private static final Set<String> RETURN_PATHS = Set.of("/health", "/health/training");

    private final TrainingService trainingService;

    @GetMapping
    public ModelAndView training() {
        ModelAndView mav = new ModelAndView("health/training");
        mav.addObject("week", trainingService.week());
        mav.addObject("recent", trainingService.recent());
        mav.addObject("football", trainingService.footballState(trainingService.today()));
        mav.addObject("footballMinutes", trainingService.footballMinutes());
        mav.addObject("freeleticsMinutes", FREELETICS_MINUTES);
        mav.addObject("activityMinutes", ACTIVITY_MINUTES);
        mav.addObject("intensities", Intensity.values());
        mav.addObject("bodyKg", trainingService.bodyWeightKg());
        mav.addObject("mets", Map.of("LIGHT", WorkoutType.FREELETICS.met(Intensity.LIGHT),
                "MEDIUM", WorkoutType.FREELETICS.met(Intensity.MEDIUM),
                "HARD", WorkoutType.FREELETICS.met(Intensity.HARD)));
        mav.addObject("aiAvailable", trainingService.aiAvailable());
        return mav;
    }

    @PostMapping("/freeletics")
    public String freeletics(@RequestParam("minutes") int minutes, @RequestParam("intensity") Intensity intensity,
                             RedirectAttributes redirect) {
        return logged(redirect, () -> trainingService.logFreeletics(minutes, intensity));
    }

    @PostMapping("/activity")
    public String activity(@RequestParam("type") WorkoutType type, @RequestParam("minutes") int minutes,
                           RedirectAttributes redirect) {
        return logged(redirect, () -> trainingService.logActivity(type, minutes));
    }

    @PostMapping("/other")
    public String other(@RequestParam("description") String description,
                        @RequestParam(name = "minutes", required = false) Integer minutes,
                        RedirectAttributes redirect) {
        try {
            Workout workout = trainingService.logOther(description, minutes);
            redirect.addFlashAttribute("successMessage", workout.label() + " eingetragen: +" + workout.getBonusKcal()
                    + " kcal fürs Tagesziel");
        } catch (AiRequestException | IllegalArgumentException e) {
            // keep the sentence, so only the missing part (usually the duration) has to be added
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            redirect.addFlashAttribute("otherDescription", description);
        }
        return "redirect:/health/training";
    }

    @PostMapping("/football")
    public String football(@RequestParam("answer") String answer,
                           @RequestParam(name = "returnTo", defaultValue = "/health/training") String returnTo,
                           RedirectAttributes redirect) {
        switch (answer) {
            case "played" -> {
                Workout workout = trainingService.logFootball();
                redirect.addFlashAttribute("successMessage", "Fußball eingetragen"
                        + (workout.getBonusKcal() > 0 ? ": +" + workout.getBonusKcal() + " kcal" : ""));
            }
            case "cancelled" -> trainingService.cancelFootball();
            default -> trainingService.resetFootball();
        }
        return "redirect:" + (RETURN_PATHS.contains(returnTo) ? returnTo : "/health/training");
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id, RedirectAttributes redirect) {
        trainingService.delete(id);
        redirect.addFlashAttribute("successMessage", "Training gelöscht");
        return "redirect:/health/training";
    }

    private String logged(RedirectAttributes redirect, java.util.function.Supplier<Workout> action) {
        try {
            Workout workout = action.get();
            redirect.addFlashAttribute("successMessage", workout.label() + " eingetragen: +" + workout.getBonusKcal()
                    + " kcal fürs Tagesziel");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/health/training";
    }
}
