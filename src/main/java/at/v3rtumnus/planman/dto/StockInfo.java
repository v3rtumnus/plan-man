package at.v3rtumnus.planman.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class StockInfo {
    private BigDecimal quote;
    private BigDecimal changeToday;
    private BigDecimal changeInPercent;
    private String currency;
}
