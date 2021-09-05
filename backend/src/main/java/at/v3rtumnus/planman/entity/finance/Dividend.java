package at.v3rtumnus.planman.entity.finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Dividend {
    @Id
    private Long id;

    private LocalDate transactionDate;
    private BigDecimal amount;

    @ManyToOne
    private FinancialProduct financialProduct;
}
