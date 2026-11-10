package at.v3rtumnus.planman.service.notification;

import at.v3rtumnus.planman.dao.BalanceItemDetailRepository;
import at.v3rtumnus.planman.dao.InsuranceEntryRepository;
import at.v3rtumnus.planman.dao.NotificationRepository;
import at.v3rtumnus.planman.dao.UploadLogRepository;
import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.dto.insurance.OpenClaim;
import at.v3rtumnus.planman.dto.notification.NotificationAction;
import at.v3rtumnus.planman.entity.balance.BalanceGroupType;
import at.v3rtumnus.planman.entity.finance.UploadLog;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;
import at.v3rtumnus.planman.entity.notification.Notification;
import at.v3rtumnus.planman.entity.notification.NotificationRule;
import at.v3rtumnus.planman.entity.notification.NotificationSeverity;
import at.v3rtumnus.planman.service.ExpenseService;
import at.v3rtumnus.planman.service.InsuranceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Locale;

/**
 * The finance rules (N2) apart from Kursbewegung: claims not yet submitted, budget nearly used
 * up and finance import due. All are inbox-only except the budget, which
 * pushes once a month.
 */
public final class FinanceNotifications {

    public static final String CLAIM_NOT_SUBMITTED = "CLAIM_NOT_SUBMITTED";
    public static final String BUDGET_NEAR_LIMIT = "BUDGET_NEAR_LIMIT";
    public static final String IMPORT_DUE = "IMPORT_DUE";
    public static final String ACTION_SUBMITTED = "submitted";

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Locale DE = Locale.GERMAN;

    private FinanceNotifications() {
    }

