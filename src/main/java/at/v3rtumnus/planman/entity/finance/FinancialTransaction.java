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
public class FinancialTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate transactionDate;
    private BigDecimal amount;
    private BigDecimal quantity;
    private BigDecimal fee;

    @Enumerated(EnumType.STRING)
    @Column(name = "financial_transaction_type")
    private FinancialTransactionType transactionType;

    @ManyToOne
    @JoinColumn(name = "financial_product_id")
    private FinancialProduct financialProduct;

    public FinancialTransaction(LocalDate transactionDate, BigDecimal amount, BigDecimal quantity, BigDecimal fee, FinancialTransactionType transactionType, FinancialProduct financialProduct) {
        this.transactionDate = transactionDate;
        this.amount = amount;
        this.quantity = quantity;
        this.fee = fee;
        this.transactionType = transactionType;
        this.financialProduct = financialProduct;
    }
}
