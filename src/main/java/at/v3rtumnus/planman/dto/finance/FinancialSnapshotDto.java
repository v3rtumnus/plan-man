package at.v3rtumnus.planman.dto.finance;

import at.v3rtumnus.planman.entity.finance.FinancialSnapshot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FinancialSnapshotDto {
    private LocalDate date;
    private BigDecimal sharesSum;
    private BigDecimal fundsSum;
    private BigDecimal etfSum;
    private BigDecimal savingsSum;
    private BigDecimal creditSum;

    public BigDecimal getGrossAssets() {
        return sharesSum.add(fundsSum).add(etfSum).add(savingsSum);
    }

    public BigDecimal getNetAssets() {
        return getGrossAssets().add(creditSum);
    }

    public static FinancialSnapshotDto fromEntity(FinancialSnapshot snapshot) {
        FinancialSnapshotDto dto = new FinancialSnapshotDto();

        BeanUtils.copyProperties(snapshot, dto);

        return dto;
    }
}
