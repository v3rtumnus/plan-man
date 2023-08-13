package at.v3rtumnus.planman.dto.balance;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class BalanceComparisonDto{
    LocalDate date;
    BigDecimal balanceSum;
    BigDecimal expenses;

    public BigDecimal getNetSum() {
        return balanceSum.subtract(expenses);
    }
}
