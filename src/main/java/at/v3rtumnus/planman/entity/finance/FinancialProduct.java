package at.v3rtumnus.planman.entity.finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
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
}
