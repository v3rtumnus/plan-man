package at.v3rtumnus.planman.dto.finance;

import at.v3rtumnus.planman.entity.finance.FinancialProductType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class FinancialProductDTO {
    private String isin;

    private String symbol;
    private String name;
    private FinancialProductType type;

    private BigDecimal currentQuantity;
    private BigDecimal currentPrice;

    private BigDecimal combinedPurchasePrice;

    private BigDecimal changeToday;
    private BigDecimal percentChangeToday;
    private BigDecimal percentChangeTotal;

    private List<FinancialTransactionDTO> transactions;
}
