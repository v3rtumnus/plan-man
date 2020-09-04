package at.v3rtumnus.planman.dto.credit;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InterestChange {
    private LocalDate changeDate;
    private BigDecimal interestRate;
}
