package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dto.health.DaySummary;
import at.v3rtumnus.planman.dto.health.WeekSummary;
import at.v3rtumnus.planman.dto.health.ai.ReviewDraft;
import at.v3rtumnus.planman.entity.health.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@WireMockTest
class AnthropicWeeklyReviewerTest {

    static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    static HealthGoal goal() {
        HealthGoal goal = new HealthGoal();
        goal.setTitle("Fett verlieren + Kraft");
        goal.setValidFrom(LocalDate.of(2026, 10, 5));
        goal.setKcalTarget(2000);
        goal.setProteinTargetG(160);
        goal.setFootballBonusKcal(250);
        goal.setStartWeightKg(new BigDecimal("83.0"));
        goal.setTargetWeightKg(new BigDecimal("77.0"));
        goal.setTargetDate(LocalDate.of(2027, 3, 31));
        goal.setPushUpsTarget(25);
        goal.setCreatedAt(LocalDateTime.now());
        return goal;
    }

    static WeekSummary week(LocalDate monday, BigDecimal trend) {
        Workout freeletics = new Workout();
        freeletics.setType(WorkoutType.FREELETICS);
        freeletics.setIntensity(Intensity.HARD);
        freeletics.setDurationMin(30);
        freeletics.setBonusKcal(330);
        Workout football = new Workout();
        football.setType(WorkoutType.FOOTBALL);
        football.setDurationMin(70);
        football.setBonusKcal(250);
        List<DaySummary> days = new ArrayList<>();
        days.add(new DaySummary(monday, 2000, 330, 160, 2100, new BigDecimal("161.4"), 4, new BigDecimal("82.4"), false, List.of(freeletics)));
        days.add(new DaySummary(monday.plusDays(1), 2000, 250, 160, 2400, new BigDecimal("120"), 3, null, false, List.of(football)));
        days.add(new DaySummary(monday.plusDays(2), null, 0, null, 0, BigDecimal.ZERO, 0, null, false, List.of()));
        days.add(new DaySummary(monday.plusDays(3), 2000, 0, 160, 0, BigDecimal.ZERO, 0, null, true, List.of()));
        return new WeekSummary(monday, days, 2, 1, 2250, 2290, new BigDecimal("140.7"), 160, 1, 1, true, 580,
                trend, trend == null ? null : new BigDecimal("-0.4"), trend == null ? null : new BigDecimal("-0.35"),
                null, null, null);
    }

    @Test
    void input_describesGoalDaysAndWeeks() {
        String input = AnthropicWeeklyReviewer.input(goal(), week(MONDAY, new BigDecimal("82.45")),
                List.of(week(MONDAY.minusWeeks(1), null)), List.of("12.10. 97", "02.11. 95,5"), List.of(), true);

        assertThat(input)
                .contains("Ziel: Fett verlieren + Kraft, seit 05.10.2026. Start 83,0 kg → Ziel 77,0 kg bis 31.03.2027.")
                .contains("Tagesziel 2000 kcal, Fußball +250 kcal, Protein 160 g, Liegestütze-Ziel 25.")
                .contains("Nötige Rate ab jetzt: -0,27 kg/Woche.")
                .contains("Ziel-Check: ja")
                .contains("Woche 02.11.–08.11.2026:")
                .contains("- Mo 02.11.: 2100 / 2330 kcal, 161 g Protein, Gewicht 82,4 kg, Training: Freeletics 30 min hart (+330 kcal)")
                .contains("- Di 03.11.: 2400 / 2250 kcal, 120 g Protein, Training: Fußball 70 min (+250 kcal)")
                .contains("- Mi 04.11.: nichts eingetragen")
                .contains("- Do 05.11.: Pause")
                .contains("Zusammen: 2 Tage eingetragen, 1 Tage Pause, Ø 2250 / 2290 kcal, Ø 141 g Protein (Ziel an 1 Tagen erreicht), "
                        + "Freeletics 1×, Fußball ja, Trend 82,5 kg (-0,40 zur Vorwoche), Rate -0,35 kg/Woche")
                .contains("Vorwochen (neueste zuerst):\n- 26.10.–01.11.: 2 Tage eingetragen")
                .contains("Taille (cm): 12.10. 97, 02.11. 95,5")
                .contains("Liegestütze am Stück: noch nicht getestet");

        String plain = AnthropicWeeklyReviewer.input(goal(), week(MONDAY, null), List.of(), List.of(), List.of("06.11. 14"), false);
        assertThat(plain).contains("Ziel-Check: nein").doesNotContain("Nötige Rate").doesNotContain("Vorwochen")
                .contains("Taille (cm): noch nicht gemessen").contains("Liegestütze am Stück: 06.11. 14");
    }

