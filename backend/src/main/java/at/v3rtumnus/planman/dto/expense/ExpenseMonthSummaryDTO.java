package at.v3rtumnus.planman.dto.expense;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class ExpenseMonthSummaryDTO {
    private List<ExpenseSummaryDTO> expenseSummaries;
    private BigDecimal monthlySum;
}
