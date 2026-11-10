package at.v3rtumnus.planman.service.notification;

import at.v3rtumnus.planman.dao.FinancialProductStockQuoteRepository;
import at.v3rtumnus.planman.dao.PriceAnchorRepository;
import at.v3rtumnus.planman.dto.finance.FinancialProductDTO;
import at.v3rtumnus.planman.entity.finance.FinancialProductStockQuote;
import at.v3rtumnus.planman.entity.finance.FinancialProductType;
import at.v3rtumnus.planman.entity.finance.PriceAnchor;
import at.v3rtumnus.planman.entity.notification.NotificationRule;
import at.v3rtumnus.planman.service.FinanceService;
import at.v3rtumnus.planman.service.notification.NotificationEvaluator.NotificationCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PriceMoveEvaluatorTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");
    /** A Wednesday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    private final Clock clock = Clock.fixed(TODAY.atTime(10, 5).atZone(ZONE).toInstant(), ZONE);

    private final FinanceService financeService = mock(FinanceService.class);
    private final FinancialProductStockQuoteRepository quotes = mock(FinancialProductStockQuoteRepository.class);
    private final PriceAnchorRepository anchors = mock(PriceAnchorRepository.class);
    private final PriceMoveEvaluator evaluator = new PriceMoveEvaluator(financeService, quotes, anchors, clock);
    private final List<FinancialProductDTO> products = new ArrayList<>();
    private final List<PriceAnchor> anchorRows = new ArrayList<>();
    private NotificationRule rule;

    @BeforeEach
    void setUp() {
        rule = new NotificationRule();
        rule.setParametersJson("{\"sharePercent\":20,\"fundPercent\":10,\"minDepotPercent\":2,\"bundleMin\":3,\"staleDays\":4}");
        when(financeService.retrieveFinancialProducts()).thenReturn(products);
        when(anchors.findAll()).thenReturn(anchorRows);
        when(quotes.findByProduct(any(), any())).thenReturn(new PageImpl<>(List.of(quote(TODAY))));
    }

    private static FinancialProductStockQuote quote(LocalDate date) {
        FinancialProductStockQuote quote = new FinancialProductStockQuote();
        quote.setLastUpdatedAt(date);
        return quote;
    }

    private void product(String isin, String name, FinancialProductType type, String quantity, String price) {
        FinancialProductDTO product = new FinancialProductDTO();
        product.setIsin(isin);
        product.setName(name);
        product.setType(type);
        product.setCurrentQuantity(new BigDecimal(quantity));
        product.setCurrentPrice(new BigDecimal(price));
        product.setCurrentAmount(new BigDecimal(quantity).multiply(new BigDecimal(price)));
        products.add(product);
    }

    private PriceAnchor anchor(String isin, String price, LocalDate date, LocalDate notified) {
        PriceAnchor anchor = new PriceAnchor(isin, date, new BigDecimal(price), notified);
        anchorRows.add(anchor);
        return anchor;
    }

    @Test
    void firstSeen_setsTheAnchorWithoutNotifying() {
        product("AT0000000001", "Verbund", FinancialProductType.SHARE, "10", "60");

        assertThat(evaluator.evaluate(rule)).isEmpty();
        verify(anchors).save(new PriceAnchor("AT0000000001", TODAY, new BigDecimal("60"), null));
        assertThat(evaluator.ruleType()).isEqualTo("PRICE_MOVE");
    }

    @Test
    void share_firesAtTheThresholdAndMovesTheAnchor() {
        product("AT0000000001", "Verbund", FinancialProductType.SHARE, "100", "48");
        product("IE0000000002", "MSCI World", FinancialProductType.ETF, "100", "100");
        PriceAnchor anchor = anchor("AT0000000001", "60", LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3));
        anchor("IE0000000002", "95", LocalDate.of(2026, 9, 1), null);

        List<NotificationCandidate> candidates = evaluator.evaluate(rule);

        assertThat(candidates).singleElement().satisfies(c -> {
            assertThat(c.title()).isEqualTo("Kursbewegung: Verbund −20 %");
            assertThat(c.message()).isEqualTo("Verbund −20 % seit 3. Okt., −1 200 €.");
            assertThat(c.periodKey()).isEqualTo("price-AT0000000001-2026-11");
            assertThat(c.url()).isEqualTo("/securities/overview");
            assertThat(c.push()).isFalse();
        });
        assertThat(anchor.getAnchorPriceEur()).isEqualByComparingTo("48");
        assertThat(anchor.getAnchorDate()).isEqualTo(TODAY);
        assertThat(anchor.getLastNotifiedOn()).isEqualTo(TODAY);
        verify(anchors).save(anchor);
    }

    @Test
    void skipsSmallPositionsStaleQuotesAndProductsNotifiedThisMonth() {
        product("AT0000000001", "Klein", FinancialProductType.SHARE, "1", "10");
        product("AT0000000002", "Alt", FinancialProductType.SHARE, "100", "200");
        product("AT0000000003", "Schon gemeldet", FinancialProductType.SHARE, "100", "200");
        product("AT0000000004", "Ruhig", FinancialProductType.SHARE, "100", "110");
        product("AT0000000005", "Verkauft", FinancialProductType.SHARE, "0", "10");
        anchor("AT0000000001", "5", TODAY.minusMonths(2), null);
        anchor("AT0000000002", "100", TODAY.minusMonths(2), null);
        anchor("AT0000000003", "100", TODAY.minusDays(9), TODAY.minusDays(9));
        anchor("AT0000000004", "100", TODAY.minusMonths(2), null);
        when(quotes.findByProduct(eq("AT0000000002"), any())).thenReturn(new PageImpl<>(List.of(quote(TODAY.minusDays(5)))));

        assertThat(evaluator.evaluate(rule)).isEmpty();
        verify(anchors, never()).save(any());
    }

    @Test
    void productWithoutQuote_isSkipped() {
        product("AT0000000001", "Neu", FinancialProductType.SHARE, "10", "60");
        when(quotes.findByProduct(any(), any())).thenReturn(new PageImpl<>(List.of()));

        assertThat(evaluator.evaluate(rule)).isEmpty();
        verify(anchors, never()).save(any());
    }

    @Test
    void emptyDepot_nothingToDo() {
        assertThat(evaluator.evaluate(rule)).isEmpty();
    }

    @Test
    void threeFundsAtOnce_comeAsOneMarketNotification() {
        product("IE0000000001", "World", FinancialProductType.ETF, "100", "90");
        product("IE0000000002", "EM", FinancialProductType.ETF, "100", "88");
        product("AT0000000003", "Fonds", FinancialProductType.FUND, "100", "90");
        product("AT0000000004", "Aktie", FinancialProductType.SHARE, "100", "130");
        for (String isin : List.of("IE0000000001", "IE0000000002", "AT0000000003", "AT0000000004")) {
            anchor(isin, "100", LocalDate.of(2026, 10, 3), null);
        }

        List<NotificationCandidate> candidates = evaluator.evaluate(rule);

        assertThat(candidates).extracting(NotificationCandidate::title)
                .containsExactly("Kursbewegung: Aktie +30 %", "Markt −11 %: 3 Positionen");
        NotificationCandidate market = candidates.get(1);
        assertThat(market.message()).isEqualTo("Zusammen −3 200 €.\n"
                + "• World −10 % seit 3. Okt., −1 000 €\n"
                + "• EM −12 % seit 3. Okt., −1 200 €\n"
                + "• Fonds −10 % seit 3. Okt., −1 000 €");
        assertThat(market.periodKey()).isEqualTo("price-market-2026-11-11");
        verify(anchors, times(4)).save(any());
    }

    @Test
    void fundsReportedEarlierThisWeek_countTowardsTheBundle() {
        product("IE0000000001", "World", FinancialProductType.ETF, "100", "110");
        product("IE0000000002", "EM", FinancialProductType.ETF, "100", "100");
        product("IE0000000003", "Europe", FinancialProductType.ETF, "100", "100");
        product("AT0000000004", "Aktie", FinancialProductType.SHARE, "100", "100");
        anchor("IE0000000001", "100", LocalDate.of(2026, 10, 3), null);
        anchor("IE0000000002", "100", LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 9));
        anchor("IE0000000003", "100", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10));
        anchor("AT0000000004", "100", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10));

        assertThat(evaluator.evaluate(rule)).singleElement().satisfies(c -> {
            assertThat(c.title()).isEqualTo("Markt +10 %: 3 Positionen");
            assertThat(c.message()).endsWith("\n2 weitere schon diese Woche gemeldet.");
        });
    }

    @Test
    void twoFunds_stayIndividual() {
        product("IE0000000001", "World", FinancialProductType.ETF, "100", "90");
        product("IE0000000002", "EM", FinancialProductType.ETF, "100", "90");
        anchor("IE0000000001", "100", LocalDate.of(2026, 10, 3), null);
        anchor("IE0000000002", "100", LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3));

        assertThat(evaluator.evaluate(rule)).extracting(NotificationCandidate::title)
                .containsExactly("Kursbewegung: World −10 %", "Kursbewegung: EM −10 %");
    }

    @Test
    void formatting() {
        assertThat(PriceMoveEvaluator.percent(new BigDecimal("-9.4"))).isEqualTo("−9 %");
        assertThat(PriceMoveEvaluator.percent(new BigDecimal("12.5"))).isEqualTo("+13 %");
        assertThat(PriceMoveEvaluator.signedEuro(new BigDecimal("-1840.4"))).isEqualTo("−1 840 €");
        assertThat(PriceMoveEvaluator.signedEuro(new BigDecimal("320"))).isEqualTo("+320 €");
    }
}
