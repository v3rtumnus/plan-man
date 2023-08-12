package at.v3rtumnus.planman.dto.balance;

import at.v3rtumnus.planman.entity.balance.BalanceGroup;
import at.v3rtumnus.planman.entity.balance.BalanceGroupType;
import at.v3rtumnus.planman.entity.balance.BalanceItem;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Data
public class BalanceGroupDto {
    private Long id;
    private String name;
    private BalanceGroupType type;
    private List<BalanceItemDto> items;

    public static BalanceGroupDto fromEntity(BalanceGroup balanceGroup) {
        BalanceGroupDto balanceGroupDto = new BalanceGroupDto();
        BeanUtils.copyProperties(balanceGroup, balanceGroupDto);

        List<BalanceItemDto> items = new ArrayList<>();

        for (BalanceItem item : balanceGroup.getItems()) {
            items.add(BalanceItemDto.fromEntity(item));
        }

        balanceGroupDto.setItems(items);

        return balanceGroupDto;
    }

    public BigDecimal getSum() {
        return BigDecimal.valueOf(Optional.ofNullable(items).orElse(Collections.emptyList())
                .stream()
                .map(BalanceItemDto::getActiveDetail)
                .flatMap(Optional::stream)
                .mapToDouble(d -> d.getAmount().doubleValue())
                .sum());
    }
}