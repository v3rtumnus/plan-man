package at.v3rtumnus.planman.entity.finance;

import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class FinancialProduct {

    @Id
    private String isin;

    private String symbol;
    private String name;

    @Enumerated(EnumType.STRING)
    private FinancialProductType type;

    @OneToMany(mappedBy = "financialProduct")
    private List<FinancialTransaction> transactions;

    @OneToMany(mappedBy = "financialProduct")
    private List<Dividend> dividends;

    public FinancialProduct(String isin) {
        this.isin = isin;
    }
}
