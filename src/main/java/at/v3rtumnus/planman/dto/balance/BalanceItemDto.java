package at.v3rtumnus.planman.dto.balance;

import at.v3rtumnus.planman.entity.balance.BalanceItem;
import at.v3rtumnus.planman.entity.balance.BalanceItemDetail;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Data
public class BalanceItemDto {
    private Long id;
    private String name;
    private BalanceGroupDto group;
    private List<BalanceItemDetailDto> details;

    public static BalanceItemDto fromEntity(BalanceItem balanceItem) {
        BalanceItemDto balanceItemDto = new BalanceItemDto();
        BeanUtils.copyProperties(balanceItem, balanceItemDto);

        List<BalanceItemDetailDto> details = new ArrayList<>();

        for (BalanceItemDetail detail : balanceItem.getDetails()) {
            details.add(BalanceItemDetailDto.fromEntity(detail));
        }

        balanceItemDto.setDetails(details);

        return balanceItemDto;
    }

    public Optional<BalanceItemDetailDto> getActiveDetail() {
        return Optional.ofNullable(details).orElse(Collections.emptyList())
                .stream().filter(d ->
                    d.getBegin().isBefore(LocalDate.now().plusDays(1)) && (d.getEnd() == null || d.getEnd().isAfter(LocalDate.now()))
                )
                .findFirst();
    }
}