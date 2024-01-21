package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.CreditIntervalRepository;
import at.v3rtumnus.planman.dto.credit.*;
import at.v3rtumnus.planman.entity.credit.CreditInterval;
import at.v3rtumnus.planman.entity.credit.CreditSingleTransaction;
import at.v3rtumnus.planman.dao.CreditSinglePaymentRepository;
import at.v3rtumnus.planman.entity.credit.TransactionType;
import at.v3rtumnus.planman.util.DateUtil;
import at.v3rtumnus.planman.util.Tuple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;

@Service
public class CreditService {

    @Autowired
    private CreditSinglePaymentRepository creditSinglePaymentRepository;

    @Autowired
    private CreditIntervalRepository creditIntervalRepository;

    public List<CreditPlanRow> generateOriginalCreditPlan() {
        return generatePlan(true, null);
    }

    public List<CreditPlanRow> generateCurrentCreditPlan() {
        return generatePlan(false, null);
    }

    public List<CreditPlanRow> generatePlan(boolean original, BigDecimal installment) {
        int lastRowWithInterestCalculated = -1;
        List<CreditSingleTransaction> additionalTransactions = creditSinglePaymentRepository.findAllByOrderByTransactionDate();

        List<CreditInterval> creditIntervals = creditIntervalRepository.findAllOrderedCreditIntervals();
        Iterator<CreditInterval> creditIntervalIterator = creditIntervals.iterator();

        LocalDate currentDate = additionalTransactions.get(0).getTransactionDate();

        List<CreditPlanRow> planRows = new ArrayList<>();
        BigDecimal currentAmount = BigDecimal.ZERO;

        int rowId = 1;
        LocalDate currentInstallmentDate = currentDate;
        CreditInterval currentCreditInterval = creditIntervalIterator.next();
        BigDecimal latestInterestRate = BigDecimal.ZERO;
        do {
            //get next balance change date
            Tuple<LocalDate, RowType> nextBalanceChanging = getNextBalanceChangingDate(currentDate, currentInstallmentDate, additionalTransactions);

            LocalDate intervalValidUntil = currentCreditInterval.getValidUntilDate();
            if (intervalValidUntil != null && intervalValidUntil.isBefore(nextBalanceChanging.x)) {
                currentCreditInterval = creditIntervalIterator.next();
            }

            switch (nextBalanceChanging.y) {
                case INSTALLMENT:
                    BigDecimal installmentAmount = currentCreditInterval.getInstallment();
                    currentInstallmentDate = nextBalanceChanging.x;

                    //if custom installment was given and relevant date is after today
                    if (installment != null && nextBalanceChanging.x.isAfter(LocalDate.now())) {
                        installmentAmount = installment;
                    }

                    if (installmentAmount.compareTo(BigDecimal.ZERO) == 0) {
                        break;
                    }

                    BigDecimal paymentAmount = installmentAmount.min(currentAmount.abs());

                    currentAmount = currentAmount.add(paymentAmount);
                    planRows.add(new CreditPlanRow(rowId++, nextBalanceChanging.x, paymentAmount, currentAmount, RowType.INSTALLMENT));
                    break;
                case SETUP_INSTALLMENT:
                case EARLY_REPAYMENT:
                    paymentAmount = additionalTransactions.get(0).getAmount();

                    //ignore additional payments if original credit plan is requested
                    if (original && paymentAmount.doubleValue() > 0 && additionalTransactions.get(0).getType() == TransactionType.EARLY_REPAYMENT) {
                        additionalTransactions.remove(0);
                        break;
                    }
                    String description = additionalTransactions.get(0).getDescription();

                    paymentAmount = paymentAmount.min(currentAmount.abs());

                    currentAmount = currentAmount.add(paymentAmount);
                    planRows.add(new CreditPlanRow(rowId++, nextBalanceChanging.x, description, paymentAmount, currentAmount, mapToRowType(additionalTransactions.get(0).getType()), additionalTransactions.get(0).getId()));

                    additionalTransactions.remove(0);
                    break;
                case END_OF_QUARTER:
                    BigDecimal interestForQuarter = BigDecimal.ZERO;

                    LocalDate beginDateCreditIntervals;
                    if (lastRowWithInterestCalculated > -1) {
                        beginDateCreditIntervals = planRows.get(lastRowWithInterestCalculated).getDate();
                    } else {
                        beginDateCreditIntervals = planRows.get(0).getDate();
                    }

                    Map<LocalDate, InterestPeriod> interestPeriods = getRelevantCreditIntervals(creditIntervals, beginDateCreditIntervals, nextBalanceChanging.x);

                    List<CreditPlanRow> relevantPlanRows = planRows.subList(lastRowWithInterestCalculated + 1, planRows.size());

                    addRelevantPlanRowsForInterestPeriod(interestPeriods, relevantPlanRows, nextBalanceChanging.x);
                    addMissingInformationToRelevantRows(interestPeriods, latestInterestRate);

                    List<LocalDate> dates = new ArrayList<>(interestPeriods.keySet());

                    dates.addAll(relevantPlanRows.stream()
                            .map(CreditPlanRow::getDate)
                            .collect(Collectors.toList()));

                    ArrayList<Map.Entry<LocalDate, InterestPeriod>> entries = new ArrayList<>(interestPeriods.entrySet());
                    for (int i = 0; i < interestPeriods.size(); i++) {
                        Map.Entry<LocalDate, InterestPeriod> interestPeriodEntry = entries.get(i);
                        LocalDate date = interestPeriodEntry.getKey();
                        InterestPeriod interestPeriod = interestPeriodEntry.getValue();

                        interestPeriod.setDays(DAYS.between(date, getPeriodEnd(date, dates, nextBalanceChanging.x)));
                    }

                    InterestPeriod latestInterestPeriod = null;
                    BigDecimal baseAmountForInterestCalculation = lastRowWithInterestCalculated == -1
                            ? BigDecimal.ZERO :
                            planRows.get(lastRowWithInterestCalculated).getNewBalance();

                    for (Map.Entry<LocalDate, InterestPeriod> interestPeriodEntry : interestPeriods.entrySet()) {
                        InterestPeriod interestPeriod = interestPeriodEntry.getValue();
                        BigDecimal interestRate = interestPeriod.getInterestRate();

                        if (interestPeriod.getBalanceChange() != null && interestPeriod.getBalanceChange().compareTo(BigDecimal.ZERO) != 0) {
                            baseAmountForInterestCalculation = baseAmountForInterestCalculation.add(interestPeriod.getBalanceChange());
                        }

                        BigDecimal interestForStep = baseAmountForInterestCalculation
                                .multiply(BigDecimal.valueOf(interestPeriod.getDays()))
                                .divide(BigDecimal.valueOf(360), RoundingMode.HALF_UP)
                                .multiply(interestRate)
                                .divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);

                        interestForQuarter = interestForQuarter.add(interestForStep);

                        latestInterestPeriod = interestPeriod;
                    }
                    currentAmount = currentAmount.add(interestForQuarter.subtract(currentCreditInterval.getFee()));

                    planRows.add(new CreditPlanRow(rowId++, nextBalanceChanging.x, interestForQuarter.subtract(currentCreditInterval.getFee()), currentAmount, RowType.END_OF_QUARTER));

                    lastRowWithInterestCalculated = planRows.size() - 1;

                    latestInterestRate = latestInterestPeriod.getInterestRate();

                    break;
            }

            currentDate = nextBalanceChanging.x;
        }
        while (currentAmount.compareTo(BigDecimal.ZERO) < 0);

