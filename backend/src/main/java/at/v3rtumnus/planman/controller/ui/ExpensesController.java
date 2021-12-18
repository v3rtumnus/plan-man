package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.dto.expense.ExpenseGraphItem;
import at.v3rtumnus.planman.dto.expense.ExpenseSummaryDTO;
import at.v3rtumnus.planman.entity.expense.ExpenseCategory;
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
@RequestMapping("/expenses/old")
@Slf4j
@AllArgsConstructor
public class ExpensesController {

    private final ExpenseService expenseService;

    @GetMapping(path = "/graph")
    public ModelAndView getExpensesGraph(@RequestParam(value = "lastMonths", required = false) Integer lastMonths) {
        ModelAndView modelAndView = new ModelAndView("fragments/expenses_graph");
        Map<LocalDate, List<ExpenseSummaryDTO>> expenseSummaryForMonths = expenseService.getExpenseSummariesForLastMonths(lastMonths);

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
                        .orElse(new ExpenseSummaryDTO(category, BigDecimal.ZERO))
                        .getAmount();

                expenseGraphItems.get(i).getAmounts().add(amount);
            }
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


        modelAndView.addObject("dates", dates);
        modelAndView.addObject("amounts", amounts);
        modelAndView.addObject("categories", categories);

        return modelAndView;
    }
}
