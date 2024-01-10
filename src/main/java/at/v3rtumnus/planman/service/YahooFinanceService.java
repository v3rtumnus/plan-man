package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dto.StockInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class YahooFinanceService {

    public StockInfo getStockInfo(WebDriver webDriver, String quote) {
        try {
            gotoStockPage(webDriver, quote);

            WebElement marketPrice = webDriver.findElement(By.xpath("//fin-streamer[@data-symbol='" + quote +"']"));
            WebElement changeToday = webDriver.findElement(By.xpath("//fin-streamer[@data-symbol='" + quote +"' and @data-field='regularMarketChange']"));
            WebElement changePercent = webDriver.findElement(By.xpath("//fin-streamer[@data-symbol='" + quote +"' and @data-field='regularMarketChangePercent']"));
            String currency = webDriver.findElement (By.xpath ("//*[contains(text(),'Currency in ')]")).getText();

            return new StockInfo(new BigDecimal(marketPrice.getDomAttribute("value")),
                    new BigDecimal(changeToday.getDomAttribute("value")),
                    new BigDecimal(changePercent.getDomAttribute("value")).multiply(BigDecimal.valueOf(100)),
                    currency.substring(currency.lastIndexOf(" ") + 1));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public BigDecimal getQuotePrice(WebDriver webDriver, String quote) {
        try {
            gotoStockPage(webDriver, quote);

            WebElement marketPrice = webDriver.findElement(By.xpath("//fin-streamer[@data-symbol='" + quote +"']"));

            return new BigDecimal(marketPrice.getDomAttribute("value"));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void gotoStockPage(WebDriver driver, String quote) throws InterruptedException {
        driver.get("https://finance.yahoo.com/quote/" + quote);

        //check if consent window opens
        try {
            driver.findElement(By.xpath("//fin-streamer[@data-symbol='" + quote +"']"));
            return;
        } catch (NoSuchElementException | ElementNotInteractableException e) {
            //element not accessible, consent window needs to be confirmed
        }

        //check if consent window opens
        try {
            WebElement consentSubmitButton = driver.findElement(By.xpath("//button[@type='submit']"));
            consentSubmitButton.click();
        } catch (NoSuchElementException | ElementNotInteractableException e) {
            log.info("No consent window opened, getting price right away");
        }
    }
}
