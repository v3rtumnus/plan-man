package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.PlanManUser;
import at.v3rtumnus.planman.dto.expense.ExpenseSummary;
import at.v3rtumnus.planman.dto.finance.FinancialSnapshotDto;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;
import at.v3rtumnus.planman.service.BalanceService;
import at.v3rtumnus.planman.service.CreditService;
import at.v3rtumnus.planman.service.ExpenseService;
import at.v3rtumnus.planman.service.FinanceService;
import at.v3rtumnus.planman.service.InsuranceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class UiController {

    private final FinanceService financeService;
    private final BalanceService balanceService;
    private final CreditService creditService;
    private final InsuranceService insuranceService;
    private final ExpenseService expenseService;

    @GetMapping("")
    public ModelAndView homePage() {
        ModelAndView modelAndView = new ModelAndView("index");

        List<FinancialSnapshotDto> snapshots = financeService.getFinancialSnapshots()
                .stream().map(FinancialSnapshotDto::fromEntity)
                .toList();
        BigDecimal netAssets = snapshots.get(snapshots.size() - 1).getNetAssets();

        BigDecimal currentMonthNetResult = balanceService.getCurrentMonthNetResult();

        LocalDate now = LocalDate.now();
        BigDecimal expensesThisMonth = expenseService.getExpenseSummaryForMonth(now.getYear(), now.getMonthValue())
                .stream()
                .map(ExpenseSummary::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal currentCreditBalance = creditService.getCurrentBalance();

        long openInsuranceClaims = insuranceService.getInsuranceEntries(null, null, null)
                .stream()
                .filter(e -> e.getCalculatedState() != InsuranceEntryState.DONE)
                .count();

        modelAndView.addObject("netAssets", netAssets);
        modelAndView.addObject("currentMonthNetResult", currentMonthNetResult);
        modelAndView.addObject("expensesThisMonth", expensesThisMonth);
        modelAndView.addObject("currentCreditBalance", currentCreditBalance);
        modelAndView.addObject("openInsuranceClaims", openInsuranceClaims);

        return modelAndView;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @ModelAttribute
    public User globalUserObject(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("loggedInUser", authentication.getName());
        model.addAttribute("roles", authentication.getAuthorities());

        return new PlanManUser(authentication.getName(), "", authentication.getAuthorities());
    }
}
