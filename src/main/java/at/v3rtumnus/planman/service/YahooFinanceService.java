package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dto.StockInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.NoSuchElementException;

@Service
@Slf4j
@RequiredArgsConstructor
public class YahooFinanceService {
    public StockInfo getStockInfo(String quote) {

        ChromeOptions options = new ChromeOptions();
        options.setHeadless(true);
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-software-rasterizer");
        options.addArguments("--disable-dev-shm-usage");
        WebDriver driver = new ChromeDriver(options);

        try {
            gotoStockPage(driver, quote);

            WebElement marketPrice = driver.findElement(By.xpath("//fin-streamer[@data-symbol='" + quote +"']"));
            WebElement changeToday = driver.findElement(By.xpath("//fin-streamer[@data-symbol='" + quote +"' and @data-field='regularMarketChange']"));
            WebElement changePercent = driver.findElement(By.xpath("//fin-streamer[@data-symbol='" + quote +"' and @data-field='regularMarketChangePercent']"));
            String currency = driver.findElement (By.xpath ("//*[contains(text(),'Currency in ')]")).getText();

            return new StockInfo(new BigDecimal(marketPrice.getDomAttribute("value")),
                    new BigDecimal(changeToday.getDomAttribute("value")),
                    new BigDecimal(changePercent.getDomAttribute("value")).multiply(BigDecimal.valueOf(100)),
                    currency.substring(currency.lastIndexOf(" ") + 1));
        } finally {
            driver.quit();
        }
    }

    public BigDecimal getQuotePrice(String quote) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--remote-allow-origins=*");
        WebDriver driver = new ChromeDriver(options);

        try {
            gotoStockPage(driver, quote);

            WebElement marketPrice = driver.findElement(By.xpath("//fin-streamer[@data-symbol='" + quote +"']"));

            return new BigDecimal(marketPrice.getDomAttribute("value"));
        } finally {
            driver.quit();
        }
    }

    private void gotoStockPage(WebDriver driver, String quote) {
        driver.get("https://finance.yahoo.com/quote/" + quote);

        //check if consent window opens
        try {
            WebElement scrollDownButton = driver.findElement(By.id("scroll-down-btn"));
            scrollDownButton.click();
        } catch (NoSuchElementException e) {
            log.info("No scroll down button visible, trying to see if consent window is there");
        }

        //check if consent window opens
        try {
            WebElement consentSubmitButton = driver.findElement(By.xpath("//button[@type='submit']"));
            consentSubmitButton.click();
        } catch (NoSuchElementException e) {
            log.info("No consent window opened, getting price right away");
        }
    }
}
