package at.v3rtumnus.planman.dto.finance;

import at.v3rtumnus.planman.entity.finance.FinancialTransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FinancialTransactionDTO {
    private Long id;

    private LocalDate transactionDate;
    private BigDecimal amount;
    private BigDecimal quantity;
    private BigDecimal fee;
    private FinancialTransactionType transactionType;
    private FinancialProductDTO product;
}
