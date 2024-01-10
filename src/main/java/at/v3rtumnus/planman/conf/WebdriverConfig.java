package at.v3rtumnus.planman.conf;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.MalformedURLException;
import java.net.URL;

@Configuration
public class WebdriverConfig {

    @Bean
    public WebDriver webDriver() throws MalformedURLException {
        String os = System.getProperty("os.name");
        String remoteHost = os.startsWith("Windows") ? "localhost" : "docker-selenium";

        return new RemoteWebDriver(new URL("http://" + remoteHost + ":4444"), new ChromeOptions());
    }
}
