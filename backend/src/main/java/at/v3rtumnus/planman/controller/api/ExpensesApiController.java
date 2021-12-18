package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.dto.expense.ExpenseMonthSummaryDTO;
import at.v3rtumnus.planman.dto.expense.ExpenseSummaryDTO;
import at.v3rtumnus.planman.entity.expense.Expense;
import at.v3rtumnus.planman.entity.expense.ExpenseCategory;
import at.v3rtumnus.planman.service.ExpenseService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/expenses")
@Slf4j
@AllArgsConstructor
public class ExpensesApiController {

    private final ExpenseService expenseService;

    @PostMapping(produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public @ResponseBody void saveExpense(@RequestBody ExpenseDTO expense) {
        expenseService.saveExpense(expense);
    }

    @DeleteMapping("/{id}")
    public @ResponseBody void deleteExpense(@PathVariable("id") long expenseId) {
        expenseService.deleteExpense(expenseId);
    }

    @GetMapping(path = "/categories", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public @ResponseBody List<ExpenseCategory> getExpenseCategories() {
        return expenseService.getExpenseCategories();
    }

    @GetMapping(path = "/oldest", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public @ResponseBody Expense getOldestExpense() {
        return expenseService.findOldestExpense();
    }

    @GetMapping(path = "/monthly")
    public @ResponseBody List<ExpenseDTO> getExpensesMonthlyDetails(@RequestParam(value = "year") Integer year,
                                                  @RequestParam(value = "month") Integer month) {
        return expenseService.getExpensesForMonth(year, month);
    }

    @GetMapping(path = "/monthly/overview")
    public @ResponseBody ExpenseMonthSummaryDTO getExpensesMonthlyOverview(@RequestParam(value = "year", required = false) Integer year,
                                                   @RequestParam(value = "month", required = false) Integer month) {
        List<ExpenseSummaryDTO> expenseSummaryForMonth = expenseService.getExpenseSummaryForMonth(
                year, month);

        expenseSummaryForMonth.sort(Comparator.comparing(ExpenseSummaryDTO::getAmount).reversed());

        BigDecimal monthlySum = expenseSummaryForMonth.stream()
                .map(ExpenseSummaryDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ExpenseMonthSummaryDTO(expenseSummaryForMonth, monthlySum);
    }
}
