package at.v3rtumnus.planman.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class ForeignExchangeService {

    private final OnVistaFinancialService onVistaFinancialService;

    @Cacheable("fx")
    public BigDecimal getFxRate(String fx) throws IOException {
        return onVistaFinancialService.getUSDExchangeRate();
    }

    @CacheEvict(value = "fx", allEntries = true)
    @Scheduled(fixedRateString = "${caching.spring.fxTTL}")
    public void emptyFxCache() {
        log.info("Emptying FX cache");
    }
}
