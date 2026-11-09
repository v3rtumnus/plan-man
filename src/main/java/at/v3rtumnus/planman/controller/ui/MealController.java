package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.health.MealForm;
import at.v3rtumnus.planman.dto.health.MealItemForm;
import at.v3rtumnus.planman.dto.health.MealQuestion;
import at.v3rtumnus.planman.dto.health.NutritionDay;
import at.v3rtumnus.planman.entity.health.MealSlot;
import at.v3rtumnus.planman.service.health.AiRequestException;
import at.v3rtumnus.planman.service.health.MealService;
import at.v3rtumnus.planman.entity.health.Meal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Meal capture (sentence/photo → model → review → save) and "Schnell wieder". */
@Controller
@RequestMapping("/health/meals")
@RequiredArgsConstructor
public class MealController {

    /** A shrunk 1024 px JPEG is ~150–300 KB as base64; anything far above is not ours. */
    static final int MAX_PHOTO_BASE64 = 3_000_000;

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN);

    private final MealService mealService;

    @GetMapping("/new")
    public ModelAndView capture(@RequestParam(name = "slot", required = false) MealSlot slot,
                                @RequestParam(name = "photo", defaultValue = "false") boolean photo,
                                @RequestParam(name = "date", required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        MealSlot selected = slot != null ? slot : mealService.slotNow();
        LocalDate day = day(date);
        ModelAndView mav = new ModelAndView("health/meal-capture");
        mav.addObject("slot", selected);
        mav.addObject("slots", MealSlot.values());
        mav.addObject("date", day);
        mav.addObject("pastDay", day.isBefore(mealService.today()) ? DAY_FORMAT.format(day) : null);
        mav.addObject("quickMeals", mealService.quickMeals(selected, day));
        mav.addObject("aiAvailable", mealService.recognitionAvailable());
        mav.addObject("openCamera", photo);
        return mav;
    }

    @PostMapping("/recognize")
    public ModelAndView recognize(@RequestParam("slot") MealSlot slot,
                                  @RequestParam(name = "description", required = false) String description,
                                  @RequestParam(name = "photo", required = false) String photo,
                                  @RequestParam(name = "date", required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        String photoData = stripDataUrl(photo);
        LocalDate day = day(date);
        try {
            if (photoData != null && photoData.length() > MAX_PHOTO_BASE64) {
                throw new AiRequestException("Das Foto ist zu groß. Bitte nochmal aufnehmen.");
            }
            MealService.Recognized recognized = mealService.recognize(slot, day, description, photoData);
            return review(recognized.form(), recognized.question(), photoData != null);
        } catch (AiRequestException e) {
            ModelAndView mav = capture(slot, false, day);
            mav.addObject("description", description);
            mav.addObject("errorMessage", e.getMessage());
            return mav;
        }
    }

    @GetMapping("/manual")
    public ModelAndView manual(@RequestParam(name = "slot", required = false) MealSlot slot,
                               @RequestParam(name = "description", required = false) String description,
                               @RequestParam(name = "date", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return review(mealService.manualForm(slot != null ? slot : mealService.slotNow(), day(date), description), null, false);
    }

    @GetMapping("/{id}/edit")
    public ModelAndView edit(@PathVariable("id") Long id, RedirectAttributes redirect) {
        return mealService.editForm(id)
                .map(form -> review(form, null, false))
                .orElseGet(() -> {
                    redirect.addFlashAttribute("errorMessage", "Mahlzeit nicht gefunden");
                    return new ModelAndView("redirect:/health");
                });
    }

    @PostMapping
    public ModelAndView save(@Valid @ModelAttribute("mealForm") MealForm form, BindingResult result,
                             RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                mealService.save(form);
                redirect.addFlashAttribute("successMessage", form.getTitle().trim() + " gespeichert");
                return new ModelAndView("redirect:" + HealthController.dayPath(form.getDate(), mealService.today()));
            } catch (IllegalArgumentException e) {
                result.reject("meal", e.getMessage());
            }
        }
        if (form.getItems().isEmpty()) {
            form.getItems().add(MealItemForm.empty());
        }
        ModelAndView mav = review(form, null, false);
        mav.addObject("errorMessage", result.getGlobalErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst().orElse("Bitte Name und Menge jeder Zutat prüfen"));
        return mav;
    }

    @PostMapping("/quick")
    public String quick(@RequestParam("slot") MealSlot slot,
                        @RequestParam(name = "savedMealId", required = false) Long savedMealId,
                        @RequestParam(name = "mealId", required = false) Long mealId,
                        @RequestParam(name = "date", required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        RedirectAttributes redirect) {
        LocalDate day = day(date);
        try {
            String title = savedMealId != null
                    ? mealService.logSaved(savedMealId, slot, day).getTitle()
                    : mealService.logRepeat(mealId, slot, day).getTitle();
            redirect.addFlashAttribute("successMessage", title + " eingetragen");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + HealthController.dayPath(day, mealService.today());
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id, RedirectAttributes redirect) {
        LocalDate day = mealService.delete(id).orElse(null);
        redirect.addFlashAttribute("successMessage", "Mahlzeit gelöscht");
        return "redirect:" + HealthController.dayPath(day, mealService.today());
    }

    /** The day a capture is for: today unless a past day was given. */
    private LocalDate day(LocalDate date) {
        LocalDate today = mealService.today();
        return date == null || date.isAfter(today) ? today : date;
    }

    @PostMapping("/saved/{id}/delete")
    public String deleteSaved(@PathVariable("id") Long id, @RequestParam("slot") MealSlot slot,
                              RedirectAttributes redirect) {
        mealService.deleteSaved(id);
        redirect.addFlashAttribute("successMessage", "Aus „Schnell wieder“ entfernt");
        return "redirect:/health/meals/new?slot=" + slot;
    }

    private ModelAndView review(MealForm form, MealQuestion question, boolean hadPhoto) {
        if (form.getDate() == null) {
            form.setDate(mealService.today());
        }
        NutritionDay day = mealService.nutritionDay(form.getDate());
        ModelAndView mav = new ModelAndView("health/meal-review");
        mav.addObject("mealForm", form);
        mav.addObject("question", question);
        mav.addObject("slots", MealSlot.values());
        mav.addObject("hadPhoto", hadPhoto);
        // what is left after this meal: the day without the meal being edited
        int editedKcal = 0;
        BigDecimal editedProtein = BigDecimal.ZERO;
        if (form.getMealId() != null) {
            for (Meal meal : day.meals()) {
                if (meal.getId().equals(form.getMealId())) {
                    editedKcal = meal.getKcal();
                    editedProtein = meal.getProteinG();
                }
            }
        }
        mav.addObject("kcalTarget", day.kcalTarget());
        mav.addObject("proteinTarget", day.proteinTargetG());
        mav.addObject("kcalBefore", day.kcalEaten() - editedKcal);
        mav.addObject("proteinBefore", day.proteinEatenG().subtract(editedProtein));
        return mav;
    }

    private static String stripDataUrl(String photo) {
        if (photo == null || photo.isBlank()) {
            return null;
        }
        int comma = photo.indexOf(',');
        return photo.startsWith("data:") && comma > 0 ? photo.substring(comma + 1) : photo;
    }
}
