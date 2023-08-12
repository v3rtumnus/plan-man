package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.BalanceGroupRepository;
import at.v3rtumnus.planman.dto.balance.BalanceGroupDto;
import at.v3rtumnus.planman.dto.balance.NewBalanceItemDto;
import at.v3rtumnus.planman.entity.balance.BalanceGroup;
import at.v3rtumnus.planman.entity.balance.BalanceGroupType;
import at.v3rtumnus.planman.entity.balance.BalanceItem;
import at.v3rtumnus.planman.entity.balance.BalanceItemDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceService {

    private final BalanceGroupRepository balanceGroupRepository;

    public Map<BalanceGroupType, List<BalanceGroupDto>> retrieveBalanceGroups() {
        log.info("Retrieving balance groups");
        return balanceGroupRepository.findAll()
                .stream()
                .map(BalanceGroupDto::fromEntity)
                .sorted(Comparator.comparing(BalanceGroupDto::getSum).reversed())
                .collect(Collectors.groupingBy(BalanceGroupDto::getType));
    }

    public void saveBalanceItem(NewBalanceItemDto balanceItem) {
        log.info("Saving balance item with name {}", balanceItem.getName());

        Optional<BalanceGroup> optionalBalanceGroup = balanceGroupRepository.find(balanceItem.getType(), balanceItem.getGroup());

        BalanceGroup balanceGroup;
        if (optionalBalanceGroup.isPresent()) {
            balanceGroup = optionalBalanceGroup.get();

            Optional<BalanceItem> optionalItem = balanceGroup.getItems()
                    .stream()
                    .filter(i -> i.getName().equals(balanceItem.getName()))
                    .findFirst();

            if (optionalItem.isPresent()) {
                BalanceItem existingBalanceItem = optionalItem.get();

                BalanceItemDetail lastDetail = existingBalanceItem.getDetails().get(existingBalanceItem.getDetails().size() - 1);
                lastDetail.setEnd(balanceItem.getDate().minusDays(1));

                existingBalanceItem.getDetails().add(new BalanceItemDetail(balanceItem.getAmount(), balanceItem.getDate(), null, existingBalanceItem));
            } else {
                BalanceItem newBalanceItem = new BalanceItem(balanceItem.getName(), balanceGroup);
                newBalanceItem.setDetails(Collections.singletonList(new BalanceItemDetail(
                        balanceItem.getAmount(), balanceItem.getDate(), null, newBalanceItem
                )));

                balanceGroup.getItems().add(newBalanceItem);
            }
        } else {
            balanceGroup = new BalanceGroup(balanceItem.getGroup(), balanceItem.getType());
            BalanceItem item = new BalanceItem(balanceItem.getName(), balanceGroup);
            BalanceItemDetail detail = new BalanceItemDetail(balanceItem.getAmount(), balanceItem.getDate(), null, item);

            item.setDetails(Collections.singletonList(detail));
            balanceGroup.setItems(Collections.singletonList(item));
        }

        balanceGroupRepository.save(balanceGroup);
    }
}
