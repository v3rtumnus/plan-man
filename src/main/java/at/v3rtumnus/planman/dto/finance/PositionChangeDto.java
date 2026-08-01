package at.v3rtumnus.planman.dto.finance;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PositionChangeDto {
    private String label;
    private boolean savings;
    private PositionVariant variant;
    private BigDecimal currentValue;
    private BigDecimal oneMonthChange;
    private BigDecimal oneMonthPercent;
    private BigDecimal oneYearChange;
    private BigDecimal oneYearPercent;
    private BigDecimal threeYearChange;
    private BigDecimal threeYearPercent;
}
