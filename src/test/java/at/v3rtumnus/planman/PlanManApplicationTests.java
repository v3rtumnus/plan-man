package at.v3rtumnus.planman;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Full context test requires external services (DB, mail, AI, MCP)")
public class PlanManApplicationTests {

	@Test
	public void contextLoads() {
	}

}
