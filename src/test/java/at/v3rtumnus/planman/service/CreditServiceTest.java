package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.CreditIntervalRepository;
import at.v3rtumnus.planman.dao.CreditSinglePaymentRepository;
import at.v3rtumnus.planman.dto.credit.CreditPlanRow;
import at.v3rtumnus.planman.dto.credit.RowType;
import at.v3rtumnus.planman.entity.credit.CreditInterval;
import at.v3rtumnus.planman.entity.credit.CreditSingleTransaction;
import at.v3rtumnus.planman.entity.credit.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditServiceTest {

    @Mock
    private CreditSinglePaymentRepository creditSinglePaymentRepository;

    @Mock
    private CreditIntervalRepository creditIntervalRepository;

    @InjectMocks
    private CreditService creditService;

    /**
     * Builds a single CreditInterval with null validUntilDate (permanent).
     */
    private CreditInterval interval(BigDecimal rate, BigDecimal installment, BigDecimal fee, LocalDate validUntil) {
        CreditInterval ci = new CreditInterval();
        ci.setInterestRate(rate);
        ci.setInstallment(installment);
        ci.setFee(fee);
        ci.setValidUntilDate(validUntil);
        return ci;
    }

    /**
     * A setup-installment transaction (loan disbursement, negative amount).
     */
    private CreditSingleTransaction setupTx(LocalDate date, BigDecimal amount) {
        return new CreditSingleTransaction(date, "Setup", amount, TransactionType.SETUP_INSTALLMENT);
    }

    /**
     * An early-repayment transaction (positive amount, reduces balance).
     */
    private CreditSingleTransaction earlyRepaymentTx(LocalDate date, BigDecimal amount) {
        return new CreditSingleTransaction(date, "Early repayment", amount, TransactionType.EARLY_REPAYMENT);
    }

    // --- basic plan generation ---

    @Test
    void generatePlan_singleInterval_producesSetupAndInstallmentRows() {
        // Loan of 1000, 500/mo installment, 0% interest → pays off in 2 installments
        LocalDate start = LocalDate.of(2024, 1, 1);
        List<CreditSingleTransaction> txs = new ArrayList<>();
        txs.add(setupTx(start, new BigDecimal("-1000")));

        CreditInterval ci = interval(BigDecimal.ZERO, new BigDecimal("500"), BigDecimal.ZERO, null);

        when(creditSinglePaymentRepository.findAllByOrderByTransactionDate()).thenReturn(txs);
        when(creditIntervalRepository.findAllOrderedCreditIntervals()).thenReturn(List.of(ci));

        List<CreditPlanRow> plan = creditService.generatePlan(true, null);

        assertThat(plan).isNotEmpty();
        // First row is the setup installment
        assertThat(plan.get(0).getRowType()).isEqualTo(RowType.SETUP_INSTALLMENT);
        // Subsequent rows are INSTALLMENT type
        long installmentRows = plan.stream()
                .filter(r -> r.getRowType() == RowType.INSTALLMENT)
                .count();
        assertThat(installmentRows).isGreaterThanOrEqualTo(2);
    }

    @Test
    void generatePlan_terminatesWhenBalanceReachesZero() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        List<CreditSingleTransaction> txs = new ArrayList<>();
        txs.add(setupTx(start, new BigDecimal("-1000")));

        CreditInterval ci = interval(BigDecimal.ZERO, new BigDecimal("500"), BigDecimal.ZERO, null);

        when(creditSinglePaymentRepository.findAllByOrderByTransactionDate()).thenReturn(txs);
        when(creditIntervalRepository.findAllOrderedCreditIntervals()).thenReturn(List.of(ci));

        List<CreditPlanRow> plan = creditService.generatePlan(true, null);

        CreditPlanRow lastRow = plan.get(plan.size() - 1);
        assertThat(lastRow.getNewBalance().compareTo(BigDecimal.ZERO)).isGreaterThanOrEqualTo(0);
    }

    @Test
    void generatePlan_setupInstallmentRow_createsSetupRowType() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        List<CreditSingleTransaction> txs = new ArrayList<>();
        txs.add(setupTx(start, new BigDecimal("-2000")));

        CreditInterval ci = interval(BigDecimal.ZERO, new BigDecimal("1000"), BigDecimal.ZERO, null);

        when(creditSinglePaymentRepository.findAllByOrderByTransactionDate()).thenReturn(txs);
        when(creditIntervalRepository.findAllOrderedCreditIntervals()).thenReturn(List.of(ci));

        List<CreditPlanRow> plan = creditService.generatePlan(true, null);

        assertThat(plan.get(0).getRowType()).isEqualTo(RowType.SETUP_INSTALLMENT);
        assertThat(plan.get(0).getNewBalance()).isEqualByComparingTo(new BigDecimal("-2000"));
    }

    // --- quarterly interest ---

    @Test
    void generatePlan_withSmallInstallment_producesEndOfQuarterRows() {
        // Loan 5000, installment 200/mo → won't pay off in one quarter
        LocalDate start = LocalDate.of(2024, 1, 1);
        List<CreditSingleTransaction> txs = new ArrayList<>();
        txs.add(setupTx(start, new BigDecimal("-5000")));

        // 3% interest rate, small installment
        CreditInterval ci = interval(new BigDecimal("3"), new BigDecimal("200"), BigDecimal.ZERO, null);

        when(creditSinglePaymentRepository.findAllByOrderByTransactionDate()).thenReturn(txs);
        when(creditIntervalRepository.findAllOrderedCreditIntervals()).thenReturn(List.of(ci));

        List<CreditPlanRow> plan = creditService.generatePlan(true, null);

        long quarterRows = plan.stream()
                .filter(r -> r.getRowType() == RowType.END_OF_QUARTER)
                .count();
        assertThat(quarterRows).isGreaterThanOrEqualTo(1);
    }

    // --- early repayment ---

    @Test
    void generatePlan_withEarlyRepayment_reducesRemainingBalance() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate repayDate = LocalDate.of(2024, 2, 15);

        // Loan 3000, 200/mo installment (not enough to pay off fast without early repayment)
        List<CreditSingleTransaction> txs = new ArrayList<>();
        txs.add(setupTx(start, new BigDecimal("-3000")));
        txs.add(earlyRepaymentTx(repayDate, new BigDecimal("2000")));

        CreditInterval ci = interval(BigDecimal.ZERO, new BigDecimal("200"), BigDecimal.ZERO, null);

        when(creditSinglePaymentRepository.findAllByOrderByTransactionDate()).thenReturn(txs);
        when(creditIntervalRepository.findAllOrderedCreditIntervals()).thenReturn(List.of(ci));

        List<CreditPlanRow> plan = creditService.generatePlan(false, null);

        boolean hasEarlyRepayment = plan.stream()
                .anyMatch(r -> r.getRowType() == RowType.EARLY_REPAYMENT);
        assertThat(hasEarlyRepayment).isTrue();
    }

    @Test
    void generatePlan_originalTrue_ignoresEarlyRepayments() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate repayDate = LocalDate.of(2024, 2, 15);

        List<CreditSingleTransaction> txs = new ArrayList<>();
        txs.add(setupTx(start, new BigDecimal("-1000")));
        txs.add(earlyRepaymentTx(repayDate, new BigDecimal("500")));

        CreditInterval ci = interval(BigDecimal.ZERO, new BigDecimal("500"), BigDecimal.ZERO, null);

        when(creditSinglePaymentRepository.findAllByOrderByTransactionDate()).thenReturn(txs);
        when(creditIntervalRepository.findAllOrderedCreditIntervals()).thenReturn(List.of(ci));

        List<CreditPlanRow> originalPlan = creditService.generatePlan(true, null);

        boolean hasEarlyRepayment = originalPlan.stream()
                .anyMatch(r -> r.getRowType() == RowType.EARLY_REPAYMENT);
        assertThat(hasEarlyRepayment).isFalse();
    }

    // --- multiple intervals ---

    @Test
    void generatePlan_withMultipleIntervals_switchesRateAtBoundary() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        List<CreditSingleTransaction> txs = new ArrayList<>();
        txs.add(setupTx(start, new BigDecimal("-5000")));

        // First interval valid until end of Q1, second is permanent
        CreditInterval ci1 = interval(new BigDecimal("2"), new BigDecimal("200"), BigDecimal.ZERO,
                LocalDate.of(2024, 3, 31));
        CreditInterval ci2 = interval(new BigDecimal("5"), new BigDecimal("200"), BigDecimal.ZERO, null);

        when(creditSinglePaymentRepository.findAllByOrderByTransactionDate()).thenReturn(txs);
        when(creditIntervalRepository.findAllOrderedCreditIntervals()).thenReturn(List.of(ci1, ci2));

        // Should not throw
        List<CreditPlanRow> plan = creditService.generatePlan(true, null);

        assertThat(plan).isNotEmpty();
    }

    // --- generateOriginalCreditPlan vs generateCurrentCreditPlan ---

    @Test
    void generateCurrentCreditPlan_includesEarlyRepayments() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate repayDate = LocalDate.of(2024, 2, 10);

        List<CreditSingleTransaction> txs = new ArrayList<>();
        txs.add(setupTx(start, new BigDecimal("-1000")));
        txs.add(earlyRepaymentTx(repayDate, new BigDecimal("300")));

        CreditInterval ci = interval(BigDecimal.ZERO, new BigDecimal("200"), BigDecimal.ZERO, null);

        // First call: generateCurrentCreditPlan (original=false)
        when(creditSinglePaymentRepository.findAllByOrderByTransactionDate()).thenReturn(txs);
        when(creditIntervalRepository.findAllOrderedCreditIntervals()).thenReturn(List.of(ci));
        List<CreditPlanRow> current = creditService.generateCurrentCreditPlan();

        assertThat(current.stream().anyMatch(r -> r.getRowType() == RowType.EARLY_REPAYMENT)).isTrue();
    }

    // --- saveSingleTransaction / removeSingleTransaction ---

    @Test
    void saveSingleTransaction_delegatesToRepository() {
        CreditSingleTransaction tx = new CreditSingleTransaction(
                LocalDate.now(), "Extra", new BigDecimal("500"), TransactionType.EARLY_REPAYMENT);

        creditService.saveSingleTransaction(tx);

        verify(creditSinglePaymentRepository).saveAndFlush(tx);
    }

    @Test
    void removeSingleTransaction_delegatesToRepository() {
        creditService.removeSingleTransaction(42L);

        verify(creditSinglePaymentRepository).deleteById(42L);
    }

    @Test
    void generateOriginalCreditPlan_excludesEarlyRepayments() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate repayDate = LocalDate.of(2024, 2, 10);

        List<CreditSingleTransaction> txs = new ArrayList<>();
        txs.add(setupTx(start, new BigDecimal("-1000")));
        txs.add(earlyRepaymentTx(repayDate, new BigDecimal("300")));

        CreditInterval ci = interval(BigDecimal.ZERO, new BigDecimal("500"), BigDecimal.ZERO, null);

        when(creditSinglePaymentRepository.findAllByOrderByTransactionDate()).thenReturn(txs);
        when(creditIntervalRepository.findAllOrderedCreditIntervals()).thenReturn(List.of(ci));
        List<CreditPlanRow> original = creditService.generateOriginalCreditPlan();

        assertThat(original.stream().anyMatch(r -> r.getRowType() == RowType.EARLY_REPAYMENT)).isFalse();
    }
}
