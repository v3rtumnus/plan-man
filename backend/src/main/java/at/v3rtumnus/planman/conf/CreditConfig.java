package at.v3rtumnus.planman.conf;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "credit")
@Data
public class CreditConfig {
    private BigDecimal installmentAmount;
    private BigDecimal interestRate;
    private BigDecimal processingFee;
}
