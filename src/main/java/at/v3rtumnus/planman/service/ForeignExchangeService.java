package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.DividendRepository;
import at.v3rtumnus.planman.dao.FinancialProductRepository;
import at.v3rtumnus.planman.dao.FinancialProductStockQuoteRepository;
import at.v3rtumnus.planman.dao.FinancialTransactionRepository;
import at.v3rtumnus.planman.dto.finance.FinancialOverviewDTO;
import at.v3rtumnus.planman.dto.finance.FinancialProductDTO;
import at.v3rtumnus.planman.dto.finance.FinancialTransactionDTO;
import at.v3rtumnus.planman.entity.finance.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
import yahoofinance.quotes.stock.StockQuote;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ForeignExchangeService {

    @Cacheable("fx")
    public BigDecimal getFxRate(String fx) throws IOException {
        return YahooFinance.getFx(fx).getPrice();
    }

    @CacheEvict(value = "fx", allEntries = true)
    @Scheduled(fixedRateString = "${caching.spring.fxTTL}")
    public void emptyFxCache() {
        log.info("Emptying FX cache");
    }
}
