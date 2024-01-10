package at.v3rtumnus.planman.conf;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebdriverConfig {

    @Bean
    public WebDriver webDriver() {
        return WebDriverManager.chromedriver().browserInDocker().create();
    }
}
