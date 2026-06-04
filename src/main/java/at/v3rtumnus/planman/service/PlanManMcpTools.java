package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dto.expense.ExpenseSummary;
import at.v3rtumnus.planman.dto.insurance.InsuranceEntryDTO;
import at.v3rtumnus.planman.entity.balance.BalanceGroupType;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlanManMcpTools {

    private final ExpenseService expenseService;
    private final BalanceService balanceService;
    private final InsuranceService insuranceService;

    @Tool(description = "Get expense totals by category for a given month and year. Optionally filter by a specific category name.")
    public Map<String, Object> getExpenseSummary(int month, int year, @Nullable String category) {
        log.info("MCP: getExpenseSummary month={} year={} category={}", month, year, category);
        List<ExpenseSummary> summaries = expenseService.getExpenseSummaryForMonth(year, month);

        if (category != null && !category.isBlank()) {
            summaries = summaries.stream()
                    .filter(s -> s.getCategory().equalsIgnoreCase(category))
                    .toList();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("month", month);
        result.put("year", year);
        result.put("categories", summaries.stream()
                .map(s -> Map.of("category", s.getCategory(), "total", s.getAmount()))
                .toList());
        result.put("grandTotal", summaries.stream()
                .mapToDouble(s -> s.getAmount().doubleValue())
                .sum());
        return result;
    }

    @Tool(description = "Get current account balances grouped by type (INCOME/EXPENSE). Optionally filter by group name.")
    public Map<String, Object> getBalance(@Nullable String account) {
        log.info("MCP: getBalance account={}", account);
        var groups = balanceService.retrieveBalanceGroups();

        Map<String, Object> result = new HashMap<>();
        for (BalanceGroupType type : BalanceGroupType.values()) {
            var groupList = groups.getOrDefault(type, List.of());
            var filtered = (account != null && !account.isBlank())
                    ? groupList.stream().filter(g -> g.getName().equalsIgnoreCase(account)).toList()
                    : groupList;
            result.put(type.name().toLowerCase(), filtered.stream()
                    .map(g -> Map.of("group", g.getName(), "currentBalance", g.getSum()))
                    .toList());
        }
        return result;
    }

    @Tool(description = "List health/private insurance claim entries. Set activeOnly=true to exclude completed (DONE) entries.")
    public List<Map<String, Object>> getInsurancePolicies(boolean activeOnly) {
        log.info("MCP: getInsurancePolicies activeOnly={}", activeOnly);
        List<InsuranceEntryDTO> entries = insuranceService.getInsuranceEntries(null, null, null);

        if (activeOnly) {
            entries = entries.stream()
                    .filter(e -> e.getState() != InsuranceEntryState.DONE)
                    .toList();
        }

        return entries.stream()
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", e.getId());
                    map.put("date", e.getEntryDate().toString());
                    map.put("person", e.getPerson());
                    map.put("type", e.getType() != null ? e.getType().name() : null);
                    map.put("insuranceType", e.getInsuranceType() != null ? e.getInsuranceType().name() : null);
                    map.put("provider", e.getDoctor());
                    map.put("state", e.getState() != null ? e.getState().getLabel() : null);
                    map.put("amount", e.getAmount());
                    map.put("healthInsuranceAmount", e.getHealthInsuranceAmount());
                    map.put("privateInsuranceAmount", e.getPrivateInsuranceAmount());
                    map.put("retention", e.getRetention());
                    return map;
                })
                .toList();
    }

    @Tool(description = "Get the top N largest expense transactions for a given month and year, sorted by amount descending.")
    public List<Map<String, Object>> getTopExpenses(int month, int year, int limit) {
        log.info("MCP: getTopExpenses month={} year={} limit={}", month, year, limit);
        return expenseService.getExpensesForMonth(year, month).stream()
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("date", e.getDate() != null ? e.getDate().toString() : null);
                    map.put("category", e.getCategory());
                    map.put("comment", e.getComment());
                    map.put("amount", e.getAmount());
                    return map;
                })
                .toList();
    }
}
