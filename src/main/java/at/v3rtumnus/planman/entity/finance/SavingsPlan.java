package at.v3rtumnus.planman.entity.finance;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import java.math.BigDecimal;
import java.time.LocalDate;

public class SavingsPlan {
    @Id
    private Long id;

    private LocalDate startDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    private SavingsPlanInterval interval;

    private BigDecimal amount;

    @OneToOne
    private FinancialProduct financialProduct;
}
