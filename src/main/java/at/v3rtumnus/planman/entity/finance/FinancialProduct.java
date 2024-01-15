package at.v3rtumnus.planman.entity.finance;

import at.v3rtumnus.planman.dto.finance.FinancialTransactionDTO;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class FinancialProduct {

    @Id
    private String isin;

    private String url;
    private String name;

    @Enumerated(EnumType.STRING)
    private FinancialProductType type;

    @OneToMany(mappedBy = "financialProduct")
    @ToString.Exclude
    @OrderBy("transactionDate ASC")
    private List<FinancialTransaction> transactions;

    @OneToMany(mappedBy = "financialProduct")
    @ToString.Exclude
    private List<Dividend> dividends;

    public FinancialProduct(String isin) {
        this.isin = isin;
    }

    public boolean isActive() {
        BigDecimal currentQuantity = BigDecimal.ZERO;

        for (FinancialTransaction transaction : this.getTransactions()) {
            if (transaction.getTransactionType() == FinancialTransactionType.SELL) {
                currentQuantity = currentQuantity.subtract(transaction.getQuantity());

            } else {
                currentQuantity = currentQuantity.add(transaction.getQuantity());
            }
        }

        return currentQuantity.compareTo(BigDecimal.ZERO) > 0;
    }
}
