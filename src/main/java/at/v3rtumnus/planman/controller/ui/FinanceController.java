package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.service.FinanceImportService;
import at.v3rtumnus.planman.service.FinanceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/finance")
@Slf4j
@AllArgsConstructor
public class FinanceController {

    @GetMapping(path = "/overview")
    public ModelAndView getOverview() {
        return new ModelAndView("finance/overview");
    }
}
