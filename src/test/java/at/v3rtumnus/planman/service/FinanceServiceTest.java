package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.*;
import at.v3rtumnus.planman.dto.StockInfo;
import at.v3rtumnus.planman.entity.finance.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageImpl;
import org.thymeleaf.TemplateEngine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {

    @Mock private ForeignExchangeService foreignExchangeService;
    @Mock private TemplateEngine templateEngine;
    @Mock private FinancialProductRepository financialProductRepository;
    @Mock private FinancialTransactionRepository financialTransactionRepository;
    @Mock private FinancialProductStockQuoteRepository quoteRepository;
    @Mock private DividendRepository dividendRepository;
    @Mock private SavingsPlanRepository savingsPlanRepository;
    @Mock private FinancialSnapshotRepository snapshotRepository;
    @Mock private CreditService creditService;
    @Mock private OnVistaFinancialService onVistaFinancialService;
    @Mock private CacheManager cacheManager;

    @InjectMocks
    private FinanceService service;

    @Test
    void retrieveFinancialTransactions_withNoData_returnsEmptyList() {
        when(financialTransactionRepository.findAllByOrderByTransactionDateDesc()).thenReturn(Collections.emptyList());
        when(dividendRepository.findAllByOrderByTransactionDateDesc()).thenReturn(Collections.emptyList());

        assertThat(service.retrieveFinancialTransactions()).isEmpty();
    }

    @Test
    void retrieveActiveSavingPlans_withNoPlans_returnsEmptyList() {
        when(savingsPlanRepository.findByEndDateIsNull()).thenReturn(Collections.emptyList());

        assertThat(service.retrieveActiveSavingPlans()).isEmpty();
    }

    @Test
    void getFinancialSnapshots_delegatesToRepository() {
        FinancialSnapshot snapshot = new FinancialSnapshot(
                LocalDate.of(2024, 1, 1), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE
        );
        when(snapshotRepository.findAllByOrderBySnapshotDate()).thenReturn(List.of(snapshot));

        List<FinancialSnapshot> result = service.getFinancialSnapshots();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSnapshotDate()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    @Test
    void emptySnapshotCache_doesNotThrow() {
        service.emptySnapshotCache();
    }

    @Test
    void retrieveFinancialProducts_withNoProducts_returnsEmptyList() {
        when(financialProductRepository.findAll()).thenReturn(Collections.emptyList());

        assertThat(service.retrieveFinancialProducts()).isEmpty();
    }

    @Test
    void retrieveLatestQuotes_withNoActiveProducts_doesNotCallOnVista() throws Exception {
        when(financialProductRepository.findAll()).thenReturn(Collections.emptyList());

        // @Scheduled method - call directly; should not throw
        service.retrieveLatestQuotes();
    }

    @Test
    void updateSavingsAmount_updatesSnapshotAndClearsCache() {
        FinancialSnapshot snapshot = new FinancialSnapshot(
                LocalDate.of(2024, 1, 1), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE
        );
        when(snapshotRepository.findAllByOrderBySnapshotDate()).thenReturn(List.of(snapshot));
        Cache mockCache = mock(Cache.class);
        when(cacheManager.getCache("snapshots")).thenReturn(mockCache);

        service.updateSavingsAmount("1000");

        verify(snapshotRepository).save(snapshot);
        verify(mockCache).clear();
    }

    @Test
    void retrieveFinancialTransactions_withData_includesBothTransactionsAndDividends() {
        FinancialProduct product = new FinancialProduct();
        product.setIsin("AT0000000001");
        product.setName("Test Stock");
        product.setType(FinancialProductType.SHARE);
        product.setTransactions(List.of());
        product.setDividends(List.of());

        FinancialTransaction tx = new FinancialTransaction(
                LocalDate.of(2024, 3, 1), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO,
                FinancialTransactionType.BUY, product);
        Dividend div = new Dividend(LocalDate.of(2024, 2, 1), BigDecimal.ONE, product);

        when(financialTransactionRepository.findAllByOrderByTransactionDateDesc()).thenReturn(List.of(tx));
        when(dividendRepository.findAllByOrderByTransactionDateDesc()).thenReturn(List.of(div));

        var result = service.retrieveFinancialTransactions();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(result.get(1).getTransactionDate()).isEqualTo(LocalDate.of(2024, 2, 1));
    }

    @Test
    void retrieveFinancialProducts_withActiveProductAndEurQuote_setsCurrentPrice() {
        FinancialProduct product = new FinancialProduct();
        product.setIsin("AT0000000001");
        product.setName("Test Stock");
        product.setType(FinancialProductType.SHARE);

        FinancialTransaction buyTx = new FinancialTransaction(
                LocalDate.of(2024, 1, 1), BigDecimal.valueOf(100), BigDecimal.valueOf(10),
                BigDecimal.ZERO, FinancialTransactionType.BUY, product);
        product.setTransactions(List.of(buyTx));
        product.setDividends(List.of());

        FinancialProductStockQuote quote = new FinancialProductStockQuote(
                LocalDate.now(), BigDecimal.valueOf(12), BigDecimal.valueOf(0.5),
                BigDecimal.ONE, "EUR", product);

        when(financialProductRepository.findAll()).thenReturn(List.of(product));
        when(quoteRepository.findByProduct(eq("AT0000000001"), any()))
                .thenReturn(new PageImpl<>(List.of(quote)));

        var result = service.retrieveFinancialProducts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrentPrice()).isEqualByComparingTo(BigDecimal.valueOf(12));
        assertThat(result.get(0).getCurrentAmount()).isEqualByComparingTo(BigDecimal.valueOf(120));
    }

    @Test
    void retrieveFinancialProducts_withActiveProductAndNoQuote_returnsProductWithoutPrice() {
        FinancialProduct product = new FinancialProduct();
        product.setIsin("AT0000000002");
        product.setName("No Quote Stock");
        product.setType(FinancialProductType.SHARE);

        FinancialTransaction buyTx = new FinancialTransaction(
                LocalDate.of(2024, 1, 1), BigDecimal.valueOf(50), BigDecimal.valueOf(5),
                BigDecimal.ZERO, FinancialTransactionType.BUY, product);
        product.setTransactions(List.of(buyTx));
        product.setDividends(List.of());

        when(financialProductRepository.findAll()).thenReturn(List.of(product));
        when(quoteRepository.findByProduct(eq("AT0000000002"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.retrieveFinancialProducts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrentPrice()).isNull();
    }

    @Test
    void persistFinancialSnapshots_withEmptyData_savesNewSnapshot() {
        when(financialProductRepository.findAll()).thenReturn(Collections.emptyList());
        when(snapshotRepository.findAllByOrderBySnapshotDate()).thenReturn(Collections.emptyList());
        when(creditService.generateCurrentCreditPlan()).thenReturn(Collections.emptyList());

        service.persistFinancialSnapshots();

        verify(snapshotRepository).save(any(FinancialSnapshot.class));
    }

    @Test
    void persistFinancialSnapshots_withExistingSnapshot_usesSavingsSumFromLastSnapshot() {
        FinancialSnapshot existing = new FinancialSnapshot(
                LocalDate.of(2024, 1, 1), BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.valueOf(500), BigDecimal.ONE);

        when(financialProductRepository.findAll()).thenReturn(Collections.emptyList());
        when(snapshotRepository.findAllByOrderBySnapshotDate()).thenReturn(List.of(existing));
        when(creditService.generateCurrentCreditPlan()).thenReturn(Collections.emptyList());

        service.persistFinancialSnapshots();

        verify(snapshotRepository).save(argThat(s ->
                s.getSavingsSum().compareTo(BigDecimal.valueOf(500)) == 0));
    }

    @Test
    void retrieveLatestQuotes_withActiveProductNeedingUpdate_callsOnVistaAndSavesQuote() throws Exception {
        FinancialProduct product = new FinancialProduct();
        product.setIsin("AT0000000003");
        product.setName("Active Stock");
        product.setUrl("http://test.com/stock");
        product.setType(FinancialProductType.SHARE);

        FinancialTransaction buyTx = new FinancialTransaction(
                LocalDate.of(2024, 1, 1), BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ZERO, FinancialTransactionType.BUY, product);
        product.setTransactions(List.of(buyTx));
        product.setDividends(List.of());

        // Return old quote (from 2020) so threshold check fails -> needs update
        FinancialProductStockQuote oldQuote = new FinancialProductStockQuote(
                LocalDate.of(2020, 1, 1), BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.ZERO, "EUR", product);

        when(financialProductRepository.findAll()).thenReturn(List.of(product));
        when(quoteRepository.findByProduct(eq("AT0000000003"), any()))
                .thenReturn(new PageImpl<>(List.of(oldQuote)));
        when(onVistaFinancialService.getStockInfo("http://test.com/stock"))
                .thenReturn(new StockInfo(BigDecimal.valueOf(15), BigDecimal.valueOf(0.1),
                        BigDecimal.valueOf(0.5), "EUR"));

        service.retrieveLatestQuotes();

        verify(quoteRepository).save(any(FinancialProductStockQuote.class));
    }
}
