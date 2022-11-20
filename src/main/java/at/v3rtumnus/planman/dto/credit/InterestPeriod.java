package at.v3rtumnus.planman.dto.credit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class InterestPeriod {

    private Long days;
    private BigDecimal interestRate;
    private BigDecimal balanceChange;
}
