package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.health.DaySummary;
import at.v3rtumnus.planman.dto.health.WeekSummary;
import at.v3rtumnus.planman.entity.health.*;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import at.v3rtumnus.planman.service.health.HealthHistoryService;
import at.v3rtumnus.planman.service.health.WeeklyReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthWeekController.class)
@WithMockUser
class HealthWeekControllerTest {

    /** Tuesday; this week starts 2026-11-09, last week 2026-11-02. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final LocalDate THIS_WEEK = LocalDate.of(2026, 11, 9);
    private static final LocalDate LAST_WEEK = LocalDate.of(2026, 11, 2);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthHistoryService historyService;
    @MockitoBean
    private WeeklyReviewService reviewService;
    @MockitoBean
    private PlanManUserDetailsService userDetailsService;
    @MockitoBean
    private DataSource dataSource;
    @MockitoBean
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        when(historyService.today()).thenReturn(TODAY);
        when(reviewService.lastWeekStart()).thenReturn(LAST_WEEK);
        when(reviewService.aiAvailable()).thenReturn(true);
        when(historyService.week(eq("user"), any())).thenAnswer(inv -> week(inv.getArgument(1)));
    }

    private static WeekSummary week(LocalDate monday) {
        List<DaySummary> days = new ArrayList<>();
        Workout freeletics = new Workout();
        freeletics.setType(WorkoutType.FREELETICS);
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            boolean logged = i < 2;
            days.add(new DaySummary(d, 2000, i == 0 ? 250 : 0, 160, logged ? (i == 0 ? 2400 : 1800) : 0,
                    logged ? new BigDecimal(i == 0 ? "165" : "120") : BigDecimal.ZERO, logged ? 3 : 0,
                    i == 1 ? new BigDecimal("82.4") : null, i == 3, i == 0 ? List.of(freeletics) : List.of()));
        }
        return new WeekSummary(monday, days, 2, 1, 2100, 2125, new BigDecimal("142.5"), 160, 1, 1, false, 250,
                new BigDecimal("82.4"), new BigDecimal("-0.3"), null, null, null, null);
    }

    private static WeeklyReview review(FeedbackStatus status) {
        WeeklyReview review = new WeeklyReview();
        review.setId(5L);
        review.setWeekStart(LAST_WEEK);
        review.setFeedbackStatus(status);
        return review;
    }

    @Test
    void currentWeek_showsDaysTilesAndWhenTheReviewComes() throws Exception {
        mockMvc.perform(get("/health/week"))
                .andExpect(status().isOk())
                .andExpect(view().name("health/week"))
                .andExpect(content().string(containsString("9. Nov. – 15. Nov.")))
                .andExpect(content().string(containsString("2.100")))
                .andExpect(content().string(containsString("Ziel Ø 2.125")))
                .andExpect(content().string(containsString("1 von 2")))
                .andExpect(content().string(containsString("Freeletics 1×")))
                .andExpect(content().string(containsString("-0,3 kg zur Vorwoche")))
                .andExpect(content().string(containsString(" / 2.250")))
                .andExpect(content().string(containsString("health-bar-over")))
                .andExpect(content().string(containsString("Pause")))
                .andExpect(content().string(containsString("/health?date=2026-11-09")))
                .andExpect(content().string(not(containsString("/health?date=2026-11-11"))))
                .andExpect(content().string(containsString("kommt am Montag früh")))
                .andExpect(content().string(not(containsString("Rückblick jetzt erstellen"))));
    }

    @Test
    void lastWeek_withoutReview_offersToCreateIt() throws Exception {
        mockMvc.perform(get("/health/week").param("start", "2026-11-04"))
                .andExpect(content().string(containsString("2. Nov. – 8. Nov.")))
                .andExpect(content().string(containsString("Rückblick jetzt erstellen")))
                .andExpect(content().string(containsString("/health/week?start=2026-11-09")));
    }

    @Test
    void doneReview_showsFeedbackAndGoalCheckWithProposal() throws Exception {
        WeeklyReview review = review(FeedbackStatus.DONE);
        review.setHeadline("Protein fehlt an 4 Tagen");
        review.setFeedback("Gute Woche.\nMehr Protein am Morgen.");
        review.setModel("claude-opus-5-5");
        review.setFeedbackAt(LocalDateTime.of(2026, 11, 9, 5, 31));
        review.setGoalCheck(true);
        review.setGoalStatus(GoalStatus.STALLED);
        review.setGoalReason("Trend seit 3 Wochen flach.");
        review.setProposedKcalTarget(1850);
        review.setProposedTargetDate(LocalDate.of(2027, 4, 30));
        review.setProposalReason("Kleiner Schritt.");
        when(reviewService.find("user", LAST_WEEK)).thenReturn(Optional.of(review));

        mockMvc.perform(get("/health/week").param("start", LAST_WEEK.toString()))
                .andExpect(content().string(containsString("Protein fehlt an 4 Tagen")))
                .andExpect(content().string(containsString("Mehr Protein am Morgen.")))
                .andExpect(content().string(containsString("Stagniert")))
                .andExpect(content().string(containsString("Kalorienziel 1.850 kcal")))
                .andExpect(content().string(containsString("Zieldatum 30.04.2027")))
                .andExpect(content().string(containsString("/health/goal?review=5")))
                .andExpect(content().string(containsString("Gespräch führen")))
                .andExpect(content().string(not(containsString("Rückblick jetzt erstellen"))));

        review.setDecision(ReviewDecision.TALK);
        mockMvc.perform(get("/health/week").param("start", LAST_WEEK.toString()))
                .andExpect(content().string(containsString("health review")))
                .andExpect(content().string(not(containsString("Vorschlag übernehmen"))));
        review.setDecision(ReviewDecision.KEEP);
        mockMvc.perform(get("/health/week").param("start", LAST_WEEK.toString()))
                .andExpect(content().string(containsString("Ziel bleibt")));
        review.setDecision(ReviewDecision.ACCEPTED);
        mockMvc.perform(get("/health/week").param("start", LAST_WEEK.toString()))
                .andExpect(content().string(containsString("als neues Ziel übernommen")));
    }

    @Test
    void reviewWithoutText_explainsWhy() throws Exception {
        WeeklyReview review = review(FeedbackStatus.TOO_FEW_DAYS);
        when(reviewService.find("user", LAST_WEEK)).thenReturn(Optional.of(review));
        mockMvc.perform(get("/health/week").param("start", LAST_WEEK.toString()))
                .andExpect(content().string(containsString("Weniger als 3 Tage")));

        review.setFeedbackStatus(FeedbackStatus.PAUSED);
        mockMvc.perform(get("/health/week").param("start", LAST_WEEK.toString()))
                .andExpect(content().string(containsString("Pause in dieser Woche")));

        review.setFeedbackStatus(FeedbackStatus.SKIPPED);
        mockMvc.perform(get("/health/week").param("start", LAST_WEEK.toString()))
                .andExpect(content().string(containsString("Nur die Zahlen")));

        review.setFeedbackStatus(FeedbackStatus.PENDING);
        review.setError("Guthaben aufgebraucht");
        mockMvc.perform(get("/health/week").param("start", LAST_WEEK.toString()))
                .andExpect(content().string(containsString("(Guthaben aufgebraucht)")))
                .andExpect(content().string(containsString("Rückblick jetzt erstellen")));

        mockMvc.perform(get("/health/week").param("start", "2026-10-19"))
                .andExpect(content().string(containsString("keinen Rückblick")));
    }

    @Test
    void createReview_onlyForLastWeek() throws Exception {
        when(reviewService.review("user", LAST_WEEK)).thenReturn(Optional.of(review(FeedbackStatus.DONE)));
        mockMvc.perform(post("/health/week/review").param("start", LAST_WEEK.toString()))
                .andExpect(redirectedUrl("/health/week?start=" + LAST_WEEK))
                .andExpect(flash().attribute("successMessage", "Rückblick erstellt"));

        WeeklyReview failed = review(FeedbackStatus.PENDING);
        failed.setError("nicht erreichbar");
        when(reviewService.review("user", LAST_WEEK)).thenReturn(Optional.of(failed));
        mockMvc.perform(post("/health/week/review").param("start", LAST_WEEK.toString()))
                .andExpect(flash().attribute("errorMessage", "Der Rückblick konnte nicht erstellt werden: nicht erreichbar"));

        when(reviewService.review("user", LAST_WEEK)).thenReturn(Optional.of(review(FeedbackStatus.TOO_FEW_DAYS)));
        mockMvc.perform(post("/health/week/review").param("start", LAST_WEEK.toString()))
                .andExpect(flash().attribute("successMessage", "Zahlen gespeichert, ohne Text (Grund unten)"));

        when(reviewService.review("user", LAST_WEEK)).thenReturn(Optional.empty());
        mockMvc.perform(post("/health/week/review").param("start", LAST_WEEK.toString()))
                .andExpect(flash().attribute("errorMessage", "Für diese Woche gibt es kein Ziel"));

        mockMvc.perform(post("/health/week/review").param("start", "2026-10-26"))
                .andExpect(redirectedUrl("/health/week?start=2026-10-26"));
        verify(reviewService, never()).review("user", LocalDate.of(2026, 10, 26));
    }

    @Test
    void decision_talkOrKeep() throws Exception {
        when(reviewService.find("user", 5L)).thenReturn(Optional.of(review(FeedbackStatus.DONE)));

        mockMvc.perform(post("/health/week/review/5/decision").param("decision", "TALK"))
                .andExpect(redirectedUrl("/health/week?start=" + LAST_WEEK))
                .andExpect(flash().attribute("successMessage", "Gemerkt: in Claude Code „health review“ sagen"));
        verify(reviewService).decide("user", 5L, ReviewDecision.TALK);

        mockMvc.perform(post("/health/week/review/5/decision").param("decision", "KEEP"))
                .andExpect(flash().attribute("successMessage", "Gemerkt: Ziel bleibt"));

        // accepting goes through the goal form, and unknown reviews are refused
        mockMvc.perform(post("/health/week/review/5/decision").param("decision", "ACCEPTED"))
                .andExpect(redirectedUrl("/health/week"));
        mockMvc.perform(post("/health/week/review/6/decision").param("decision", "KEEP"))
                .andExpect(redirectedUrl("/health/week"));
        verify(reviewService, never()).decide("user", 5L, ReviewDecision.ACCEPTED);
        verify(reviewService, never()).decide(eq("user"), eq(6L), any());
    }
}
