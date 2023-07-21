package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.expense.ExpenseGraphItem;
import at.v3rtumnus.planman.dto.expense.ExpenseSummary;
import at.v3rtumnus.planman.dto.finance.FinancialSnapshotDto;
import at.v3rtumnus.planman.service.FinanceImportService;
import at.v3rtumnus.planman.service.FinanceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/finance")
@Slf4j
@AllArgsConstructor
public class FinanceController {
    private static final List<String> COLORS = Arrays.asList(
            "'#FF6F61'", "'#6B5B95'", "'#88B04B'", "'#F7CAC9'", "'#92A8D1'",
            "'#955251'", "'#B565A7'", "'#009B77'", "'#DD4124'", "'#D65076'",
            "'#45B8AC'", "'#EFC050'", "'#5B5EA6'", "'#9B2335'", "'#DFCFBE'"
    );

    private static final List<String> LABELS_PIE = Arrays.asList(
            "'Aktien'", "'Sparen'", "'Fonds'", "'ETF'"
    );

    private static final List<String> LABELS_GRAPH = Arrays.asList(
            "'Aktien'", "'Sparen'", "'Fonds'", "'ETF'", "'Kredit'", "'Bruttovermögen'", "'Nettovermögen'"
    );

    private final FinanceService financeService;

    @GetMapping(path = "/overview")
    public ModelAndView getOverview() {
        ModelAndView modelAndView = new ModelAndView("finance/overview");

        List<FinancialSnapshotDto> snapshots = financeService.getFinancialSnapshots()
                .stream().map(FinancialSnapshotDto::fromEntity)
                .toList();

        FinancialSnapshotDto currentSnapshot = snapshots.get(snapshots.size() - 1);

        modelAndView.addObject("shareSum", currentSnapshot.getSharesSum());
        modelAndView.addObject("fundSum", currentSnapshot.getFundsSum());
        modelAndView.addObject("etfSum", currentSnapshot.getEtfSum());
        modelAndView.addObject("savingsSum", currentSnapshot.getSavingsSum());
        modelAndView.addObject("grossAssets", currentSnapshot.getGrossAssets());
        modelAndView.addObject("creditSum", currentSnapshot.getCreditSum());
        modelAndView.addObject("netAssets", currentSnapshot.getNetAssets());

        List<BigDecimal> amounts = Arrays.asList(currentSnapshot.getSharesSum(), currentSnapshot.getSavingsSum(),
                currentSnapshot.getFundsSum(), currentSnapshot.getEtfSum());

        modelAndView.addObject("categoriesPie", LABELS_PIE);
        modelAndView.addObject("amountsPie", amounts);
        modelAndView.addObject("colorsPie", COLORS.subList(0, LABELS_PIE.size()));

        List<String> dates = snapshots
                .stream()
                .map(FinancialSnapshotDto::getDate)
                .filter(d -> d.getDayOfMonth() == 1)
                .map(date -> "'" + DateTimeFormatter.ofPattern("MM/yy").format(date) + "'")
                .collect(Collectors.toList());

        List<List<BigDecimal>> amountsForGraph = new ArrayList<>();

        LABELS_GRAPH.forEach(l -> amountsForGraph.add(new ArrayList<>()));

        snapshots
                .forEach(s -> {
                    if (s.getDate().getDayOfMonth() == 1) {
                        amountsForGraph.get(0).add(s.getSharesSum());
                        amountsForGraph.get(1).add(s.getSavingsSum());
                        amountsForGraph.get(2).add(s.getFundsSum());
                        amountsForGraph.get(3).add(s.getEtfSum());
                        amountsForGraph.get(4).add(s.getCreditSum());
                        amountsForGraph.get(5).add(s.getGrossAssets());
                        amountsForGraph.get(6).add(s.getNetAssets());
                    }
                });

        modelAndView.addObject("datesGraph", dates);
        modelAndView.addObject("amountsGraph", amountsForGraph);
        modelAndView.addObject("categoriesGraph", LABELS_GRAPH);
        modelAndView.addObject("colorsGraph", COLORS.subList(0, LABELS_GRAPH.size()));

        return modelAndView;
    }
}