    /** "1.234,50 €" */
    static String euro(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.GERMANY);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount) + " €";
    }

    static String claimPrefix(Long claimId) {
        return "claim-" + claimId + "-";
    }

    /** Claims still *eingetragen* some days after the visit; repeats weekly at most. */
    @Component
    @RequiredArgsConstructor
    public static class ClaimNotSubmittedEvaluator implements NotificationEvaluator {

        private final InsuranceEntryRepository claims;
        private final Clock clock;

        @Override
        public String ruleType() {
            return CLAIM_NOT_SUBMITTED;
        }

        @Override
        public List<NotificationCandidate> evaluate(NotificationRule rule) {
            int days = RuleParameters.parse(rule.getParametersJson()).integer("days", 14);
            LocalDate today = LocalDate.now(clock);
            String week = today.get(IsoFields.WEEK_BASED_YEAR) + "-W" + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            return claims.findOpenClaims(InsuranceEntryState.RECORDED, today.minusDays(days)).stream()
                    .map(c -> new NotificationCandidate("Noch nicht eingereicht", message(c, today),
                            NotificationSeverity.INFO, claimPrefix(c.id()) + week, "/insurance/overview",
                            List.of(new NotificationAction(ACTION_SUBMITTED, "Eingereicht")), false))
                    .toList();
        }

        static String message(OpenClaim claim, LocalDate today) {
            String what = claim.name() != null && !claim.name().isBlank() ? claim.name() : "Rechnung";
            long days = ChronoUnit.DAYS.between(claim.entryDate(), today);
            return what + " (" + claim.person() + ", " + euro(claim.amount()) + ") vom "
                    + DATE.format(claim.entryDate()) + " ist seit " + days + " Tagen nicht bei der ÖGK eingereicht.";
        }
    }

    /**
     * "Eingereicht" moves the claim to *Warte auf ÖGK*, like the button on the insurance page,
     * and clears this claim's reminders for every user. A claim that already moved on stays.
     */
    @Component
    @RequiredArgsConstructor
    public static class ClaimSubmittedActionHandler implements NotificationActionHandler {

        private final InsuranceEntryRepository claims;
        private final InsuranceService insuranceService;
        private final NotificationRepository notifications;
        private final Clock clock;

        @Override
        public String ruleType() {
            return CLAIM_NOT_SUBMITTED;
        }

        @Override
        public void handle(Notification notification, String actionKey) {
            if (!ACTION_SUBMITTED.equals(actionKey)) {
                throw new IllegalArgumentException("Unknown claim action " + actionKey);
            }
            String key = notification.getPeriodKey();
            Long claimId = Long.valueOf(key.substring("claim-".length(), key.indexOf('-', "claim-".length())));
            InsuranceEntryState state = claims.findStateById(claimId)
                    .orElseThrow(() -> new IllegalStateException("Claim " + claimId + " no longer exists"));
            if (state == InsuranceEntryState.RECORDED) {
                insuranceService.updateState(claimId, InsuranceEntryState.WAITING_FOR_HEALTH_INSURANCE);
            }
            notifications.markReadByPeriodKeyPrefix(notification.getRule(), claimPrefix(claimId), LocalDateTime.now(clock));
        }
    }

    /**
     * This month's expenses within {@code thresholdAmount} of the liquid budget (income minus
     * fixed costs valid on the 1st, as on the balance page). Push, once a month.
     */
    @Component
    @RequiredArgsConstructor
    public static class BudgetNearLimitEvaluator implements NotificationEvaluator {

        private final BalanceItemDetailRepository balanceDetails;
        private final ExpenseService expenseService;
        private final Clock clock;

        @Override
        public String ruleType() {
            return BUDGET_NEAR_LIMIT;
        }

        @Override
        public List<NotificationCandidate> evaluate(NotificationRule rule) {
            BigDecimal threshold = RuleParameters.parse(rule.getParametersJson())
                    .decimal("thresholdAmount", BigDecimal.valueOf(300));
            LocalDate first = LocalDate.now(clock).withDayOfMonth(1);
            BigDecimal budget = balanceDetails.getRelevantAmountsForDate(first).stream()
                    .map(d -> d.getItem().getGroup().getType() == BalanceGroupType.INCOME ? d.getAmount() : d.getAmount().negate())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (budget.signum() <= 0) {
                return List.of();
            }
            BigDecimal spent = expenseService.getExpensesForMonth(first.getYear(), first.getMonthValue()).stream()
                    .map(ExpenseDTO::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal remaining = budget.subtract(spent);
            if (remaining.compareTo(threshold) >= 0) {
                return List.of();
            }
            String month = first.getMonth().getDisplayName(TextStyle.FULL, DE) + " " + first.getYear();
            boolean over = remaining.signum() <= 0;
            String title = over ? "Budget überschritten: " + euro(remaining.negate()) + " drüber"
                    : "Budget knapp: noch " + euro(remaining);
            String message = "Ausgaben " + month + ": " + euro(spent) + " von " + euro(budget) + " Budget. "
                    + (over ? "Das Budget ist um " + euro(remaining.negate()) + " überschritten."
                    : "Es bleiben " + euro(remaining) + ".");
            return List.of(new NotificationCandidate(title, message,
                    over ? NotificationSeverity.DANGER : NotificationSeverity.WARNING,
                    "budget-" + first.getYear() + "-" + String.format("%02d", first.getMonthValue()),
                    "/expenses", List.of(), true));
        }
    }

    /** No finance upload for {@code days} days; once per gap. */
    @Component
    @RequiredArgsConstructor
    public static class ImportDueEvaluator implements NotificationEvaluator {

        private final UploadLogRepository uploads;
        private final Clock clock;

        @Override
        public String ruleType() {
            return IMPORT_DUE;
        }

        @Override
        public List<NotificationCandidate> evaluate(NotificationRule rule) {
            int days = RuleParameters.parse(rule.getParametersJson()).integer("days", 35);
            LocalDate today = LocalDate.now(clock);
            return uploads.findByOrderByImportedAtDesc().stream().findFirst()
                    .map(UploadLog::getImportedAt)
                    .filter(last -> ChronoUnit.DAYS.between(last, today) >= days)
                    .map(last -> List.of(new NotificationCandidate("Import fällig",
                            "Der letzte Finanz-Import war am " + DATE.format(last) + ", vor "
                                    + ChronoUnit.DAYS.between(last, today) + " Tagen.",
                            NotificationSeverity.INFO, "import-" + last, "/securities/upload", List.of(), false)))
                    .orElse(List.of());
        }
    }
}
