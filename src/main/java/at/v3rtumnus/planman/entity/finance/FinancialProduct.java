package at.v3rtumnus.planman.entity.finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;

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
}
