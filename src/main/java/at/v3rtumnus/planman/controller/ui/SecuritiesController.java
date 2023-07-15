package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.finance.FinancialProductDTO;
import at.v3rtumnus.planman.entity.finance.FinancialProduct;
import at.v3rtumnus.planman.entity.finance.FinancialProductType;
import at.v3rtumnus.planman.service.FinanceImportService;
import at.v3rtumnus.planman.service.FinanceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/securities")
@Slf4j
@AllArgsConstructor
public class SecuritiesController {

    private final FinanceImportService financeImportService;
    private final FinanceService financeService;

    @GetMapping(path = "/overview")
    public ModelAndView getOverview() {
        ModelAndView modelAndView = new ModelAndView("securities/overview");

        modelAndView.addObject("savingsPlans", financeService.retrieveActiveSavingPlans());

        List<FinancialProductDTO> products = new LinkedList<>(financeService.retrieveFinancialProducts());

        List<FinancialProductDTO> archivedProducts = products
                .stream().filter(p -> p.getCurrentQuantity().compareTo(BigDecimal.ZERO) == 0).toList();

        products.removeAll(archivedProducts);

        List<FinancialProductDTO> shareProducts = products
                .stream().filter(p -> p.getType() == FinancialProductType.SHARE).toList();

        List<FinancialProductDTO> fundProducts = products
                .stream().filter(p -> p.getType() == FinancialProductType.FUND).toList();

        List<FinancialProductDTO> etfProducts = products
                .stream().filter(p -> p.getType() == FinancialProductType.ETF).toList();

        modelAndView.addObject("shareProducts", shareProducts);
        modelAndView.addObject("fundProducts", fundProducts);
        modelAndView.addObject("etfProducts", etfProducts);

        modelAndView.addObject("shareSum", shareProducts
                .stream()
                .mapToDouble(p -> p.getCurrentAmount().doubleValue())
                .sum());
        modelAndView.addObject("fundSum", fundProducts
                .stream()
                .mapToDouble(p -> p.getCurrentAmount().doubleValue())
                .sum());
        modelAndView.addObject("etfSum", etfProducts
                .stream()
                .mapToDouble(p -> p.getCurrentAmount().doubleValue())
                .sum());

        modelAndView.addObject("shareChange", shareProducts
                .stream()
                .mapToDouble(p -> p.getChangeTotal() != null ? p.getChangeTotal().doubleValue() : 0)
                .sum());
        modelAndView.addObject("fundChange", fundProducts
                .stream()
                .mapToDouble(p -> p.getChangeTotal() != null ? p.getChangeTotal().doubleValue() : 0)
                .sum());
        modelAndView.addObject("etfChange", etfProducts
                .stream()
                .mapToDouble(p -> p.getChangeTotal() != null ? p.getChangeTotal().doubleValue() : 0)
                .sum());

        modelAndView.addObject("shareDividend", shareProducts
                .stream()
                .mapToDouble(p -> p.getDividendTotal() != null ? p.getDividendTotal().doubleValue() : 0)
                .sum());
        modelAndView.addObject("fundDividend", fundProducts
                .stream()
                .mapToDouble(p -> p.getDividendTotal() != null ? p.getDividendTotal().doubleValue() : 0)
                .sum());
        modelAndView.addObject("etfDividend", etfProducts
                .stream()
                .mapToDouble(p -> p.getDividendTotal() != null ? p.getDividendTotal().doubleValue() : 0)
                .sum());

        modelAndView.addObject("totalSum", products
                .stream()
                .mapToDouble(p -> p.getCurrentAmount().doubleValue())
                .sum());
        modelAndView.addObject("totalChange", products
                .stream()
                .mapToDouble(p -> p.getChangeTotal() != null ? p.getChangeTotal().doubleValue() : 0)
                .sum());
        modelAndView.addObject("totalDividend", products
                .stream()
                .mapToDouble(p -> p.getDividendTotal() != null ? p.getDividendTotal().doubleValue() : 0)
                .sum());

        return modelAndView;
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
