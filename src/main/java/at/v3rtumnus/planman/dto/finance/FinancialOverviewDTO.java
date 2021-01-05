package at.v3rtumnus.planman.dto.finance;

import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;

@Data
public class FinancialOverviewDTO {
    private BigDecimal purchasePriceTotal;
    private BigDecimal amountTotalDayBefore;
    private BigDecimal amountTotal;

    private BigDecimal changeToday;
    private BigDecimal changeTotal;
    private BigDecimal percentChangeToday;
    private BigDecimal percentChangeTotal;

    private List<FinancialProductDTO> activeProducts;

    public FinancialOverviewDTO() {
        this.activeProducts = new LinkedList<>();
    }
}
