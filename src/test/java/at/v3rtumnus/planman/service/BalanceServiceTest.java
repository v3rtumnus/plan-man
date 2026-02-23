package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.BalanceGroupRepository;
import at.v3rtumnus.planman.dao.BalanceItemDetailRepository;
import at.v3rtumnus.planman.dto.balance.BalanceComparisonDto;
import at.v3rtumnus.planman.dto.balance.BalanceGroupDto;
import at.v3rtumnus.planman.dto.balance.NewBalanceItemDto;
import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.entity.balance.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private BalanceGroupRepository balanceGroupRepository;

    @Mock
    private BalanceItemDetailRepository balanceDetailRepository;

    @Mock
    private ExpenseService expenseService;

    @InjectMocks
    private BalanceService balanceService;

    private BalanceGroup buildGroup(String name, BalanceGroupType type, BigDecimal amount) {
        BalanceGroup group = new BalanceGroup(name, type);
        BalanceItem item = new BalanceItem("Item", group);
        // active: begin yesterday, end null → is active today
        BalanceItemDetail detail = new BalanceItemDetail(amount, LocalDate.now().minusDays(1), null, item);
        item.setDetails(List.of(detail));
        group.setItems(List.of(item));
        return group;
    }

    // --- retrieveBalanceGroups ---

    @Test
    void retrieveBalanceGroups_sortsByValueDescending() {
        BalanceGroup g1 = buildGroup("Savings", BalanceGroupType.INCOME, new BigDecimal("100"));
        BalanceGroup g2 = buildGroup("Stocks", BalanceGroupType.INCOME, new BigDecimal("500"));

        when(balanceGroupRepository.findAll()).thenReturn(List.of(g1, g2));

        Map<BalanceGroupType, List<BalanceGroupDto>> result = balanceService.retrieveBalanceGroups();

        List<BalanceGroupDto> incomeGroups = result.get(BalanceGroupType.INCOME);
        assertThat(incomeGroups).isNotNull();
        assertThat(incomeGroups.get(0).getSum()).isGreaterThan(incomeGroups.get(1).getSum());
    }

    @Test
    void retrieveBalanceGroups_separatesIncomeFromExpenditure() {
        BalanceGroup income = buildGroup("Savings", BalanceGroupType.INCOME, new BigDecimal("200"));
        BalanceGroup expenditure = buildGroup("Rent", BalanceGroupType.EXPENDITURE, new BigDecimal("800"));

        when(balanceGroupRepository.findAll()).thenReturn(List.of(income, expenditure));

        Map<BalanceGroupType, List<BalanceGroupDto>> result = balanceService.retrieveBalanceGroups();

        assertThat(result).containsKey(BalanceGroupType.INCOME);
        assertThat(result).containsKey(BalanceGroupType.EXPENDITURE);
        assertThat(result.get(BalanceGroupType.INCOME)).hasSize(1);
        assertThat(result.get(BalanceGroupType.EXPENDITURE)).hasSize(1);
    }

    // --- saveBalanceItem: new group ---

    @Test
    void saveBalanceItem_noExistingGroup_createsNewGroup() {
        NewBalanceItemDto dto = new NewBalanceItemDto();
        dto.setName("Savings");
        dto.setGroup("Bank");
        dto.setType(BalanceGroupType.INCOME);
        dto.setAmount(new BigDecimal("1000"));
        dto.setDate(LocalDate.now());

        when(balanceGroupRepository.find(BalanceGroupType.INCOME, "Bank")).thenReturn(Optional.empty());

        balanceService.saveBalanceItem(dto);

        ArgumentCaptor<BalanceGroup> captor = ArgumentCaptor.forClass(BalanceGroup.class);
        verify(balanceGroupRepository).save(captor.capture());
        BalanceGroup saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Bank");
        assertThat(saved.getType()).isEqualTo(BalanceGroupType.INCOME);
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getName()).isEqualTo("Savings");
    }

    // --- saveBalanceItem: existing group, new item ---

    @Test
    void saveBalanceItem_existingGroupNewItem_addsItemToGroup() {
        BalanceGroup existingGroup = new BalanceGroup("Bank", BalanceGroupType.INCOME);
        existingGroup.setItems(new ArrayList<>());

        NewBalanceItemDto dto = new NewBalanceItemDto();
        dto.setName("NewSavings");
        dto.setGroup("Bank");
        dto.setType(BalanceGroupType.INCOME);
        dto.setAmount(new BigDecimal("500"));
        dto.setDate(LocalDate.now());

        when(balanceGroupRepository.find(BalanceGroupType.INCOME, "Bank")).thenReturn(Optional.of(existingGroup));

        balanceService.saveBalanceItem(dto);

        assertThat(existingGroup.getItems()).hasSize(1);
        assertThat(existingGroup.getItems().get(0).getName()).isEqualTo("NewSavings");
        verify(balanceGroupRepository).save(existingGroup);
    }

    // --- saveBalanceItem: existing group, existing item → closes previous detail ---

    @Test
    void saveBalanceItem_existingGroupExistingItem_closesPreviousDetail() {
        LocalDate existingDate = LocalDate.of(2024, 1, 1);
        LocalDate newDate = LocalDate.of(2024, 6, 1);

        BalanceGroup existingGroup = new BalanceGroup("Bank", BalanceGroupType.INCOME);
        BalanceItem existingItem = new BalanceItem("Savings", existingGroup);
        BalanceItemDetail existingDetail = new BalanceItemDetail(
                new BigDecimal("1000"), existingDate, null, existingItem);
        existingItem.setDetails(new ArrayList<>(List.of(existingDetail)));
        existingGroup.setItems(new ArrayList<>(List.of(existingItem)));

        NewBalanceItemDto dto = new NewBalanceItemDto();
        dto.setName("Savings");
        dto.setGroup("Bank");
        dto.setType(BalanceGroupType.INCOME);
        dto.setAmount(new BigDecimal("1500"));
        dto.setDate(newDate);

        when(balanceGroupRepository.find(BalanceGroupType.INCOME, "Bank")).thenReturn(Optional.of(existingGroup));

        balanceService.saveBalanceItem(dto);

        // Previous detail should be closed: end = newDate - 1
        assertThat(existingDetail.getEnd()).isEqualTo(newDate.minusDays(1));
        // New detail should have been added
        assertThat(existingItem.getDetails()).hasSize(2);
    }

    // --- getBalanceComparisons ---

    @Test
    void getBalanceComparisons_startsFromLatestOfBothDates() {
        LocalDate balanceStart = LocalDate.now().minusMonths(2).withDayOfMonth(1);
        LocalDate expenseStart = LocalDate.now().minusMonths(1).withDayOfMonth(1);

        when(balanceDetailRepository.getMinimumBalanceDate()).thenReturn(balanceStart);
        when(expenseService.getMinimumExpenseDate()).thenReturn(expenseStart);
        when(expenseService.getExpensesForMonth(anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(balanceDetailRepository.getRelevantAmountsForDate(any())).thenReturn(Collections.emptyList());

        List<BalanceComparisonDto> result = balanceService.getBalanceComparisons();

        // Should start from the later date (expense start = 1 month ago)
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getDate()).isEqualTo(expenseStart);
    }

    @Test
    void getBalanceComparisons_incomeGroupContributesPositively() {
        LocalDate start = LocalDate.now().minusMonths(1).withDayOfMonth(1);

        BalanceGroup incomeGroup = buildGroup("Salary", BalanceGroupType.INCOME, new BigDecimal("2000"));
        BalanceItem item = incomeGroup.getItems().get(0);
        BalanceItemDetail detail = item.getDetails().get(0);
        detail.setItem(item); // ensure item reference is set

        when(balanceDetailRepository.getMinimumBalanceDate()).thenReturn(start);
        when(expenseService.getMinimumExpenseDate()).thenReturn(start);
        when(expenseService.getExpensesForMonth(anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(balanceDetailRepository.getRelevantAmountsForDate(any())).thenReturn(List.of(detail));

        List<BalanceComparisonDto> result = balanceService.getBalanceComparisons();

        // At least one entry, and balance sum should be positive (income)
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getBalanceSum()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    void getBalanceComparisons_expenditureGroupContributesNegatively() {
        LocalDate start = LocalDate.now().minusMonths(1).withDayOfMonth(1);

        BalanceGroup expendGroup = buildGroup("Rent", BalanceGroupType.EXPENDITURE, new BigDecimal("800"));
        BalanceItem item = expendGroup.getItems().get(0);
        BalanceItemDetail detail = item.getDetails().get(0);

        when(balanceDetailRepository.getMinimumBalanceDate()).thenReturn(start);
        when(expenseService.getMinimumExpenseDate()).thenReturn(start);
        when(expenseService.getExpensesForMonth(anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(balanceDetailRepository.getRelevantAmountsForDate(any())).thenReturn(List.of(detail));

        List<BalanceComparisonDto> result = balanceService.getBalanceComparisons();

        // Balance sum should be negative (expenditure)
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getBalanceSum()).isLessThanOrEqualTo(BigDecimal.ZERO);
    }
}
