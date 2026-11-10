package at.v3rtumnus.planman.service.notification;

import at.v3rtumnus.planman.dao.FinancialProductStockQuoteRepository;
import at.v3rtumnus.planman.dao.PriceAnchorRepository;
import at.v3rtumnus.planman.dto.finance.FinancialProductDTO;
import at.v3rtumnus.planman.entity.finance.FinancialProductStockQuote;
import at.v3rtumnus.planman.entity.finance.FinancialProductType;
import at.v3rtumnus.planman.entity.finance.PriceAnchor;
import at.v3rtumnus.planman.entity.notification.NotificationRule;
import at.v3rtumnus.planman.entity.notification.NotificationSeverity;
import at.v3rtumnus.planman.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Kursbewegung: a position moved ±X % (EUR) away from its anchor, the price at its last
 * notification. Only positions held today and worth at least {@code minDepotPercent} of the
 * depot, at most one notification per product a month, quotes older than {@code staleDays}
 * skipped (a stalled import alerts via Grafana). When {@code bundleMin} or more ETFs/funds fire
 * in the same week, the ones firing now come as one "Markt" notification.
 */
@Component
@RequiredArgsConstructor
public class PriceMoveEvaluator implements NotificationEvaluator {

    public static final String PRICE_MOVE = "PRICE_MOVE";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final FinanceService financeService;
    private final FinancialProductStockQuoteRepository quotes;
    private final PriceAnchorRepository anchors;
    private final Clock clock;

    record Move(FinancialProductDTO product, PriceAnchor anchor, BigDecimal percent, BigDecimal euroEffect) {

        boolean fund() {
            return product.getType() != FinancialProductType.SHARE;
        }
    }

    @Override
    public String ruleType() {
        return PRICE_MOVE;
    }

