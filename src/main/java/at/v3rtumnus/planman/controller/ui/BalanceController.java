package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.balance.BalanceGroupDto;
import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.dto.expense.ExpenseGraphItem;
import at.v3rtumnus.planman.dto.expense.ExpenseSummary;
import at.v3rtumnus.planman.entity.balance.BalanceGroupType;
import at.v3rtumnus.planman.entity.expense.ExpenseCategory;
import at.v3rtumnus.planman.service.BalanceService;
import at.v3rtumnus.planman.service.ExpenseService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/balance")
@Slf4j
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;

    @GetMapping
    public ModelAndView getBalanceOverview() {
        return new ModelAndView("balance/overview");
    }

    @GetMapping(path = "/detail")
    public ModelAndView getExpensesMonthlyOverview() {
        ModelAndView modelAndView = new ModelAndView("fragments/balance_details");

        Map<BalanceGroupType, List<BalanceGroupDto>> groups = balanceService.retrieveBalanceGroups();

        List<BalanceGroupDto> incomeGroups = groups.get(BalanceGroupType.INCOME);
        List<BalanceGroupDto> expenditureGroups = groups.get(BalanceGroupType.EXPENDITURE);

        BigDecimal incomeSum = BigDecimal.valueOf(incomeGroups
                .stream()
                .mapToDouble(g -> g.getSum().doubleValue())
                .sum());

        BigDecimal expenditureSum = BigDecimal.valueOf(expenditureGroups
                .stream()
                .mapToDouble(g -> g.getSum().doubleValue())
                .sum());

        modelAndView.addObject("incomeGroups", incomeGroups);
        modelAndView.addObject("expenditureGroups", expenditureGroups);
        modelAndView.addObject("incomeSum", incomeSum);
        modelAndView.addObject("expenditureSum", expenditureSum);
        modelAndView.addObject("totalSum", incomeSum.subtract(expenditureSum));

        return modelAndView;
    }
}
