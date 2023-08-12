package at.v3rtumnus.planman.dto.balance;

import at.v3rtumnus.planman.entity.balance.BalanceItem;
import at.v3rtumnus.planman.entity.balance.BalanceItemDetail;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BalanceItemDetailDto {
    private Long id;

    private BigDecimal amount;
    private LocalDate begin;
    private LocalDate end;

    private BalanceItemDto item;

    public static BalanceItemDetailDto fromEntity(BalanceItemDetail balanceItemDetail) {
        BalanceItemDetailDto balanceItemDetailDto = new BalanceItemDetailDto();
        BeanUtils.copyProperties(balanceItemDetail, balanceItemDetailDto);

        return balanceItemDetailDto;
    }
}