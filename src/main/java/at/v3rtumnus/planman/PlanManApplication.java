package at.v3rtumnus.planman;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PlanManApplication {

	public static void main(String[] args) {
		SpringApplication.run(PlanManApplication.class, args);
	}
}
