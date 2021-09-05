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
public class SavingsAccount {
    @Id
    private Long id;

    private LocalDate startDate;
    private LocalDate endDate;

    private BigDecimal amount;
    private BigDecimal interestRate;

    @Enumerated(EnumType.STRING)
    private Interval interval;
}
