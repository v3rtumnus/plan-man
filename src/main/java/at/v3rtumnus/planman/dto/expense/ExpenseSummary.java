package at.v3rtumnus.planman.dto.expense;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class ExpenseSummary {
    private String category;
    private BigDecimal amount;
}
