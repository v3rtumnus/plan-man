package at.v3rtumnus.planman.entity.finance;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@NoArgsConstructor
public class FinancialSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate snapshotDate;
    private BigDecimal sharesSum;
    private BigDecimal fundsSum;
    private BigDecimal etfSum;
    private BigDecimal savingsSum;
    private BigDecimal creditSum;

    public FinancialSnapshot(LocalDate snapshotDate, BigDecimal sharesSum, BigDecimal fundsSum, BigDecimal etfSum, BigDecimal savingsSum, BigDecimal creditSum) {
        this.snapshotDate = snapshotDate;
        this.sharesSum = sharesSum;
        this.fundsSum = fundsSum;
        this.etfSum = etfSum;
        this.savingsSum = savingsSum;
        this.creditSum = creditSum;
    }
}
