package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.expense.ExpenseDto;
import at.v3rtumnus.planman.service.ExpenseService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/api/expenses")
@Slf4j
@AllArgsConstructor
public class ExpensesApiController {

    private final ExpenseService expenseService;

    @PostMapping(produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public @ResponseBody void saveExpense(@RequestBody ExpenseDto expense) {
        expenseService.saveExpense(expense);
    }
}
