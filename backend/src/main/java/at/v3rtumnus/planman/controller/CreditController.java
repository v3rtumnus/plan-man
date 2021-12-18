package at.v3rtumnus.planman.controller;

import at.v3rtumnus.planman.dto.credit.CreditOverview;
import at.v3rtumnus.planman.dto.credit.CreditPlanRow;
import at.v3rtumnus.planman.dto.credit.Payment;
import at.v3rtumnus.planman.dto.credit.RowType;
import at.v3rtumnus.planman.entity.credit.CreditSingleTransaction;
import at.v3rtumnus.planman.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/credit")
@Slf4j
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    @GetMapping(path = "/overview", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public CreditOverview getCreditOverview() {
        List<CreditPlanRow> currentCreditPlan = creditService.generateCurrentCreditPlan();
        List<CreditPlanRow> originalCreditPlan = creditService.generateOriginalCreditPlan();

        LocalDate currentLastDate = currentCreditPlan.get(currentCreditPlan.size() - 1).getDate();
        LocalDate originalLastDate = originalCreditPlan.get(originalCreditPlan.size() - 1).getDate();

        List<CreditPlanRow> additionalPayments = currentCreditPlan.stream()
                .filter(row -> row.getRowType() == RowType.ADDITIONAL_PAYMENT && row.getBalanceChange().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        Collections.reverse(additionalPayments);

        return new CreditOverview(
                originalLastDate,
                currentLastDate,
                additionalPayments,
                creditService.getMinimumInstallment()
        );
    }

    @GetMapping(path = "/plan", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public List<CreditPlanRow> retrieveCreditPlan() {
        return creditService.generateCurrentCreditPlan();
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public CreditSingleTransaction saveSingleTransaction(@ModelAttribute Payment payment, RedirectAttributes redirAttrs) {
        return creditService.saveSingleTransaction(new CreditSingleTransaction(payment.getDate(), payment.getDescription(), payment.getAmount()));
    }

    @DeleteMapping(path = "/{id}")
    public void removeSingleTransaction(@PathVariable("id") long transactionId) {
        creditService.removeSingleTransaction(transactionId);
    }
}
