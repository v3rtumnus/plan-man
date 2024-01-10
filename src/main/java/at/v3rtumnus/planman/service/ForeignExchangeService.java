package at.v3rtumnus.planman.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;

@Service
@Slf4j
@RequiredArgsConstructor
public class ForeignExchangeService {

    private final YahooFinanceService yahooFinanceService;

    @Cacheable("fx")
    public BigDecimal getFxRate(String fx) throws IOException {
        String os = System.getProperty("os.name");
        String remoteHost = os.startsWith("Windows") ? "localhost" : "selenium-chrome";

        return yahooFinanceService.getQuotePrice(new RemoteWebDriver(new URL("http://" + remoteHost + ":4444"), new ChromeOptions()), fx);
    }

    @CacheEvict(value = "fx", allEntries = true)
    @Scheduled(fixedRateString = "${caching.spring.fxTTL}")
    public void emptyFxCache() {
        log.info("Emptying FX cache");
    }
}
