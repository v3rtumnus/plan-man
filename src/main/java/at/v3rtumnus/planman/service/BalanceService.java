package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.BalanceGroupRepository;
import at.v3rtumnus.planman.dao.BalanceItemDetailRepository;
import at.v3rtumnus.planman.dto.balance.BalanceComparisonDto;
import at.v3rtumnus.planman.dto.balance.BalanceGroupDto;
import at.v3rtumnus.planman.dto.balance.NewBalanceItemDto;
import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.entity.balance.BalanceGroup;
import at.v3rtumnus.planman.entity.balance.BalanceGroupType;
import at.v3rtumnus.planman.entity.balance.BalanceItem;
import at.v3rtumnus.planman.entity.balance.BalanceItemDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceService {

    private final ExpenseService expenseService;
    private final BalanceGroupRepository balanceGroupRepository;
    private final BalanceItemDetailRepository balanceDetailRepository;

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

    public List<BalanceComparisonDto> getBalanceComparisons() {
        Optional<LocalDate> startDateOptional = Stream.of(balanceDetailRepository.getMinimumBalanceDate().withDayOfMonth(1),
                        expenseService.getMinimumExpenseDate().withDayOfMonth(1))
                .max(LocalDate::compareTo);

        LocalDate date = startDateOptional.get();
        List<BalanceComparisonDto> comparisons = new ArrayList<>();

        while (date.isBefore(LocalDate.now())) {
            BigDecimal expenses = BigDecimal.valueOf(expenseService.getExpensesForMonth(date.getYear(), date.getMonth().getValue())
                    .stream()
                    .mapToDouble(e -> e.getAmount().doubleValue())
                    .sum());

            BigDecimal balanceSum = BigDecimal.valueOf(balanceDetailRepository.getRelevantAmountsForDate(date)
                    .stream()
                    .mapToDouble(b -> {
                        if (b.getItem().getGroup().getType() == BalanceGroupType.INCOME) {
                            return b.getAmount().doubleValue();
                        } else {
                            return b.getAmount().multiply(BigDecimal.valueOf(-1)).doubleValue();
                        }
                    })
                    .sum());

            comparisons.add(new BalanceComparisonDto(date, balanceSum, expenses));

            date = date.plusMonths(1);
        }

        return comparisons;
    }
}
