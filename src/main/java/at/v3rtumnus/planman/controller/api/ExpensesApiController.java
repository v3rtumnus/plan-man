package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.service.ExpenseService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/api/expenses")
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

    @PostMapping
    public @ResponseBody void updateExpenses(@RequestBody List<ExpenseDTO> expenses) {
        expenseService.updateExpenses(expenses);
    }
}
