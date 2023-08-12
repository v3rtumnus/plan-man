package at.v3rtumnus.planman.dto.balance;

import at.v3rtumnus.planman.entity.balance.BalanceGroupType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class NewBalanceItemDto {
    private LocalDate date;
    private String name;
    private BigDecimal amount;
    private String group;
    private BalanceGroupType type;

}
