package at.v3rtumnus.planman.rest;

import at.v3rtumnus.planman.dto.expense.Expense;
import at.v3rtumnus.planman.dto.expense.ExpenseGraphItem;
import at.v3rtumnus.planman.dto.expense.ExpenseSummary;
import at.v3rtumnus.planman.service.ExpenseService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/expenses")
@Slf4j
@AllArgsConstructor
public class ExpensesController {

    private static final List<String> COLORS = Arrays.asList(
      "'#FF6F61'", "'#6B5B95'", "'#88B04B'", "'#F7CAC9'", "'#92A8D1'",
            "'#955251'", "'#B565A7'", "'#009B77'", "'#DD4124'", "'#D65076'",
            "'#45B8AC'", "'#EFC050'", "'#5B5EA6'", "'#9B2335'", "'#DFCFBE'"
    );

    private final ExpenseService expenseService;

    @GetMapping(path = "/ongoing")
    public ModelAndView getOngoingExpenses() {
        ModelAndView modelAndView = new ModelAndView("expenses/ongoing");

        LocalDate endOfCurrentMonth = LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1);
        LocalDate minimumExpenseDate = expenseService.getMinimumExpenseDate().withDayOfMonth(1);

        List<LocalDate> datesToSelect = new LinkedList<>();
        while (endOfCurrentMonth.isAfter(minimumExpenseDate)) {
            datesToSelect.add(minimumExpenseDate);
            minimumExpenseDate = minimumExpenseDate.plusMonths(1);
        }

        Collections.reverse(datesToSelect);

        modelAndView.addObject("datesToSelect", datesToSelect);
        modelAndView.addObject("expense", new Expense());

        return modelAndView;
    }

    @GetMapping(path = "/ongoing/monthly")
    public ModelAndView getOngoingExpensesMonthlyOverview(@RequestParam(value = "year", required = false) Integer year,
                                                          @RequestParam(value = "month", required = false) Integer month) {
        ModelAndView modelAndView = new ModelAndView("fragments/expenses_ongoing_monthly");
        List<ExpenseSummary> expenseSummaryForMonth = expenseService.getExpenseSummaryForMonth(
                year, month);

        expenseSummaryForMonth.sort(Comparator.comparing(ExpenseSummary::getAmount).reversed());

        BigDecimal monthlySum = expenseSummaryForMonth.stream()
                .map(ExpenseSummary::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        modelAndView.addObject("expenseSummary", expenseSummaryForMonth);
        modelAndView.addObject("monthlySum", monthlySum);

        return modelAndView;
    }

    @GetMapping(path = "/ongoing/pie")
    public ModelAndView getOngoingExpensesPie(@RequestParam(value = "year", required = false) Integer year,
                                              @RequestParam(value = "month", required = false) Integer month) {
        ModelAndView modelAndView = new ModelAndView("fragments/expenses_ongoing_pie");
        List<ExpenseSummary> expenseSummaryForMonth = expenseService.getExpenseSummaryForMonth(
                year, month);

        List<String> categories = expenseSummaryForMonth
                .stream()
                .map(es -> "'" + es.getCategory() + "'")
                .collect(Collectors.toList());

        List<BigDecimal> amounts = expenseSummaryForMonth
                .stream()
                .map(ExpenseSummary::getAmount)
                .collect(Collectors.toList());

        modelAndView.addObject("categories", categories);
        modelAndView.addObject("amounts", amounts);
        modelAndView.addObject("colors", COLORS.subList(0, categories.size()));

        return modelAndView;
    }

    @GetMapping(path = "/ongoing/graph")
    public ModelAndView getOngoingExpensesGraph(@RequestParam(value = "lastMonths", required = false) Integer lastMonths) {
        ModelAndView modelAndView = new ModelAndView("fragments/expenses_ongoing_graph");
        Map<LocalDate, List<ExpenseSummary>> expenseSummaryForMonths = expenseService.getExpenseSummariesForLastMonths(lastMonths);

        List<ExpenseGraphItem> expenseGraphItems = new ArrayList<>();


        expenseSummaryForMonths.forEach((date, summaries) -> summaries.forEach(
                summary -> {
                    boolean categoryPresent = expenseGraphItems.stream()
                            .anyMatch(egi -> egi.getCategory().equals(summary.getCategory()));

                    if (!categoryPresent) {
                        expenseGraphItems.add(new ExpenseGraphItem(summary.getCategory()));
                    }
                }
        ));

        for (int i = 0; i < expenseGraphItems.size(); i++) {
            String category = expenseGraphItems.get(i).getCategory();

            for (LocalDate date : expenseSummaryForMonths.keySet()) {
                BigDecimal amount = expenseSummaryForMonths.get(date)
                        .stream()
                        .filter(es -> es.getCategory().equals(category))
                        .findFirst()
                        .orElse(new ExpenseSummary(category, BigDecimal.ZERO))
                        .getAmount();

                expenseGraphItems.get(i).getAmounts().add(amount);
            }
            expenseGraphItems.get(i).setColor(COLORS.get(i % COLORS.size()));
        }

        List<String> dates = expenseSummaryForMonths.keySet()
                .stream()
                .map(date -> "'" + DateTimeFormatter.ofPattern("MM/yy").format(date) + "'")
                .collect(Collectors.toList());

        List<List<BigDecimal>> amounts = expenseGraphItems
                .stream()
                .map(ExpenseGraphItem::getAmounts)
                .collect(Collectors.toList());

        List<String> categories = expenseGraphItems
                .stream()
                .map(egi -> "'" + egi.getCategory() + "'")
                .collect(Collectors.toList());

        List<String> colors = expenseGraphItems
                .stream()
                .map(ExpenseGraphItem::getColor)
                .collect(Collectors.toList());


        modelAndView.addObject("dates", dates);
        modelAndView.addObject("amounts", amounts);
        modelAndView.addObject("categories", categories);
        modelAndView.addObject("colors", colors);

        return modelAndView;
    }
}
