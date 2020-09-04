package at.v3rtumnus.planman.dto.credit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditPlanRow {
    private long id;
    private LocalDate date;
    private String description;
    private BigDecimal balanceChange;
    private BigDecimal newBalance;
    private RowType rowType;
    private Long databaseId;

    public CreditPlanRow(long id, LocalDate date, BigDecimal balanceChange, BigDecimal newBalance, RowType rowType) {
        this(id, date, null, balanceChange, newBalance, rowType, null);
    }
}
