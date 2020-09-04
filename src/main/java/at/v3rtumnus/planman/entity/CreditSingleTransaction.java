package at.v3rtumnus.planman.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "CREDIT_SINGLE_TRANSACTION")
@Data
@NoArgsConstructor
public class CreditSingleTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "TRANSACTION_DATE")
    private LocalDate transactionDate;

    @Column
    private String description;

    @Column
    private BigDecimal amount;

    public CreditSingleTransaction(LocalDate transactionDate, BigDecimal amount) {
        this.transactionDate = transactionDate;
        this.amount = amount;
    }

    public CreditSingleTransaction(LocalDate transactionDate, String description, BigDecimal amount) {
        this.transactionDate = transactionDate;
        this.description = description;
        this.amount = amount;
    }
}
