package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.entity.credit.CreditSingleTransaction;
import at.v3rtumnus.planman.conf.CreditConfig;
import at.v3rtumnus.planman.dao.CreditSinglePaymentRepository;
import at.v3rtumnus.planman.dto.credit.CreditPlanRow;
import at.v3rtumnus.planman.dto.credit.RegularAdditionalPayment;
import at.v3rtumnus.planman.dto.credit.RowType;
import at.v3rtumnus.planman.dto.credit.SimulationData;
import at.v3rtumnus.planman.util.DateUtil;
import at.v3rtumnus.planman.util.Tuple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static java.time.temporal.ChronoUnit.DAYS;

@Service
public class CreditService {

    @Autowired
    private CreditSinglePaymentRepository creditSinglePaymentRepository;

    @Autowired
    private CreditConfig creditConfig;

    public List<CreditPlanRow> generateOriginalCreditPlan() {
        return generatePlan(true, null, null);
    }

    public List<CreditPlanRow> generateCurrentCreditPlan() {
        return generatePlan(false, null, null);
    }

    public List<CreditPlanRow> generateCreditPlanSimulation(SimulationData simulationData) {
        return generatePlan(false, simulationData, null);
    }

    public List<CreditPlanRow> generatePlan(boolean original, SimulationData simulationData, BigDecimal installment) {
        int lastRowWithInterestCalculated = -1;
        List<CreditSingleTransaction> additionalTransactions = creditSinglePaymentRepository.findAllByOrderByTransactionDate();

        if (simulationData != null) {
            additionalTransactions.addAll(generateAdditionalPaymentsForSimulation(simulationData));
        }


        LocalDate currentDate = additionalTransactions.get(0).getTransactionDate();

        List<CreditPlanRow> planRows = new ArrayList<>();
        BigDecimal currentAmount = BigDecimal.ZERO;

        int rowId = 1;
        LocalDate currentInstallmentDate = currentDate;
        do {
            //get next balance change date
            Tuple<LocalDate, RowType> nextBalanceChanging = getNextBalanceChangingDate(currentDate, currentInstallmentDate, additionalTransactions);

            switch (nextBalanceChanging.y) {
                case INSTALLMENT:
                    BigDecimal installmentAmount = creditConfig.getInstallmentAmount();

                    //if custom installment was given and relevant date is after today
                    if (installment != null && nextBalanceChanging.x.isAfter(LocalDate.now())) {
                        installmentAmount = installment;
                    }

                    BigDecimal paymentAmount = installmentAmount.min(currentAmount.abs());

                    currentAmount = currentAmount.add(paymentAmount);
                    planRows.add(new CreditPlanRow(rowId++, nextBalanceChanging.x, paymentAmount, currentAmount, RowType.INSTALLMENT));
                    currentInstallmentDate = nextBalanceChanging.x;
                    break;
                case ADDITIONAL_PAYMENT:
                    paymentAmount = additionalTransactions.get(0).getAmount();

                    //ignore additional payments if original credit plan is requested
                    if (original && paymentAmount.doubleValue() > 0) {
                        additionalTransactions.remove(0);
                        break;
                    }
                    String description = additionalTransactions.get(0).getDescription();

                    paymentAmount = paymentAmount.min(currentAmount.abs());

                    currentAmount = currentAmount.add(paymentAmount);
                    planRows.add(new CreditPlanRow(rowId++, nextBalanceChanging.x, description, paymentAmount, currentAmount, RowType.ADDITIONAL_PAYMENT, additionalTransactions.get(0).getId()));

                    additionalTransactions.remove(0);
                    break;
                case END_OF_QUARTER:
                    BigDecimal interestForQuarter = BigDecimal.ZERO;

                    ListIterator<CreditPlanRow> planRowIterator = planRows.listIterator(lastRowWithInterestCalculated + 1);

                    CreditPlanRow currentPlanRow = planRowIterator.next();

                    BigDecimal interestRate = creditConfig.getInterestRate();
                    if (simulationData != null && simulationData.getInterestChange() != null && !simulationData.getInterestChange().getChangeDate().isBefore(nextBalanceChanging.x)) {
                        interestRate = simulationData.getInterestChange().getInterestRate();
                    }

                    while (planRowIterator.hasNext()) {
                        CreditPlanRow nextRow = planRowIterator.next();

                        long daysBetween = DAYS.between(currentPlanRow.getDate(), nextRow.getDate());

                        BigDecimal interestForStep = BigDecimal.valueOf(
                                currentPlanRow.getNewBalance().doubleValue() * daysBetween / 360.0 * interestRate.doubleValue() / 100);

                        interestForQuarter = interestForQuarter.add(interestForStep);

                        currentPlanRow = nextRow;
                    }

                    long daysBetween = DAYS.between(currentPlanRow.getDate(), nextBalanceChanging.x);

                    BigDecimal interestForStep = BigDecimal.valueOf(
                            currentPlanRow.getNewBalance().doubleValue() * daysBetween / 360.0 * interestRate.doubleValue() / 100);

                    interestForQuarter = interestForQuarter.add(interestForStep);

                    currentAmount = currentAmount.add(interestForQuarter.subtract(creditConfig.getProcessingFee()));

                    planRows.add(new CreditPlanRow(rowId++, nextBalanceChanging.x, interestForQuarter.subtract(creditConfig.getProcessingFee()), currentAmount, RowType.END_OF_QUARTER));

                    lastRowWithInterestCalculated = planRows.size() - 2;

                    break;
            }

            currentDate = nextBalanceChanging.x;
        }
        while (currentAmount.compareTo(BigDecimal.ZERO) < 0);

        return planRows;
    }

