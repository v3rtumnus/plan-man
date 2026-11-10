package at.v3rtumnus.planman.service.notification;

import at.v3rtumnus.planman.dao.*;
import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.dto.insurance.OpenClaim;
import at.v3rtumnus.planman.entity.balance.BalanceGroup;
import at.v3rtumnus.planman.entity.balance.BalanceGroupType;
import at.v3rtumnus.planman.entity.balance.BalanceItem;
import at.v3rtumnus.planman.entity.balance.BalanceItemDetail;
import at.v3rtumnus.planman.entity.finance.UploadLog;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;
import at.v3rtumnus.planman.entity.notification.Notification;
import at.v3rtumnus.planman.entity.notification.NotificationRule;
import at.v3rtumnus.planman.entity.notification.NotificationSeverity;
import at.v3rtumnus.planman.service.ExpenseService;
import at.v3rtumnus.planman.service.InsuranceService;
import at.v3rtumnus.planman.service.notification.NotificationEvaluator.NotificationCandidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FinanceNotificationsTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private final Clock clock = Clock.fixed(TODAY.atTime(10, 5).atZone(ZONE).toInstant(), ZONE);

    private static NotificationRule rule(String type, String json) {
        NotificationRule rule = new NotificationRule();
        rule.setId(9L);
        rule.setRuleType(type);
        rule.setParametersJson(json);
        return rule;
    }

    // --- Noch nicht eingereicht

    @Test
    void claim_remindsWeeklyWithSubmitButton() {
        InsuranceEntryRepository claims = mock(InsuranceEntryRepository.class);
        when(claims.findOpenClaims(InsuranceEntryState.RECORDED, TODAY.minusDays(14))).thenReturn(List.of(
                new OpenClaim(42L, LocalDate.of(2026, 10, 20), "Michael", "Dr. Jaros", new BigDecimal("100.00")),
                new OpenClaim(43L, LocalDate.of(2026, 10, 1), "Aga", " ", new BigDecimal("1234.5"))));
        var evaluator = new FinanceNotifications.ClaimNotSubmittedEvaluator(claims, clock);
        assertThat(evaluator.ruleType()).isEqualTo("CLAIM_NOT_SUBMITTED");

        List<NotificationCandidate> candidates = evaluator.evaluate(rule("CLAIM_NOT_SUBMITTED", "{\"days\":14}"));

        assertThat(candidates).hasSize(2);
        NotificationCandidate first = candidates.getFirst();
        assertThat(first.title()).isEqualTo("Noch nicht eingereicht");
        assertThat(first.message()).isEqualTo("Dr. Jaros (Michael, 100,00 €) vom 20.10.2026 ist seit 21 Tagen nicht bei der ÖGK eingereicht.");
        assertThat(first.periodKey()).isEqualTo("claim-42-2026-W46");
        assertThat(first.url()).isEqualTo("/insurance/overview");
        assertThat(first.actions()).extracting("key").containsExactly("submitted");
        assertThat(first.push()).isFalse();
        assertThat(candidates.get(1).message()).startsWith("Rechnung (Aga, 1.234,50 €) vom 01.10.2026");
    }

    @Test
    void claim_defaultDaysWhenParameterMissing() {
        InsuranceEntryRepository claims = mock(InsuranceEntryRepository.class);
        when(claims.findOpenClaims(any(), any())).thenReturn(List.of());

        assertThat(new FinanceNotifications.ClaimNotSubmittedEvaluator(claims, clock).evaluate(rule("CLAIM_NOT_SUBMITTED", "{}"))).isEmpty();
        verify(claims).findOpenClaims(InsuranceEntryState.RECORDED, TODAY.minusDays(14));
    }

    private Notification claimNotification(NotificationRule rule) {
        Notification n = new Notification();
        n.setRule(rule);
        n.setPeriodKey("claim-42-2026-W46");
        return n;
    }

    @Test
    void submitted_movesClaimToWaitingAndClearsItsReminders() {
        InsuranceEntryRepository claims = mock(InsuranceEntryRepository.class);
        InsuranceService insuranceService = mock(InsuranceService.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        when(claims.findStateById(42L)).thenReturn(Optional.of(InsuranceEntryState.RECORDED));
        var handler = new FinanceNotifications.ClaimSubmittedActionHandler(claims, insuranceService, notifications, clock);
        assertThat(handler.ruleType()).isEqualTo("CLAIM_NOT_SUBMITTED");
        NotificationRule rule = rule("CLAIM_NOT_SUBMITTED", "{}");

        handler.handle(claimNotification(rule), "submitted");

        verify(insuranceService).updateState(42L, InsuranceEntryState.WAITING_FOR_HEALTH_INSURANCE);
        verify(notifications).markReadByPeriodKeyPrefix(rule, "claim-42-", LocalDateTime.of(2026, 11, 10, 10, 5));
    }

    @Test
    void submitted_leavesAClaimThatAlreadyMovedOn() {
        InsuranceEntryRepository claims = mock(InsuranceEntryRepository.class);
        InsuranceService insuranceService = mock(InsuranceService.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        when(claims.findStateById(42L)).thenReturn(Optional.of(InsuranceEntryState.HEALH_INSURANCE_RECEIVED));
        var handler = new FinanceNotifications.ClaimSubmittedActionHandler(claims, insuranceService, notifications, clock);

        handler.handle(claimNotification(null), "submitted");

        verifyNoInteractions(insuranceService);
        verify(notifications).markReadByPeriodKeyPrefix(eq(null), eq("claim-42-"), any());
    }

    @Test
    void submitted_rejectsUnknownActionAndMissingClaim() {
        InsuranceEntryRepository claims = mock(InsuranceEntryRepository.class);
        when(claims.findStateById(42L)).thenReturn(Optional.empty());
        var handler = new FinanceNotifications.ClaimSubmittedActionHandler(claims, mock(InsuranceService.class),
                mock(NotificationRepository.class), clock);

        assertThatThrownBy(() -> handler.handle(claimNotification(null), "other")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle(claimNotification(null), "submitted")).isInstanceOf(IllegalStateException.class);
    }

    // --- Budget knapp

    private static BalanceItemDetail detail(BalanceGroupType type, String amount) {
        BalanceItem item = new BalanceItem("x", new BalanceGroup("g", type));
        BalanceItemDetail detail = new BalanceItemDetail();
        detail.setItem(item);
        detail.setAmount(new BigDecimal(amount));
        return detail;
    }

    private List<NotificationCandidate> budget(String income, String fixed, String... expenses) {
        BalanceItemDetailRepository details = mock(BalanceItemDetailRepository.class);
        ExpenseService expenseService = mock(ExpenseService.class);
        when(details.getRelevantAmountsForDate(LocalDate.of(2026, 11, 1))).thenReturn(List.of(
                detail(BalanceGroupType.INCOME, income), detail(BalanceGroupType.EXPENDITURE, fixed)));
        when(expenseService.getExpensesForMonth(2026, 11)).thenReturn(java.util.Arrays.stream(expenses)
                .map(a -> new ExpenseDTO(1L, new BigDecimal(a), "Essen", null, TODAY)).toList());
        var evaluator = new FinanceNotifications.BudgetNearLimitEvaluator(details, expenseService, clock);
        assertThat(evaluator.ruleType()).isEqualTo("BUDGET_NEAR_LIMIT");
        return evaluator.evaluate(rule("BUDGET_NEAR_LIMIT", "{\"thresholdAmount\":300}"));
    }

    @Test
    void budget_quietWhileMoreThanTheThresholdIsLeft() {
        assertThat(budget("6000", "2150.42", "3000", "549")).isEmpty();
    }

    @Test
    void budget_warnsWithPushOnceAMonth() {
        List<NotificationCandidate> candidates = budget("6000", "2150.42", "3000", "600");

        assertThat(candidates).singleElement().satisfies(c -> {
            assertThat(c.title()).isEqualTo("Budget knapp: noch 249,58 €");
            assertThat(c.message()).isEqualTo("Ausgaben November 2026: 3.600,00 € von 3.849,58 € Budget. Es bleiben 249,58 €.");
            assertThat(c.severity()).isEqualTo(NotificationSeverity.WARNING);
            assertThat(c.periodKey()).isEqualTo("budget-2026-11");
            assertThat(c.url()).isEqualTo("/expenses");
            assertThat(c.push()).isTrue();
        });
    }

    @Test
    void budget_overspentIsDanger() {
        List<NotificationCandidate> candidates = budget("6000", "2150.42", "4000");

        assertThat(candidates).singleElement().satisfies(c -> {
            assertThat(c.title()).isEqualTo("Budget überschritten: 150,42 € drüber");
            assertThat(c.message()).endsWith("Das Budget ist um 150,42 € überschritten.");
            assertThat(c.severity()).isEqualTo(NotificationSeverity.DANGER);
        });
    }

    @Test
    void budget_noBudgetNoNotification() {
        assertThat(budget("100", "200", "50")).isEmpty();
    }

    // --- Import fällig

    @Test
    void import_dueOncePerGap() {
        UploadLogRepository uploads = mock(UploadLogRepository.class);
        when(uploads.findByOrderByImportedAtDesc()).thenReturn(List.of(
                new UploadLog("a.pdf", LocalDate.of(2026, 10, 6)), new UploadLog("b.pdf", LocalDate.of(2026, 9, 1))));
        var evaluator = new FinanceNotifications.ImportDueEvaluator(uploads, clock);
        assertThat(evaluator.ruleType()).isEqualTo("IMPORT_DUE");

        assertThat(evaluator.evaluate(rule("IMPORT_DUE", "{\"days\":35}"))).singleElement().satisfies(c -> {
            assertThat(c.message()).isEqualTo("Der letzte Finanz-Import war am 06.10.2026, vor 35 Tagen.");
            assertThat(c.periodKey()).isEqualTo("import-2026-10-06");
            assertThat(c.url()).isEqualTo("/securities/upload");
            assertThat(c.push()).isFalse();
        });
        assertThat(evaluator.evaluate(rule("IMPORT_DUE", "{\"days\":36}"))).isEmpty();
    }

    @Test
    void import_nothingWithoutAnyUpload() {
        UploadLogRepository uploads = mock(UploadLogRepository.class);
        when(uploads.findByOrderByImportedAtDesc()).thenReturn(List.of());

        assertThat(new FinanceNotifications.ImportDueEvaluator(uploads, clock).evaluate(rule("IMPORT_DUE", "{}"))).isEmpty();
    }
}
