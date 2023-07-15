package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.*;
import at.v3rtumnus.planman.dto.finance.FinancialOverviewDTO;
import at.v3rtumnus.planman.dto.finance.FinancialProductDTO;
import at.v3rtumnus.planman.dto.finance.FinancialTransactionDTO;
import at.v3rtumnus.planman.dto.finance.SavingsPlanDto;
import at.v3rtumnus.planman.entity.finance.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.quotes.fx.FxQuote;
import yahoofinance.quotes.fx.FxSymbols;

import jakarta.transaction.Transactional;
import yahoofinance.quotes.stock.StockQuote;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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
            transactionDTO.setTransactionType(FinancialTransactionType.DIVIDEND);
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
        log.info("Retrieving latest quotes");

        List<FinancialProduct> financialProducts = financialProductRepository.findAll().stream().filter(FinancialProduct::isActive).toList();

        Map<String, Stock> stocks = YahooFinance.get(financialProducts.stream().map(FinancialProduct::getSymbol).filter(Objects::nonNull).toList().toArray(new String[0]));

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
            }

            Stock stock = stocks.get(financialProduct.getSymbol());
            StockQuote stockQuote = stock.getQuote();

            FinancialProductStockQuote quote = new FinancialProductStockQuote(now, stockQuote.getPrice(), stockQuote.getChange(), stockQuote.getChangeInPercent(), stock.getCurrency(), financialProduct);

            quoteRepository.save(quote);

            log.info("Successfully saved new quote for {}", financialProduct.getName());
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

                        if (quote.getCurrency().equals("USD") || quote.getCurrency().equals("CAD")) {
                            BigDecimal euroConversion;
                            try {
                                if (quote.getCurrency().equals("USD")) {
                                    euroConversion = foreignExchangeService.getFxRate(FxSymbols.USDEUR);
                                } else {
                                    euroConversion = foreignExchangeService.getFxRate(FxSymbols.CADEUR);
                                }
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
                            BigDecimal dividendPerItem = product.getDividendTotal().divide(product.getCurrentQuantity(), RoundingMode.HALF_UP);
                            BigDecimal totalChangeBase = product.getCurrentPrice().add(dividendPerItem).subtract(product.getCombinedPurchasePrice()).setScale(4, RoundingMode.HALF_UP).divide(product.getCombinedPurchasePrice(), RoundingMode.HALF_UP);
                            product.setPercentChangeTotal(totalChangeBase.multiply(BigDecimal.valueOf(100L)));

                            double lifetime = (double) Duration.between(product.getPurchaseDate().atStartOfDay(), product.getSellDate().atStartOfDay()).toDays() / 365;

                            product.setPercentChangeYearly(
                                    (BigDecimal.valueOf(Math.pow(totalChangeBase.doubleValue() + 1, 1 / lifetime)).subtract(BigDecimal.ONE)).multiply(BigDecimal.valueOf(100L))
                            );
                        }
                    }

                    return product;
                })
                .toList();
    }

    public List<SavingsPlanDto> retrieveActiveSavingPlans() {
        return savingsPlanRepository.findByEndDateIsNull()
                .stream()
                .map(SavingsPlanDto::fromEntity)
                .toList();
    }
}
