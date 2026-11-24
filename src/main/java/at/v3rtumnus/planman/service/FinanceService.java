package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.*;
import at.v3rtumnus.planman.dto.StockInfo;
import at.v3rtumnus.planman.dto.credit.CreditPlanRow;
import at.v3rtumnus.planman.dto.finance.FinancialProductDTO;
import at.v3rtumnus.planman.dto.finance.FinancialSnapshotDto;
import at.v3rtumnus.planman.dto.finance.FinancialTransactionDTO;
import at.v3rtumnus.planman.dto.finance.SavingsPlanDto;
import at.v3rtumnus.planman.entity.finance.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class FinanceService {

    private final ForeignExchangeService foreignExchangeService;
    private final TemplateEngine templateEngine;
    private final FinancialProductRepository financialProductRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final FinancialProductStockQuoteRepository quoteRepository;
    private final DividendRepository dividendRepository;
    private final SavingsPlanRepository savingsPlanRepository;
    private final FinancialSnapshotRepository snapshotRepository;
    private final CreditService creditService;
    private final OnVistaFinancialService onVistaFinancialService;
    private final CacheManager cacheManager;
    private final JobStatusService jobStatusService;

    @Value("${financial.shares.quote.threshold.hours}")
    private long quoteThresholdInHours;

    public List<FinancialTransactionDTO> retrieveFinancialTransactions() {
        List<FinancialTransaction> transactions = financialTransactionRepository.findAllByOrderByTransactionDateDesc();

        List<FinancialTransactionDTO> transactionList = new ArrayList<>();

        for (FinancialTransaction transaction : transactions) {
            FinancialTransactionDTO transactionDTO = new FinancialTransactionDTO();
            transactionDTO.setTransactionDate(transaction.getTransactionDate());
            transactionDTO.setTransactionType(transaction.getTransactionType());
            transactionDTO.setAmount(transaction.getAmount());
            transactionDTO.setFee(transaction.getFee());
            transactionDTO.setQuantity(transaction.getQuantity());

            FinancialProduct financialProduct = transaction.getFinancialProduct();

            transactionDTO.setProduct(new FinancialProductDTO(financialProduct.getIsin(), financialProduct.getName(), financialProduct.getType()));

            transactionList.add(transactionDTO);
        }

        List<Dividend> dividends = dividendRepository.findAllByOrderByTransactionDateDesc();

        for (Dividend dividend : dividends) {
            FinancialTransactionDTO transactionDTO = new FinancialTransactionDTO();
            transactionDTO.setTransactionDate(dividend.getTransactionDate());
            transactionDTO.setTransactionType(dividend.getType());
            transactionDTO.setAmount(dividend.getAmount());

            FinancialProduct financialProduct = dividend.getFinancialProduct();

            transactionDTO.setProduct(new FinancialProductDTO(financialProduct.getIsin(), financialProduct.getName(), financialProduct.getType()));

            transactionList.add(transactionDTO);
        }

        transactionList.sort(Comparator.comparing(FinancialTransactionDTO::getTransactionDate).reversed());

        return transactionList;
    }

    @Scheduled(cron = "${financial.shares.quote.retrieval}")
    @Transactional
    public void retrieveLatestQuotes() throws IOException {
        try {
            retrieveStaleQuotes();
            jobStatusService.success(JobStatusService.QUOTES);
        } catch (IOException | RuntimeException e) {
            jobStatusService.failure(JobStatusService.QUOTES, e);
            throw e;
        }
    }

    private void retrieveStaleQuotes() throws IOException {
        log.info("Retrieving latest quotes");

        List<FinancialProduct> financialProducts = financialProductRepository.findAll().stream().filter(FinancialProduct::isActive).toList();

        List<FinancialProduct> productsToBeUpdated = new LinkedList<>();
        LocalDate now = LocalDate.now();

        for (FinancialProduct financialProduct : financialProducts) {
            Page<FinancialProductStockQuote> latestQuote = quoteRepository.findByProduct(financialProduct.getIsin(),
                    PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "lastUpdatedAt")));

            LocalDate lastUpdated = latestQuote
                    .stream()
                    .map(FinancialProductStockQuote::getLastUpdatedAt)
                    .findFirst()
                    .orElse(LocalDate.MIN);

            if (Duration.between(lastUpdated.atStartOfDay(), now.atStartOfDay()).toHours() < quoteThresholdInHours) {
                log.debug("Ignoring quote because last one is still valid");
                continue;
            } else {
                productsToBeUpdated.add(financialProduct);
            }
        }

        if (!productsToBeUpdated.isEmpty()) {
            for (FinancialProduct financialProduct : productsToBeUpdated) {
                StockInfo stockInfo = onVistaFinancialService.getStockInfo(financialProduct.getUrl());

                FinancialProductStockQuote quote = new FinancialProductStockQuote(now, stockInfo.getQuote(), stockInfo.getChangeToday(), stockInfo.getChangeInPercent(), stockInfo.getCurrency(), financialProduct);

                quoteRepository.save(quote);

                log.info("Successfully saved new quote for {}", financialProduct.getName());
            }
        }
    }


    public List<FinancialProductDTO> retrieveFinancialProducts() {
        return financialProductRepository.findAll()
                .stream()
                .map(FinancialProductDTO::fromEntity)
                .map(product -> {
                    boolean activeProduct = product.getCurrentQuantity().compareTo(BigDecimal.ZERO) > 0;

                    if (activeProduct) {
                        //get the current price from Yahoo Finance and perform currency conversion if necessary
                        Iterator<FinancialProductStockQuote> quoteIterator = quoteRepository.findByProduct(product.getIsin(),
                                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "lastUpdatedAt"))).iterator();

                        if (!quoteIterator.hasNext()) {
                            log.error("No quote found for security {}", product.getName());

                            return product;
                        }



                        FinancialProductStockQuote quote = quoteIterator.next();

                        BigDecimal currentPrice = quote.getQuote();
                        BigDecimal change = quote.getChangeToday();

                        if (quote.getCurrency().equals("USD")) {
                            BigDecimal euroConversion;
                            try {
                                euroConversion = foreignExchangeService.getFxRate("EUR=X");
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }

                            currentPrice = currentPrice.multiply(euroConversion);
                            change = change.multiply(euroConversion);
                        }


                        product.setCurrentPrice(currentPrice);
                        product.setCurrentAmount(product.getCurrentQuantity().multiply(currentPrice));
                        product.setChangeToday(change.multiply(product.getCurrentQuantity()));
                        product.setPercentChangeToday(quote.getChangeInPercent());

                        if (!product.isGift()) {
                            product.setChangeTotal(product.getCurrentAmount().subtract(product.getCombinedPurchasePrice().multiply(product.getCurrentQuantity())));
                        }

                        if (product.getCombinedPurchasePrice().compareTo(BigDecimal.ZERO) != 0 && !product.isGift()) {
                            BigDecimal netDividend = product.getDividendTotal().add(product.getTaxTotal());
                            BigDecimal dividendPerItem = netDividend.divide(product.getCurrentQuantity(), RoundingMode.HALF_UP);
                            BigDecimal totalChangeBase = product.getCurrentPrice().add(dividendPerItem).subtract(product.getCombinedPurchasePrice()).setScale(4, RoundingMode.HALF_UP).divide(product.getCombinedPurchasePrice(), RoundingMode.HALF_UP);
                            product.setPercentChangeTotal(totalChangeBase.multiply(BigDecimal.valueOf(100L)));

                            long days = Duration.between(product.getPurchaseDate().atStartOfDay(), product.getSellDate().atStartOfDay()).toDays();
                            BigDecimal yearly = yearlyPercent(totalChangeBase, days);
                            if (yearly == null) {
                                log.warn("No yearly return for {} ({}): total change base {} over {} days (bad quote or split not booked?)",
                                        product.getName(), product.getIsin(), totalChangeBase, days);
                            }
                            product.setPercentChangeYearly(yearly);
                        }
                    }

                    return product;
                })
                .toList();
    }

    /**
     * Yearly return in percent from the total change (0.5 = +50 %) over the holding days, or null
     * when it has no real value: bought today, or a loss beyond 100 % (a quote 60× too low after an
     * unbooked split once made this NaN, and the NaN broke the whole product list, 2026-11-24).
     */
    static BigDecimal yearlyPercent(BigDecimal totalChangeBase, long days) {
        double growth = totalChangeBase.doubleValue() + 1;
        if (days <= 0 || growth <= 0) {
            return null;
        }
        double yearly = Math.pow(growth, 365.0 / days);
        if (!Double.isFinite(yearly)) {
            return null;
        }
        return BigDecimal.valueOf(yearly).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100L));
    }

    public List<SavingsPlanDto> retrieveActiveSavingPlans() {
        return savingsPlanRepository.findByEndDateIsNull()
                .stream()
                .map(SavingsPlanDto::fromEntity)
                .toList();
    }

    @Cacheable("snapshots")
    public List<FinancialSnapshot> getFinancialSnapshots() {
        return snapshotRepository.findAllByOrderBySnapshotDate();
    }

    public Optional<FinancialSnapshotDto> getFinancialSnapshotAsOf(LocalDate targetDate) {
        return getFinancialSnapshots().stream()
                .map(FinancialSnapshotDto::fromEntity)
                .filter(s -> !s.getDate().isAfter(targetDate))
                .max(Comparator.comparing(FinancialSnapshotDto::getDate));
    }

    @CacheEvict(value = "snapshots", allEntries = true)
    @Scheduled(fixedRateString = "${caching.spring.snapshotTTL}")
    public void emptySnapshotCache() {
        log.info("Emptying snapshot cache");
    }


    @Scheduled(cron = "${financial.shares.snapshot.persistence}")
    @Transactional
    public void persistFinancialSnapshots() {
        try {
            persistSnapshot();
            jobStatusService.success(JobStatusService.SNAPSHOT);
        } catch (RuntimeException e) {
            jobStatusService.failure(JobStatusService.SNAPSHOT, e);
            throw e;
        }
    }

    private void persistSnapshot() {
        log.info("Starting persistence of financial snapshot");

        List<FinancialProductDTO> products = new LinkedList<>(this.retrieveFinancialProducts());
        List<FinancialSnapshot> snapshots = this.getFinancialSnapshots();
        FinancialSnapshot currentSnapshot;

        if (!snapshots.isEmpty()) {
            currentSnapshot = snapshots.get(snapshots.size() - 1);
        } else {
            currentSnapshot = new FinancialSnapshot();
        }


        List<FinancialProductDTO> archivedProducts = products
                .stream().filter(p -> p.getCurrentQuantity().compareTo(BigDecimal.ZERO) == 0).toList();

        products.removeAll(archivedProducts);

        BigDecimal shareSum = BigDecimal.valueOf(calculateSumForFinancialType(products, FinancialProductType.SHARE));
        BigDecimal fundSum = BigDecimal.valueOf(calculateSumForFinancialType(products, FinancialProductType.FUND));
        BigDecimal etfSum = BigDecimal.valueOf(calculateSumForFinancialType(products, FinancialProductType.ETF));
        BigDecimal savingsSum = currentSnapshot.getSavingsSum() != null ? currentSnapshot.getSavingsSum() : BigDecimal.ZERO;
        BigDecimal creditSum = BigDecimal.ZERO;

        List<CreditPlanRow> creditPlanRows = creditService.generateCurrentCreditPlan();

        for (int i = 0; i < creditPlanRows.size(); i++) {
            if (creditPlanRows.get(i).getDate().isAfter(LocalDate.now())) {
                creditSum = creditPlanRows.get(i - 1).getNewBalance();
                break;
            }
        }

        snapshotRepository.save(new FinancialSnapshot(LocalDate.now(), shareSum, fundSum, etfSum, savingsSum, creditSum));

        log.info("Finished persistence of financial snapshot");
    }

    private double calculateSumForFinancialType(List<FinancialProductDTO> products, FinancialProductType type) {
        return products
                .stream().filter(p -> p.getType() == type)
                .mapToDouble(p -> p.getCurrentAmount().doubleValue())
                .sum();
    }

    public void updateSavingsAmount(String savingsAmount) {
        log.info("Updating savings amount to {}", savingsAmount);

        List<FinancialSnapshot> snapshots = this.getFinancialSnapshots();
        FinancialSnapshot currentSnapshot = snapshots.get(snapshots.size() - 1);

        currentSnapshot.setSavingsSum(new BigDecimal(savingsAmount));

        snapshotRepository.save(currentSnapshot);

        Objects.requireNonNull(cacheManager.getCache("snapshots")).clear();
    }
}
