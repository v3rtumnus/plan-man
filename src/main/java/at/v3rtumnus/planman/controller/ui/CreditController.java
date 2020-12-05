package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.credit.CreditPlanRow;
import at.v3rtumnus.planman.dto.credit.Payment;
import at.v3rtumnus.planman.dto.credit.RowType;
import at.v3rtumnus.planman.entity.credit.CreditSingleTransaction;
import at.v3rtumnus.planman.service.CreditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/credit")
@Slf4j
public class CreditController {

    @Autowired
    private CreditService creditService;

    @GetMapping(path = "/overview")
    public ModelAndView getCreditOverview() {
        return getCreditOverview(null, null);
    }

    @GetMapping(path = "/plan")
    public ModelAndView getCreditPlan() {
        ModelAndView modelAndView = new ModelAndView("credit/plan");

        modelAndView.addObject("currentCreditPlan", creditService.generateCurrentCreditPlan());

        return modelAndView;
    }

    @PostMapping
    public ModelAndView saveSingleTransaction(@ModelAttribute Payment payment, RedirectAttributes redirAttrs) {

        String successMessage = null;
        String errorMessage = null;
        try {
            creditService.saveSingleTransaction(new CreditSingleTransaction(payment.getDate(), payment.getDescription(), payment.getAmount()));
            successMessage = "Zahlung erfolgreich gespeichert";
        } catch (Exception e) {
            log.error("Error when saving single transaction", e);
            errorMessage = "Leider ist ein Fehler aufgetreten";
        }


        return getCreditOverview(successMessage, errorMessage);
    }

    @DeleteMapping(path = "/{id}")
    public void removeSingleTransaction(@PathVariable("id") long transactionId) {
        creditService.removeSingleTransaction(transactionId);
    }

    private ModelAndView getCreditOverview(String successMessage, String errorMessage) {
        List<CreditPlanRow> currentCreditPlan = creditService.generateCurrentCreditPlan();
        List<CreditPlanRow> originalCreditPlan = creditService.generateOriginalCreditPlan();

        LocalDate currentLastDate = currentCreditPlan.get(currentCreditPlan.size() - 1).getDate();
        LocalDate originalLastDate = originalCreditPlan.get(originalCreditPlan.size() - 1).getDate();

        List<CreditPlanRow> additionalPayments = currentCreditPlan.stream()
                .filter(row -> row.getRowType() == RowType.ADDITIONAL_PAYMENT && row.getBalanceChange().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        Collections.reverse(additionalPayments);

        double additionalPaymentsSum = additionalPayments.stream()
                .mapToDouble(row -> row.getBalanceChange().doubleValue())
                .sum();

        BigDecimal originalInstallmentSum = originalCreditPlan.stream()
                .filter(row -> row.getRowType() == RowType.INSTALLMENT && row.getBalanceChange().compareTo(BigDecimal.ZERO) > 0)
                .map(CreditPlanRow::getBalanceChange)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal installmentSum = currentCreditPlan.stream()
                .filter(row -> row.getRowType() == RowType.INSTALLMENT && row.getBalanceChange().compareTo(BigDecimal.ZERO) > 0)
                .map(CreditPlanRow::getBalanceChange)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ModelAndView modelAndView = new ModelAndView("credit/overview");

        modelAndView.addObject("payment", new Payment());
        modelAndView.addObject("yearsRemaining", Period.between(LocalDate.now(), currentLastDate).getYears());
        modelAndView.addObject("originalYearsRemaining", Period.between(LocalDate.now(), originalLastDate).getYears());
        modelAndView.addObject("monthsRemaining", Period.between(LocalDate.now(), currentLastDate).getMonths() + 1);
        modelAndView.addObject("originalMonthsRemaining", Period.between(LocalDate.now(), originalLastDate).getMonths() + 1);

        modelAndView.addObject("installmentSum", installmentSum);
        modelAndView.addObject("remaining", originalInstallmentSum);

        modelAndView.addObject("additionalPayments", additionalPayments);
        modelAndView.addObject("additionalPaymentsSum", additionalPaymentsSum);

        modelAndView.addObject("minimumInstallment", creditService.getMinimumInstallment());

        modelAndView.addObject("successMessage", successMessage);
        modelAndView.addObject("errorMessage", errorMessage);
        return modelAndView;
    }
}
