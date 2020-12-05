package at.v3rtumnus.planman.entity.finance;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class FinancialTransaction {
    @Id
    private Long id;

    private LocalDate transactionDate;
    private BigDecimal amount;
    private BigDecimal quantity;
    private BigDecimal fee;

    @Enumerated(EnumType.STRING)
    private FinancialTransactionType transactionType;

    @ManyToOne
    private FinancialProduct financialProduct;
}
