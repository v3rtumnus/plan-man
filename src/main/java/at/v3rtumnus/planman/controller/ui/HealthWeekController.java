package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.conf.CurrentUser;
import at.v3rtumnus.planman.dto.health.WeekSummary;
import at.v3rtumnus.planman.entity.health.FeedbackStatus;
import at.v3rtumnus.planman.entity.health.ReviewDecision;
import at.v3rtumnus.planman.entity.health.WeeklyReview;
import at.v3rtumnus.planman.service.health.HealthHistoryService;
import at.v3rtumnus.planman.service.health.WeeklyReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/** Woche: the days of one week against their targets, and the weekly review with its goal check. */
@Controller
@RequestMapping("/health/week")
@RequiredArgsConstructor
public class HealthWeekController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN);

    private final HealthHistoryService historyService;
    private final WeeklyReviewService reviewService;

    @GetMapping
    public ModelAndView week(@RequestParam(name = "start", required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start) {
        String username = CurrentUser.require();
        LocalDate today = historyService.today();
        LocalDate thisWeek = HealthHistoryService.mondayOf(today);
        LocalDate monday = start == null || start.isAfter(today) ? thisWeek : HealthHistoryService.mondayOf(start);
        WeekSummary week = historyService.week(username, monday);
        Optional<WeeklyReview> review = reviewService.find(username, monday);

        ModelAndView mav = new ModelAndView("health/week");
        mav.addObject("week", week);
        mav.addObject("weekLabel", DAY.format(monday) + " – " + DAY.format(monday.plusDays(6)));
        mav.addObject("isCurrent", monday.equals(thisWeek));
        mav.addObject("prevStart", monday.minusWeeks(1));
        mav.addObject("nextStart", monday.equals(thisWeek) ? null : monday.plusWeeks(1));
        mav.addObject("today", today);
        mav.addObject("review", review.orElse(null));
        // the review of last week can be started by hand, e.g. before the Monday job or after a failure
        mav.addObject("canCreateReview", monday.equals(reviewService.lastWeekStart()) && reviewService.aiAvailable()
                && review.map(r -> r.getFeedbackStatus() != FeedbackStatus.DONE).orElse(true));
        return mav;
    }

    @PostMapping("/review")
    public String createReview(@RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                               RedirectAttributes redirect) {
        LocalDate monday = reviewService.lastWeekStart();
        if (!monday.equals(start)) {
            redirect.addFlashAttribute("errorMessage", "Nur die letzte Woche kann jetzt ausgewertet werden");
            return "redirect:/health/week?start=" + start;
        }
        Optional<WeeklyReview> review = reviewService.review(CurrentUser.require(), monday);
        if (review.isEmpty()) {
            redirect.addFlashAttribute("errorMessage", "Für diese Woche gibt es kein Ziel");
        } else if (review.get().getFeedbackStatus() == FeedbackStatus.PENDING) {
            redirect.addFlashAttribute("errorMessage", "Der Rückblick konnte nicht erstellt werden: " + review.get().getError());
        } else if (review.get().getFeedbackStatus() == FeedbackStatus.DONE) {
            redirect.addFlashAttribute("successMessage", "Rückblick erstellt");
        } else {
            redirect.addFlashAttribute("successMessage", "Zahlen gespeichert, ohne Text (Grund unten)");
        }
        return "redirect:/health/week?start=" + monday;
    }

    @PostMapping("/review/{id}/decision")
    public String decide(@PathVariable("id") Long id, @RequestParam("decision") ReviewDecision decision,
                         RedirectAttributes redirect) {
        String username = CurrentUser.require();
        Optional<WeeklyReview> review = reviewService.find(username, id);
        if (review.isEmpty() || decision == ReviewDecision.ACCEPTED) {
            redirect.addFlashAttribute("errorMessage", "Rückblick nicht gefunden");
            return "redirect:/health/week";
        }
        reviewService.decide(username, id, decision);
        redirect.addFlashAttribute("successMessage", decision == ReviewDecision.TALK
                ? "Gemerkt: in Claude Code „health review“ sagen" : "Gemerkt: Ziel bleibt");
        return "redirect:/health/week?start=" + review.get().getWeekStart();
    }
}
