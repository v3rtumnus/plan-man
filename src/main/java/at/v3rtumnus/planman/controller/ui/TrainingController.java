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

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
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

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN);

    private final TrainingService trainingService;

    @GetMapping
    public ModelAndView training(@RequestParam(name = "date", required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = day(date);
        ModelAndView mav = new ModelAndView("health/training");
        mav.addObject("week", trainingService.week());
        mav.addObject("recent", trainingService.recent());
        mav.addObject("date", day);
        mav.addObject("pastDay", day.isBefore(trainingService.today()) ? DAY_FORMAT.format(day) : null);
        mav.addObject("football", trainingService.footballState(day));
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
                             @RequestParam(name = "date", required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             RedirectAttributes redirect) {
        LocalDate day = day(date);
        return logged(redirect, day, () -> trainingService.logFreeletics(day, minutes, intensity));
    }

    @PostMapping("/activity")
    public String activity(@RequestParam("type") WorkoutType type, @RequestParam("minutes") int minutes,
                           @RequestParam(name = "date", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           RedirectAttributes redirect) {
        LocalDate day = day(date);
        return logged(redirect, day, () -> trainingService.logActivity(day, type, minutes));
    }

    @PostMapping("/other")
    public String other(@RequestParam("description") String description,
                        @RequestParam(name = "minutes", required = false) Integer minutes,
                        @RequestParam(name = "date", required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        RedirectAttributes redirect) {
        LocalDate day = day(date);
        try {
            Workout workout = trainingService.logOther(day, description, minutes);
            redirect.addFlashAttribute("successMessage", workout.label() + " eingetragen: +" + workout.getBonusKcal()
                    + " kcal fürs Tagesziel");
        } catch (AiRequestException | IllegalArgumentException e) {
            // keep the sentence, so only the missing part (usually the duration) has to be added
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            redirect.addFlashAttribute("otherDescription", description);
        }
        return "redirect:" + trainingPath(day);
    }

    @PostMapping("/football")
    public String football(@RequestParam("answer") String answer,
                           @RequestParam(name = "returnTo", defaultValue = "/health/training") String returnTo,
                           @RequestParam(name = "date", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           RedirectAttributes redirect) {
        LocalDate day = day(date);
        switch (answer) {
            case "played" -> {
                Workout workout = trainingService.logFootball(day);
                redirect.addFlashAttribute("successMessage", "Fußball eingetragen"
                        + (workout.getBonusKcal() > 0 ? ": +" + workout.getBonusKcal() + " kcal" : ""));
            }
            case "cancelled" -> trainingService.cancelFootball(day);
            default -> trainingService.resetFootball(day);
        }
        if (day.isBefore(trainingService.today())) {
            return "redirect:" + trainingPath(day);
        }
        return "redirect:" + (RETURN_PATHS.contains(returnTo) ? returnTo : "/health/training");
    }

    /** {@code returnTo=day} goes back to the day on Heute (past days list their training there). */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id,
                         @RequestParam(name = "date", required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam(name = "returnTo", required = false) String returnTo,
                         RedirectAttributes redirect) {
        trainingService.delete(id);
        redirect.addFlashAttribute("successMessage", "Training gelöscht");
        if ("day".equals(returnTo)) {
            return "redirect:" + HealthController.dayPath(date, trainingService.today());
        }
        return "redirect:" + trainingPath(day(date));
    }

    private String logged(RedirectAttributes redirect, LocalDate day, java.util.function.Supplier<Workout> action) {
        try {
            Workout workout = action.get();
            redirect.addFlashAttribute("successMessage", workout.label() + " eingetragen: +" + workout.getBonusKcal()
                    + " kcal fürs Tagesziel");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + trainingPath(day);
    }

    private LocalDate day(LocalDate date) {
        LocalDate today = trainingService.today();
        return date == null || date.isAfter(today) ? today : date;
    }

    private String trainingPath(LocalDate day) {
        return day.isBefore(trainingService.today()) ? "/health/training?date=" + day : "/health/training";
    }
}