    private List<CreditSingleTransaction> generateAdditionalPaymentsForSimulation(SimulationData simulationData) {
        List<CreditSingleTransaction> additionalTransactions = new ArrayList<>();

        if (simulationData.getAdditionalPayments() != null) {
            simulationData.getAdditionalPayments().forEach(ap ->
                    additionalTransactions.add(new CreditSingleTransaction(ap.getPaymentDate(), ap.getPaymentAmount())));
        }

        RegularAdditionalPayment regularAdditionalPayment = simulationData.getRegularAdditionalPayment();
        if (regularAdditionalPayment != null) {
            LocalDate paymentDate = regularAdditionalPayment.getStartDate();
            BigDecimal paymentAmount = regularAdditionalPayment.getPaymentAmount();

            for (int i = 1; i < 10000; i++) {
                additionalTransactions.add(new CreditSingleTransaction(paymentDate, paymentAmount));
                paymentDate = paymentDate.plusMonths(1);
            }
        }

        return additionalTransactions;
    }

    private Tuple<LocalDate, RowType> getNextBalanceChangingDate(LocalDate currentDate, LocalDate currentInstallmentDate, List<CreditSingleTransaction> additionalTransactions) {
        Tuple<LocalDate, RowType> nextBalanceChangingDate =
                new Tuple<>(DateUtil.getNextInstallmentDate(currentInstallmentDate), RowType.INSTALLMENT);

        LocalDate endOfQuarterDate = DateUtil.getEndOfQuarter(currentDate);

        if (endOfQuarterDate.isBefore(nextBalanceChangingDate.x) && endOfQuarterDate.isAfter(currentDate)) {
            nextBalanceChangingDate = new Tuple<>(endOfQuarterDate, RowType.END_OF_QUARTER);
        }

        LocalDate nextAdditionalTransactionDate = additionalTransactions.size() > 0 ?
                additionalTransactions.get(0).getTransactionDate() : LocalDate.MAX;

        return nextAdditionalTransactionDate.isBefore(nextBalanceChangingDate.x) ?
                new Tuple<>(nextAdditionalTransactionDate, RowType.ADDITIONAL_PAYMENT) : nextBalanceChangingDate;
    }

    public CreditSingleTransaction saveSingleTransaction(CreditSingleTransaction transaction) {
        return creditSinglePaymentRepository.saveAndFlush(transaction);
    }

    public void removeSingleTransaction(long transactionId) {
        creditSinglePaymentRepository.deleteById(transactionId);
    }

    public int getMinimumInstallment() {
        List<CreditPlanRow> originalCreditPlan = generateOriginalCreditPlan();

        LocalDate lastOriginalInstallmentDate = originalCreditPlan.get(originalCreditPlan.size() - 1).getDate();

        int testedMinimumInstallment = creditConfig.getInstallmentAmount().intValue();

        LocalDate lastInstallmentDate = lastOriginalInstallmentDate;

        while (!lastInstallmentDate.isAfter(lastOriginalInstallmentDate)) {
            List<CreditPlanRow> simulatedCreditPlan = generatePlan(false, null, BigDecimal.valueOf(--testedMinimumInstallment));

            lastInstallmentDate = simulatedCreditPlan.get(simulatedCreditPlan.size() -1).getDate();
        }

        return ++testedMinimumInstallment;
    }
}
