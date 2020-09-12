package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.expense.ExpenseDto;
import at.v3rtumnus.planman.dto.expense.ExpenseGraphItem;
import at.v3rtumnus.planman.dto.expense.ExpenseSummary;
import at.v3rtumnus.planman.entity.ExpenseCategory;
import at.v3rtumnus.planman.service.ExpenseService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.math.BigDecimal;
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

    @PostMapping(produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public @ResponseBody  void saveExpense(@RequestBody ExpenseDto expense) {
        System.out.println(expense);
    }

    @GetMapping
    public ModelAndView getExpenses() {
        ModelAndView modelAndView = new ModelAndView("expenses/expenses");

        LocalDate endOfCurrentMonth = LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1);
        LocalDate minimumExpenseDate = expenseService.getMinimumExpenseDate().withDayOfMonth(1);

        List<LocalDate> datesToSelect = new LinkedList<>();
        while (endOfCurrentMonth.isAfter(minimumExpenseDate)) {
            datesToSelect.add(minimumExpenseDate);
            minimumExpenseDate = minimumExpenseDate.plusMonths(1);
        }

        Collections.reverse(datesToSelect);

        List<String> categoryNames = expenseService.getExpenseCategories()
                .stream()
                .map(ExpenseCategory::getName)
                .collect(Collectors.toList());

        modelAndView.addObject("datesToSelect", datesToSelect);
        modelAndView.addObject("categories", categoryNames);

        return modelAndView;
    }

    @GetMapping(path = "/monthly")
    public ModelAndView getExpensesMonthlyOverview(@RequestParam(value = "year", required = false) Integer year,
                                                          @RequestParam(value = "month", required = false) Integer month) {
        ModelAndView modelAndView = new ModelAndView("fragments/expenses_monthly");
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

    @GetMapping(path = "/pie")
    public ModelAndView getExpensesPie(@RequestParam(value = "year", required = false) Integer year,
                                              @RequestParam(value = "month", required = false) Integer month) {
        ModelAndView modelAndView = new ModelAndView("fragments/expenses_pie");
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

    @GetMapping(path = "/graph")
    public ModelAndView getExpensesGraph(@RequestParam(value = "lastMonths", required = false) Integer lastMonths) {
        ModelAndView modelAndView = new ModelAndView("fragments/expenses_graph");
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