    @Test
    void requiredRate_fromTrendToTarget() {
        HealthGoal goal = goal();
        assertThat(AnthropicWeeklyReviewer.requiredRate(goal, week(MONDAY, new BigDecimal("82.0")))).isEqualByComparingTo("-0.24");
        assertThat(AnthropicWeeklyReviewer.requiredRate(goal, week(MONDAY, null))).isNull();
        goal.setTargetDate(MONDAY.plusDays(3));
        assertThat(AnthropicWeeklyReviewer.requiredRate(goal, week(MONDAY, new BigDecimal("82.0")))).isNull();
    }

    @Test
    void measurement_germanDecimals() {
        assertThat(AnthropicWeeklyReviewer.measurement(MONDAY, new BigDecimal("95.50"))).isEqualTo("02.11. 95,5");
        assertThat(AnthropicWeeklyReviewer.measurement(MONDAY, new BigDecimal("14.00"))).isEqualTo("02.11. 14");
    }

    @Test
    void review_callsOpusWithMediumEffortAndCountsCost(WireMockRuntimeInfo wm) {
        String draft = """
                {"headline":"Solide Woche","feedback":"Zwei Tage eingetragen.","goalStatus":"STALLED",
                 "goalReason":"Trend flach","proposedKcalTarget":1850,"proposedProteinTargetG":0,
                 "proposedTargetDate":"","proposalReason":"Kleiner Schritt"}""";
        String escaped = draft.replace("\"", "\\\"").replace("\n", "\\n");
        stubFor(post("/v1/messages").willReturn(okJson("""
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[{"type":"text","text":"%s"}],"stop_reason":"end_turn","stop_sequence":null,
                 "usage":{"input_tokens":1000,"output_tokens":500}}""".formatted(escaped))));
        AnthropicWeeklyReviewer reviewer = new AnthropicWeeklyReviewer(
                new AnthropicGateway("test-key", wm.getHttpBaseUrl(), meters), "claude-opus-5-5");

        ReviewDraft result = reviewer.review(goal(), week(MONDAY, new BigDecimal("82.4")), List.of(), List.of(), List.of(), true);

        assertThat(reviewer.available()).isTrue();
        assertThat(reviewer.model()).isEqualTo("claude-opus-5-5");
        assertThat(result.headline()).isEqualTo("Solide Woche");
        assertThat(result.goalStatus()).isEqualTo(GoalStatus.STALLED);
        assertThat(result.proposedKcalTarget()).isEqualTo(1850);
        String body = getAllServeEvents().getFirst().getRequest().getBodyAsString();
        assertThat(JsonPath.<String>read(body, "$.model")).isEqualTo("claude-opus-5-5");
        assertThat(JsonPath.<String>read(body, "$.output_config.effort")).isEqualTo("medium");
        assertThat(JsonPath.<Integer>read(body, "$.max_tokens")).isEqualTo(16000);
        assertThat(JsonPath.<String>read(body, "$.messages[0].content[0].text")).contains("Ziel-Check: ja");
        assertThat(body).contains("ON_TRACK").contains("RECOMPOSITION").doesNotContain("temperature");
        var cost = meters.find("planman.ai.cost.usd").tags("model", "claude-opus-5-5", "purpose", "review").counter();
        assertThat(cost).isNotNull();
        assertThat(cost.count()).isCloseTo(0.014, within(1e-9));
    }
}
