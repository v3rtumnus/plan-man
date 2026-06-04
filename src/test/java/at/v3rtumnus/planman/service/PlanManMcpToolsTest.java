package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dto.balance.BalanceGroupDto;
import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.dto.expense.ExpenseSummary;
import at.v3rtumnus.planman.dto.insurance.InsuranceEntryDTO;
import at.v3rtumnus.planman.entity.balance.BalanceGroupType;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanManMcpToolsTest {

    @Mock
    private ExpenseService expenseService;

    @Mock
    private BalanceService balanceService;

    @Mock
    private InsuranceService insuranceService;

    @InjectMocks
    private PlanManMcpTools mcpTools;

    // --- getExpenseSummary ---

    @Test
    void getExpenseSummary_noCategoryFilter_returnsAllCategories() {
        when(expenseService.getExpenseSummaryForMonth(2026, 1)).thenReturn(List.of(
                new ExpenseSummary("Lebensmittel", new BigDecimal("300")),
                new ExpenseSummary("Transport", new BigDecimal("100"))
        ));

        Map<String, Object> result = mcpTools.getExpenseSummary(1, 2026, null);

        assertThat(result).containsKey("categories");
        List<?> categories = (List<?>) result.get("categories");
        assertThat(categories).hasSize(2);
        assertThat((Double) result.get("grandTotal")).isEqualTo(400.0);
    }

    @Test
    void getExpenseSummary_withCategoryFilter_returnsMatchingCategory() {
        when(expenseService.getExpenseSummaryForMonth(2026, 1)).thenReturn(List.of(
                new ExpenseSummary("Lebensmittel", new BigDecimal("300")),
                new ExpenseSummary("Transport", new BigDecimal("100"))
        ));

        Map<String, Object> result = mcpTools.getExpenseSummary(1, 2026, "Lebensmittel");

        List<?> categories = (List<?>) result.get("categories");
        assertThat(categories).hasSize(1);
        assertThat((Double) result.get("grandTotal")).isEqualTo(300.0);
    }

    @Test
    void getExpenseSummary_caseInsensitiveCategoryFilter() {
        when(expenseService.getExpenseSummaryForMonth(2026, 3)).thenReturn(List.of(
                new ExpenseSummary("Lebensmittel", new BigDecimal("200"))
        ));

        Map<String, Object> result = mcpTools.getExpenseSummary(3, 2026, "lebensmittel");

        List<?> categories = (List<?>) result.get("categories");
        assertThat(categories).hasSize(1);
    }

    @Test
    void getExpenseSummary_blankCategoryFilter_returnsAll() {
        when(expenseService.getExpenseSummaryForMonth(2026, 1)).thenReturn(List.of(
                new ExpenseSummary("A", new BigDecimal("100")),
                new ExpenseSummary("B", new BigDecimal("50"))
        ));

        Map<String, Object> result = mcpTools.getExpenseSummary(1, 2026, "  ");

        List<?> categories = (List<?>) result.get("categories");
        assertThat(categories).hasSize(2);
    }

    // --- getBalance ---

    @Test
    void getBalance_noAccountFilter_returnsAllGroups() {
        BalanceGroupDto incomeGroup = mock(BalanceGroupDto.class);
        when(incomeGroup.getName()).thenReturn("Gehalt");
        when(incomeGroup.getSum()).thenReturn(new BigDecimal("3000"));

        when(balanceService.retrieveBalanceGroups()).thenReturn(
                Map.of(BalanceGroupType.INCOME, List.of(incomeGroup))
        );

        Map<String, Object> result = mcpTools.getBalance(null);

        assertThat(result).containsKey("income");
        List<?> incomeList = (List<?>) result.get("income");
        assertThat(incomeList).hasSize(1);
    }

    @Test
    void getBalance_withAccountFilter_returnsOnlyMatchingGroup() {
        BalanceGroupDto group1 = mock(BalanceGroupDto.class);
        when(group1.getName()).thenReturn("Gehalt");
        when(group1.getSum()).thenReturn(new BigDecimal("3000"));

        BalanceGroupDto group2 = mock(BalanceGroupDto.class);
        when(group2.getName()).thenReturn("Miete");

        when(balanceService.retrieveBalanceGroups()).thenReturn(
                Map.of(BalanceGroupType.INCOME, List.of(group1, group2))
        );

        Map<String, Object> result = mcpTools.getBalance("Gehalt");

        List<?> incomeList = (List<?>) result.get("income");
        assertThat(incomeList).hasSize(1);
    }

    @Test
    void getBalance_emptyGroups_returnsEmptyLists() {
        when(balanceService.retrieveBalanceGroups()).thenReturn(Map.of());

        Map<String, Object> result = mcpTools.getBalance(null);

        assertThat(result).containsKey("income");
        assertThat(result).containsKey("expenditure");
        assertThat((List<?>) result.get("income")).isEmpty();
        assertThat((List<?>) result.get("expenditure")).isEmpty();
    }

    // --- getInsurancePolicies ---

    @Test
    void getInsurancePolicies_notActiveOnly_returnsAllEntries() {
        when(insuranceService.getInsuranceEntries(null, null, null)).thenReturn(
                List.of(entryDto(InsuranceEntryState.RECORDED), entryDto(InsuranceEntryState.DONE))
        );

        List<Map<String, Object>> result = mcpTools.getInsurancePolicies(false);

        assertThat(result).hasSize(2);
    }

    @Test
    void getInsurancePolicies_activeOnly_excludesDoneEntries() {
        when(insuranceService.getInsuranceEntries(null, null, null)).thenReturn(
                List.of(entryDto(InsuranceEntryState.RECORDED), entryDto(InsuranceEntryState.DONE))
        );

        List<Map<String, Object>> result = mcpTools.getInsurancePolicies(true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("state")).isEqualTo("eingetragen");
    }

    @Test
    void getInsurancePolicies_mapsFieldsCorrectly() {
        InsuranceEntryDTO dto = entryDto(InsuranceEntryState.RECORDED);
        dto.setAmount(new BigDecimal("120.00"));

        when(insuranceService.getInsuranceEntries(null, null, null)).thenReturn(List.of(dto));

        List<Map<String, Object>> result = mcpTools.getInsurancePolicies(false);

        assertThat(result).hasSize(1);
        Map<String, Object> entry = result.get(0);
        assertThat(entry.get("amount")).isEqualTo(new BigDecimal("120.00"));
        assertThat(entry).containsKey("date");
        assertThat(entry).containsKey("person");
    }

    // --- getTopExpenses ---

    @Test
    void getTopExpenses_sortsDescendingAndLimits() {
        when(expenseService.getExpensesForMonth(2026, 1)).thenReturn(List.of(
                expenseDto(1L, new BigDecimal("50")),
                expenseDto(2L, new BigDecimal("200")),
                expenseDto(3L, new BigDecimal("150"))
        ));

        List<Map<String, Object>> result = mcpTools.getTopExpenses(1, 2026, 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("amount")).isEqualTo(new BigDecimal("200"));
        assertThat(result.get(1).get("amount")).isEqualTo(new BigDecimal("150"));
    }

    @Test
    void getTopExpenses_limitLargerThanResults_returnsAll() {
        when(expenseService.getExpensesForMonth(2026, 2)).thenReturn(List.of(
                expenseDto(1L, new BigDecimal("100"))
        ));

        List<Map<String, Object>> result = mcpTools.getTopExpenses(2, 2026, 10);

        assertThat(result).hasSize(1);
    }

    @Test
    void getTopExpenses_mapsFieldsCorrectly() {
        ExpenseDTO dto = new ExpenseDTO(1L, new BigDecimal("99.99"), "Lebensmittel", "REWE", LocalDate.of(2026, 1, 15));
        when(expenseService.getExpensesForMonth(2026, 1)).thenReturn(List.of(dto));

        List<Map<String, Object>> result = mcpTools.getTopExpenses(1, 2026, 1);

        Map<String, Object> entry = result.get(0);
        assertThat(entry.get("category")).isEqualTo("Lebensmittel");
        assertThat(entry.get("comment")).isEqualTo("REWE");
        assertThat(entry.get("amount")).isEqualTo(new BigDecimal("99.99"));
        assertThat(entry.get("date")).isEqualTo("2026-01-15");
    }

    // --- helpers ---

    private InsuranceEntryDTO entryDto(InsuranceEntryState state) {
        InsuranceEntryDTO dto = new InsuranceEntryDTO();
        dto.setId(1L);
        dto.setEntryDate(LocalDate.of(2026, 1, 10));
        dto.setPerson("Alice");
        dto.setState(state);
        dto.setAmount(new BigDecimal("100.00"));
        return dto;
    }

    private ExpenseDTO expenseDto(Long id, BigDecimal amount) {
        return new ExpenseDTO(id, amount, "Kategorie", "Kommentar", LocalDate.of(2026, 1, 1));
    }
}
