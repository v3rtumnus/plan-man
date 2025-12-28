package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.balance.BalanceComparisonDto;
import at.v3rtumnus.planman.dto.balance.BalanceGroupDto;
import at.v3rtumnus.planman.entity.balance.BalanceGroupType;
import at.v3rtumnus.planman.service.BalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.math.BigDecimal;
import java.util.*;

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
        ModelAndView modelAndView = new ModelAndView("balance/fragments/details");

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

    @GetMapping(path = "/comparison")
    public ModelAndView getBalanceComparison() {
        ModelAndView modelAndView = new ModelAndView("balance/fragments/comparison");

        List<BalanceComparisonDto> balanceComparisons = balanceService.getBalanceComparisons();

        Collections.reverse(balanceComparisons);

        modelAndView.addObject("monthlyComparisons", balanceComparisons);

        return modelAndView;
    }
}