    @Override
    @Transactional
    public List<NotificationCandidate> evaluate(NotificationRule rule) {
        RuleParameters params = RuleParameters.parse(rule.getParametersJson());
        BigDecimal sharePercent = params.decimal("sharePercent", BigDecimal.valueOf(20));
        BigDecimal fundPercent = params.decimal("fundPercent", BigDecimal.valueOf(10));
        BigDecimal minDepotPercent = params.decimal("minDepotPercent", BigDecimal.valueOf(2));
        int bundleMin = params.integer("bundleMin", 3);
        int staleDays = params.integer("staleDays", 4);

        LocalDate today = LocalDate.now(clock);
        List<FinancialProductDTO> held = financeService.retrieveFinancialProducts().stream()
                .filter(p -> p.getCurrentQuantity() != null && p.getCurrentQuantity().signum() > 0)
                .filter(p -> p.getCurrentPrice() != null && p.getCurrentAmount() != null)
                .toList();
        BigDecimal depot = held.stream().map(FinancialProductDTO::getCurrentAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (depot.signum() <= 0) {
            return List.of();
        }
        Map<String, PriceAnchor> anchorByIsin = anchors.findAll().stream()
                .collect(Collectors.toMap(PriceAnchor::getIsin, Function.identity()));

        List<Move> moves = new ArrayList<>();
        for (FinancialProductDTO product : held) {
            if (!fresh(product.getIsin(), today, staleDays)) {
                continue;
            }
            PriceAnchor anchor = anchorByIsin.get(product.getIsin());
            if (anchor == null) {
                anchors.save(new PriceAnchor(product.getIsin(), today, product.getCurrentPrice(), null));
                continue;
            }
            BigDecimal weight = product.getCurrentAmount().multiply(HUNDRED).divide(depot, MathContext.DECIMAL64);
            if (weight.compareTo(minDepotPercent) < 0 || anchor.getAnchorPriceEur().signum() <= 0) {
                continue;
            }
            if (anchor.getLastNotifiedOn() != null && YearMonth.from(anchor.getLastNotifiedOn()).equals(YearMonth.from(today))) {
                continue;
            }
            BigDecimal percent = product.getCurrentPrice().subtract(anchor.getAnchorPriceEur())
                    .multiply(HUNDRED).divide(anchor.getAnchorPriceEur(), MathContext.DECIMAL64);
            BigDecimal threshold = product.getType() == FinancialProductType.SHARE ? sharePercent : fundPercent;
            if (percent.abs().compareTo(threshold) >= 0) {
                BigDecimal effect = product.getCurrentPrice().subtract(anchor.getAnchorPriceEur())
                        .multiply(product.getCurrentQuantity());
                moves.add(new Move(product, anchor, percent, effect));
            }
        }
        if (moves.isEmpty()) {
            return List.of();
        }

        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long fundsEarlierThisWeek = anchorByIsin.values().stream()
                .filter(a -> a.getLastNotifiedOn() != null && !a.getLastNotifiedOn().isBefore(monday))
                .filter(a -> held.stream().anyMatch(p -> p.getIsin().equals(a.getIsin())
                        && p.getType() != FinancialProductType.SHARE))
                .count();
        List<Move> funds = moves.stream().filter(Move::fund).toList();
        boolean bundle = !funds.isEmpty() && fundsEarlierThisWeek + funds.size() >= bundleMin;

        List<NotificationCandidate> candidates = new ArrayList<>();
        for (Move move : moves) {
            if (!(bundle && move.fund())) {
                candidates.add(single(move, today));
            }
        }
        if (bundle) {
            candidates.add(market(funds, fundsEarlierThisWeek, today));
        }
        for (Move move : moves) {
            PriceAnchor anchor = move.anchor();
            anchor.setAnchorDate(today);
            anchor.setAnchorPriceEur(move.product().getCurrentPrice());
            anchor.setLastNotifiedOn(today);
            anchors.save(anchor);
        }
        return candidates;
    }

    private boolean fresh(String isin, LocalDate today, int staleDays) {
        List<FinancialProductStockQuote> latest = quotes.findByProduct(isin,
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "lastUpdatedAt"))).getContent();
        return !latest.isEmpty() && latest.getFirst().getLastUpdatedAt() != null
                && ChronoUnit.DAYS.between(latest.getFirst().getLastUpdatedAt(), today) <= staleDays;
    }

    private static NotificationCandidate single(Move move, LocalDate today) {
        String name = move.product().getName();
        String title = "Kursbewegung: " + name + " " + percent(move.percent());
        String message = name + " " + percent(move.percent()) + " seit " + DAY.format(move.anchor().getAnchorDate())
                + ", " + signedEuro(move.euroEffect()) + ".";
        return new NotificationCandidate(title, message, NotificationSeverity.INFO,
                "price-" + move.product().getIsin() + "-" + YearMonth.from(today), "/securities/overview", List.of(), false);
    }

    private static NotificationCandidate market(List<Move> funds, long earlierThisWeek, LocalDate today) {
        BigDecimal avg = funds.stream().map(Move::percent).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(funds.size()), MathContext.DECIMAL64);
        BigDecimal effect = funds.stream().map(Move::euroEffect).reduce(BigDecimal.ZERO, BigDecimal::add);
        long total = funds.size() + earlierThisWeek;
        String title = "Markt " + percent(avg) + ": " + total + " Positionen";
        String lines = funds.stream()
                .map(m -> "• " + m.product().getName() + " " + percent(m.percent()) + " seit "
                        + DAY.format(m.anchor().getAnchorDate()) + ", " + signedEuro(m.euroEffect()))
                .collect(Collectors.joining("\n"));
        String earlier = earlierThisWeek == 0 ? "" : "\n" + earlierThisWeek + " weitere schon diese Woche gemeldet.";
        return new NotificationCandidate(title, "Zusammen " + signedEuro(effect) + ".\n" + lines + earlier,
                NotificationSeverity.INFO, "price-market-" + today, "/securities/overview", List.of(), false);
    }

    /** "−9 %" / "+12 %" (real minus sign). */
    static String percent(BigDecimal value) {
        BigDecimal rounded = value.setScale(0, RoundingMode.HALF_UP);
        return (rounded.signum() < 0 ? "−" : "+") + rounded.abs().toPlainString() + " %";
    }

    /** "−1 840 €" / "+320 €", whole euros with a thin space as thousands separator. */
    static String signedEuro(BigDecimal value) {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.GERMANY);
        String digits = format.format(value.abs().setScale(0, RoundingMode.HALF_UP)).replace('.', ' ');
        return (value.signum() < 0 ? "−" : "+") + digits + " €";
    }
}
