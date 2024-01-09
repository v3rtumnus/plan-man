package at.v3rtumnus.planman;

import at.v3rtumnus.planman.service.YahooFinanceService;
import io.github.bonigarcia.wdm.WebDriverManager;
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
		WebDriverManager.chromedriver().setup();

		SpringApplication.run(PlanManApplication.class, args);
	}
}
