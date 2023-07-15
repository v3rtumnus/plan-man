package at.v3rtumnus.planman.entity.finance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class FinancialProductStockQuote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate lastUpdatedAt;
    private BigDecimal quote;
    private BigDecimal changeToday;
    private BigDecimal changeInPercent;

    private String currency;
    @ManyToOne
    @JoinColumn(name = "financial_product_id")
    private FinancialProduct financialProduct;

    public FinancialProductStockQuote(LocalDate lastUpdatedAt, BigDecimal quote, BigDecimal changeToday, BigDecimal changeInPercent, String currency, FinancialProduct financialProduct) {
        this.lastUpdatedAt = lastUpdatedAt;
        this.quote = quote;
        this.changeToday = changeToday;
        this.changeInPercent = changeInPercent;
        this.currency = currency;
        this.financialProduct = financialProduct;
    }
}
