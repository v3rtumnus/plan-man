package at.v3rtumnus.planman;

import at.v3rtumnus.planman.service.YahooFinanceService;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class PlanManApplication {

	public static void main(String[] args) {
		String suffix = "";
		if (SystemUtils.OS_NAME.startsWith("Windows")) {
			suffix = ".exe";
		}

		System.setProperty("webdriver.chrome.driver", PlanManApplication.class.getClassLoader().getResource("webdriver/chromedriver" + suffix).getPath());

		SpringApplication.run(PlanManApplication.class, args);
	}
}
