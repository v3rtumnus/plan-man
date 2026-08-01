package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.credit.Payment;
import at.v3rtumnus.planman.dto.expense.ExpenseGraphItem;
import at.v3rtumnus.planman.dto.expense.ExpenseSummary;
import at.v3rtumnus.planman.dto.finance.FinancialSnapshotDto;
import at.v3rtumnus.planman.dto.finance.PositionChangeDto;
import at.v3rtumnus.planman.dto.finance.PositionVariant;
import at.v3rtumnus.planman.service.FinanceImportService;
import at.v3rtumnus.planman.service.FinanceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

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

        modelAndView.addObject("positionChanges", buildPositionChanges(currentSnapshot));

        List<BigDecimal> amounts = Arrays.asList(currentSnapshot.getSharesSum(), currentSnapshot.getSavingsSum(),
                currentSnapshot.getFundsSum(), currentSnapshot.getEtfSum());

        modelAndView.addObject("categoriesPie", LABELS_PIE);
        modelAndView.addObject("amountsPie", amounts);
        modelAndView.addObject("colorsPie", COLORS.subList(0, LABELS_PIE.size()));

        return modelAndView;
    }

    private List<PositionChangeDto> buildPositionChanges(FinancialSnapshotDto currentSnapshot) {
        LocalDate now = LocalDate.now();
        Optional<FinancialSnapshotDto> oneMonthAgo = financeService.getFinancialSnapshotAsOf(now.minusMonths(1));
        Optional<FinancialSnapshotDto> oneYearAgo = financeService.getFinancialSnapshotAsOf(now.minusYears(1));
        Optional<FinancialSnapshotDto> threeYearsAgo = financeService.getFinancialSnapshotAsOf(now.minusYears(3));

        List<PositionChangeDto> positionChanges = new ArrayList<>();

        List<Function<FinancialSnapshotDto, BigDecimal>> metricExtractors = List.of(
                FinancialSnapshotDto::getSharesSum, FinancialSnapshotDto::getFundsSum,
                FinancialSnapshotDto::getEtfSum, FinancialSnapshotDto::getSavingsSum,
                FinancialSnapshotDto::getGrossAssets, FinancialSnapshotDto::getCreditSum,
                FinancialSnapshotDto::getNetAssets);

        List<String> labels = List.of("Aktien", "Aktienfonds", "ETFs", "Sparen", "Vermögen", "Kredit", "Nettovermögen");
        List<PositionVariant> variants = List.of(
                PositionVariant.ASSET, PositionVariant.ASSET, PositionVariant.ASSET, PositionVariant.ASSET,
                PositionVariant.TOTAL, PositionVariant.LIABILITY, PositionVariant.TOTAL);

        for (int i = 0; i < labels.size(); i++) {
            Function<FinancialSnapshotDto, BigDecimal> extractor = metricExtractors.get(i);
            BigDecimal currentValue = extractor.apply(currentSnapshot);

            Change oneMonth = computeChange(currentValue, oneMonthAgo, extractor);
            Change oneYear = computeChange(currentValue, oneYearAgo, extractor);
            Change threeYears = computeChange(currentValue, threeYearsAgo, extractor);

            positionChanges.add(new PositionChangeDto(
                    labels.get(i),
                    "Sparen".equals(labels.get(i)),
                    variants.get(i),
                    currentValue,
                    oneMonth.delta(), oneMonth.percent(),
                    oneYear.delta(), oneYear.percent(),
                    threeYears.delta(), threeYears.percent()
            ));
        }

        return positionChanges;
    }

    private Change computeChange(BigDecimal currentValue, Optional<FinancialSnapshotDto> historical,
                                  Function<FinancialSnapshotDto, BigDecimal> extractor) {
        return historical.map(s -> {
            BigDecimal historicalValue = extractor.apply(s);
            BigDecimal delta = currentValue.subtract(historicalValue);
            BigDecimal percent = historicalValue.compareTo(BigDecimal.ZERO) == 0
                    ? null
                    : delta.divide(historicalValue.abs(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100L));

            return new Change(delta, percent);
        }).orElse(new Change(null, null));
    }

    private record Change(BigDecimal delta, BigDecimal percent) {
    }

    @PostMapping(path = "/savingsAmount")
    public RedirectView saveSavingsAmount(@RequestParam(name = "savingsAmount") String savingsAmount) {
        financeService.updateSavingsAmount(savingsAmount);

        return new RedirectView("overview");
    }
}
