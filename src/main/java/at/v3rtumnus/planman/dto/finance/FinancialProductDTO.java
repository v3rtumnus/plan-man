package at.v3rtumnus.planman.dto.finance;

import at.v3rtumnus.planman.entity.finance.FinancialProductType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
public class FinancialProductDTO {
    private String isin;

    private String symbol;
    private String name;
    private FinancialProductType type;

    private BigDecimal currentQuantity;
    private BigDecimal currentPrice;
    private BigDecimal currentAmount;

    private BigDecimal combinedPurchasePrice;

    private BigDecimal changeToday;
    private BigDecimal changeTotal;
    private BigDecimal percentChangeToday;
    private BigDecimal percentChangeTotal;

    private List<FinancialTransactionDTO> transactions;

    public FinancialProductDTO(String isin, String name, FinancialProductType type) {
        this.isin = isin;
        this.name = name;
        this.type = type;
    }
}
