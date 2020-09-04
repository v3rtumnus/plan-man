package at.v3rtumnus.planman.rest;

import at.v3rtumnus.planman.dto.expenses.Expense;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/expenses")
@Slf4j
public class ExpensesController {

    @GetMapping(path = "/ongoing")
    public ModelAndView getOngoingExpenses() {
        ModelAndView modelAndView = new ModelAndView("expenses/ongoing");

        modelAndView.addObject("expense", new Expense());

        return modelAndView;
    }
}
