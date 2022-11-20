package at.v3rtumnus.planman.entity.credit;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "CREDIT_INTERVAL")
@Data
@NoArgsConstructor
public class CreditInterval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column
    private BigDecimal interestRate;

    @Column
    private BigDecimal installment;

    @Column
    private BigDecimal fee;

    @Column(name = "VALID_UNTIL")
    private LocalDate validUntilDate;
}