        return planRows;
    }

    private void addMissingInformationToRelevantRows(Map<LocalDate, InterestPeriod> interestPeriods, BigDecimal latestInterestRate) {
        InterestPeriod lastPeriod = new InterestPeriod(null, latestInterestRate, BigDecimal.ZERO);
        for (Map.Entry<LocalDate, InterestPeriod> interestPeriod : interestPeriods.entrySet()) {
            InterestPeriod value = interestPeriod.getValue();
            if (value.getInterestRate() == null) {
                value.setInterestRate(lastPeriod.getInterestRate());
            }

            lastPeriod = value;
        }
    }

    private Map<LocalDate, InterestPeriod> addRelevantPlanRowsForInterestPeriod(Map<LocalDate, InterestPeriod> interestPeriods,
                                                                                List<CreditPlanRow> relevantPlanRows,
                                                                                LocalDate endDate) {
        for (int i = 0; i < relevantPlanRows.size(); i++) {
            CreditPlanRow creditPlanRow = relevantPlanRows.get(i);
            LocalDate date = creditPlanRow.getDate();

            if (interestPeriods.containsKey(date)) {
                InterestPeriod interestPeriod = interestPeriods.get(date);

                BigDecimal newBalanceChange = Optional.ofNullable(interestPeriod.getBalanceChange())
                        .orElse(BigDecimal.ZERO)
                        .add(creditPlanRow.getBalanceChange());

                interestPeriod.setBalanceChange(newBalanceChange);
            } else {
                interestPeriods.put(date, new InterestPeriod(null, null,
                        creditPlanRow.getBalanceChange()));
            }
        }

        return interestPeriods;
    }

    private LocalDate getPeriodEnd(LocalDate currentDate, List<LocalDate> dates, LocalDate maxDate) {
        Optional<LocalDate> relevantPeriodEnd = dates.stream()
                .filter(date -> date.isAfter(currentDate))
                .findFirst();

        if (!relevantPeriodEnd.isPresent() || relevantPeriodEnd.get().isAfter(maxDate)) {
            return maxDate;
        }

        return relevantPeriodEnd.get();
    }

    private Map<LocalDate, InterestPeriod> getRelevantCreditIntervals(List<CreditInterval> creditIntervals, LocalDate beginDate, LocalDate endDate) {
        List<CreditInterval> relevantIntervals = creditIntervals
                .stream()
                .filter(interval -> {
                    LocalDate validUntilDate = interval.getValidUntilDate();

                    return validUntilDate == null ||
                            validUntilDate.isEqual(beginDate) ||
                            validUntilDate.isAfter(beginDate);
                })
                .collect(Collectors.toList());

        LocalDate lastValidUntil = beginDate.minusDays(1);
        Map<LocalDate, InterestPeriod> interestPeriods = new TreeMap<>();

        for (CreditInterval relevantInterval : relevantIntervals) {
            //remove credit interval with NULL valid until if it is not needed
            if (lastValidUntil.isAfter(endDate) || lastValidUntil.isEqual(endDate)) {
                break;
            }

            LocalDate interestPeriodBegin = lastValidUntil.plusDays(1);
            interestPeriods.put(interestPeriodBegin, new InterestPeriod(null,
                    relevantInterval.getInterestRate(), null));
            lastValidUntil = relevantInterval.getValidUntilDate();
        }

        return interestPeriods;
    }

    private Tuple<LocalDate, RowType> getNextBalanceChangingDate(LocalDate currentDate, LocalDate currentInstallmentDate, List<CreditSingleTransaction> additionalTransactions) {
        Tuple<LocalDate, RowType> nextBalanceChangingDate =
                new Tuple<>(DateUtil.getNextInstallmentDate(currentInstallmentDate), RowType.INSTALLMENT);

        LocalDate endOfQuarterDate = DateUtil.getEndOfQuarter(currentDate);

        if (endOfQuarterDate.isBefore(nextBalanceChangingDate.x) && endOfQuarterDate.isAfter(currentDate)) {
            nextBalanceChangingDate = new Tuple<>(endOfQuarterDate, RowType.END_OF_QUARTER);
        }

        LocalDate nextAdditionalTransactionDate = !additionalTransactions.isEmpty() ?
                additionalTransactions.get(0).getTransactionDate() : LocalDate.MAX;

        return nextAdditionalTransactionDate.isBefore(nextBalanceChangingDate.x) ?
                new Tuple<>(nextAdditionalTransactionDate, mapToRowType(additionalTransactions.get(0).getType())) : nextBalanceChangingDate;
    }

    private RowType mapToRowType(TransactionType transactionType) {
        if (transactionType == TransactionType.EARLY_REPAYMENT) {
            return RowType.EARLY_REPAYMENT;
        } else {
            return RowType.SETUP_INSTALLMENT;
        }
    }

    public void saveSingleTransaction(CreditSingleTransaction transaction) {
        creditSinglePaymentRepository.saveAndFlush(transaction);
    }

    public void removeSingleTransaction(long transactionId) {
        creditSinglePaymentRepository.deleteById(transactionId);
    }

    public BigDecimal getMinimumInstallment() {
        List<CreditPlanRow> originalCreditPlan = generateOriginalCreditPlan();

        LocalDate lastOriginalInstallmentDate = originalCreditPlan.get(originalCreditPlan.size() - 1).getDate();

        BigDecimal testedMinimumInstallment = creditIntervalRepository
                .findAllOrderedCreditIntervals()
                .stream().map(CreditInterval::getInstallment)
                .max(BigDecimal::compareTo)
                .get();

        LocalDate lastInstallmentDate = lastOriginalInstallmentDate;

        while (!lastInstallmentDate.isAfter(lastOriginalInstallmentDate)) {
            testedMinimumInstallment = testedMinimumInstallment.subtract(BigDecimal.ONE);
            List<CreditPlanRow> simulatedCreditPlan = generatePlan(false, testedMinimumInstallment);

            lastInstallmentDate = simulatedCreditPlan.get(simulatedCreditPlan.size() -1).getDate();
        }

        return testedMinimumInstallment.add(BigDecimal.ONE);
    }
}
