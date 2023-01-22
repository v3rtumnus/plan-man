package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.FinancialProductRepository;
import at.v3rtumnus.planman.dto.finance.FinancialOverviewDTO;
import at.v3rtumnus.planman.dto.finance.FinancialProductDTO;
import at.v3rtumnus.planman.dto.finance.FinancialTransactionDTO;
import at.v3rtumnus.planman.entity.finance.FinancialProduct;
import at.v3rtumnus.planman.entity.finance.FinancialProductType;
import at.v3rtumnus.planman.entity.finance.FinancialTransaction;
import at.v3rtumnus.planman.entity.finance.FinancialTransactionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.quotes.fx.FxQuote;
import yahoofinance.quotes.fx.FxSymbols;

import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FinanceService {

    private final EmailService emailService;
    private final TemplateEngine templateEngine;
    private final FinancialProductRepository financialProductRepository;

    @Scheduled(cron = "${financial.shares.check}")
    @Transactional
    public void checkAllActiveShares() throws JsonProcessingException {
        log.info("Starting check for all active shares");

        FinancialOverviewDTO overview = new FinancialOverviewDTO();

        BigDecimal purchasePriceTotal = BigDecimal.ZERO;
        BigDecimal amountTotalDayBefore = BigDecimal.ZERO;
        BigDecimal amountTotal = BigDecimal.ZERO;
        BigDecimal changeToday = BigDecimal.ZERO;
        BigDecimal changeTotal = BigDecimal.ZERO;

        for (FinancialProduct financialProduct : financialProductRepository.findAll()) {
            FinancialProductDTO productDTO = null;
            try {
                productDTO = mapToFinancialProductDtoWithLiveData(financialProduct);

                //null is returned in case the financial product is currently not active
                if (productDTO == null) {
                    continue;
                }

                //add amounts to totals for overview
                BigDecimal productPurchasePrice = productDTO.getCombinedPurchasePrice().multiply(productDTO.getCurrentQuantity());
                purchasePriceTotal = purchasePriceTotal.add(productPurchasePrice);

                BigDecimal productAmountTotal = productDTO.getCurrentPrice().multiply(productDTO.getCurrentQuantity());

                amountTotal = amountTotal.add(productAmountTotal);

                changeToday = changeToday.add(productDTO.getChangeToday());

                amountTotalDayBefore = amountTotalDayBefore.add(productAmountTotal.subtract(productDTO.getChangeToday()));

                changeTotal = changeTotal.add(productAmountTotal.subtract(productPurchasePrice));

                overview.getActiveProducts().add(productDTO);
            } catch (IOException e) {
                log.error("Error occured during retrieval of live data", e);
                //TODO handle this error as email
            }
        }
        
        overview.getActiveProducts().sort((p1, p2) -> {
            if (p1.getType() != p2.getType()) {
                return p1.getType().ordinal() - p2.getType().ordinal();
            } else {
                return p2.getCurrentAmount().intValue() - p1.getCurrentAmount().intValue();
            }
        });

        overview.setPurchasePriceTotal(purchasePriceTotal);
        overview.setAmountTotalDayBefore(amountTotalDayBefore);
        overview.setAmountTotal(amountTotal);
        overview.setChangeToday(changeToday);
        overview.setChangeTotal(changeTotal);

        overview.setPercentChangeToday(changeToday
                .divide(amountTotalDayBefore, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100L)));

        overview.setPercentChangeTotal(changeTotal
                .divide(purchasePriceTotal, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100L)));

        Context mailContext = new Context();
        mailContext.setVariable("overview", overview);
        String mailContent = templateEngine.process("email/financials_overview_email.html", mailContext);
        log.info("Sending email for portfolio overview");

        try {
            emailService.sendHtmlMessage("Portfolio overview", mailContent);

            log.info("Successfully finished check for active shares and sent mail");
        } catch (MessagingException e) {
            log.error("Error sending mail for portfolio overview", e);
        }
    }

    private FinancialProductDTO mapToFinancialProductDtoWithLiveData(FinancialProduct financialProduct) throws IOException {
        FinancialProductDTO productDTO = new FinancialProductDTO();

        BeanUtils.copyProperties(financialProduct, productDTO);

        productDTO.setTransactions(financialProduct.getTransactions()
        .stream().map(transaction -> {
                    FinancialTransactionDTO transactionDTO = new FinancialTransactionDTO();
                    BeanUtils.copyProperties(transaction, transactionDTO);

                    return transactionDTO;
                })
        .collect(Collectors.toList()));

        //go through the transactions and calculate current qauntity and combined purchase price
        BigDecimal currentQuantity = BigDecimal.ZERO;
        BigDecimal combinedPurchasePriceCounter = BigDecimal.ZERO;
        BigDecimal combinedPurchasePriceDenominator = BigDecimal.ZERO;

        for (FinancialTransaction transaction : financialProduct.getTransactions()) {
            if (transaction.getTransactionType() == FinancialTransactionType.SELL) {
                currentQuantity = currentQuantity.subtract(transaction.getQuantity());

                if (currentQuantity.compareTo(BigDecimal.ZERO) == 0) {
                    combinedPurchasePriceCounter = BigDecimal.ZERO;
                    combinedPurchasePriceDenominator = BigDecimal.ZERO;
                }

            } else {
                currentQuantity = currentQuantity.add(transaction.getQuantity());

                combinedPurchasePriceCounter = combinedPurchasePriceCounter.add(transaction.getAmount());
                combinedPurchasePriceDenominator = combinedPurchasePriceDenominator.add(transaction.getQuantity());
            }
        }

        //return null in case the current quantity is zero
        if (currentQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        productDTO.setCurrentQuantity(currentQuantity);

        //get the current price from Yahoo Finance and perform currency conversion if necessary
        Stock stock = YahooFinance.get(financialProduct.getSymbol());

        BigDecimal currentPrice = stock.getQuote().getPrice();
        BigDecimal change = stock.getQuote().getChange();
        BigDecimal combinedPurchasePrice = combinedPurchasePriceCounter.divide(combinedPurchasePriceDenominator, RoundingMode.HALF_UP);

        if (stock.getCurrency().equals("USD") || stock.getCurrency().equals("CAD")) {
            FxQuote euroConversion;
            if (stock.getCurrency().equals("USD")) {
                euroConversion = YahooFinance.getFx(FxSymbols.USDEUR);
            } else {
                euroConversion = YahooFinance.getFx(FxSymbols.CADEUR);
            }

            currentPrice = currentPrice.multiply(euroConversion.getPrice());
            change = change.multiply(euroConversion.getPrice());
        }

        productDTO.setCombinedPurchasePrice(combinedPurchasePrice);
        productDTO.setCurrentPrice(currentPrice);
        productDTO.setCurrentAmount(currentQuantity.multiply(currentPrice));
        productDTO.setChangeToday(change.multiply(productDTO.getCurrentQuantity()));
        productDTO.setChangeTotal(productDTO.getCurrentAmount().subtract(combinedPurchasePrice.multiply(currentQuantity)));
        productDTO.setPercentChangeToday(stock.getQuote().getChangeInPercent());

        if (productDTO.getCombinedPurchasePrice().compareTo(BigDecimal.ZERO) != 0) {
            productDTO.setPercentChangeTotal(productDTO.getCurrentPrice()
                    .subtract(productDTO.getCombinedPurchasePrice()).setScale(4, RoundingMode.HALF_UP)
                    .divide(productDTO.getCombinedPurchasePrice(), RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100L)));
        }

        return productDTO;
    }
}
