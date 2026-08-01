package at.v3rtumnus.planman.entity.finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Dividend {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate transactionDate;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private FinancialTransactionType type;

    @ManyToOne
    @JoinColumn(name = "financial_product_id")
    private FinancialProduct financialProduct;

    public Dividend(LocalDate transactionDate, BigDecimal amount, FinancialTransactionType type, FinancialProduct financialProduct) {
        this.transactionDate = transactionDate;
        this.amount = amount;
        this.type = type;
        this.financialProduct = financialProduct;
    }
}
