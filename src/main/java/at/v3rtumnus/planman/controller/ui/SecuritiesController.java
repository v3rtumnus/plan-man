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
@RequestMapping("/securities")
@Slf4j
@AllArgsConstructor
public class SecuritiesController {

    private final FinanceImportService financeImportService;
    private final FinanceService financeService;

    @GetMapping(path = "/overview")
    public ModelAndView getOverview() {
        return new ModelAndView("securities/overview");
    }

    @GetMapping(path = "/history")
    public ModelAndView getFinancialTransactionHistory() {
        ModelAndView modelAndView = new ModelAndView("securities/history");

        modelAndView.addObject("transactions", financeService.retrieveFinancialTransactions());
        return modelAndView;
    }

    @GetMapping(path = "/upload")
    public ModelAndView getFinanceUpload() {
        ModelAndView modelAndView = new ModelAndView("securities/upload");

        modelAndView.addObject("uploadLogs", financeImportService.retrieveUploadLogs());
        return modelAndView;
    }
}
