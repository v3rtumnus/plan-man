package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.service.ExpenseService;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import at.v3rtumnus.planman.dto.expense.ExpenseSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ExpensesController.class)
class ExpensesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService expenseService;

    @MockitoBean
    private PlanManUserDetailsService userDetailsService;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void getExpenses_returns200AndCorrectView() throws Exception {
        when(expenseService.getMinimumExpenseDate()).thenReturn(LocalDate.now().minusMonths(1));
        when(expenseService.getExpenseCategories()).thenReturn(List.of());

        mockMvc.perform(get("/expenses"))
                .andExpect(status().isOk())
                .andExpect(view().name("expenses/overview"));
    }

    @Test
    void getExpensesMonthlyOverview_withParams_returns200() throws Exception {
        when(expenseService.getExpenseSummaryForMonth(2024, 3)).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/expenses/monthly").param("year", "2024").param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("expenses/fragments/monthly"));
    }

    @Test
    void getExpensesPie_withParams_returns200() throws Exception {
        when(expenseService.getExpenseSummaryForMonth(2024, 3)).thenReturn(List.of());

        mockMvc.perform(get("/expenses/pie").param("year", "2024").param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("expenses/fragments/pie"));
    }

    @Test
    void getExpensesGraph_returns200() throws Exception {
        when(expenseService.getExpenseSummariesForLastMonths(any())).thenReturn(new TreeMap<>());

        mockMvc.perform(get("/expenses/graph"))
                .andExpect(status().isOk())
                .andExpect(view().name("expenses/fragments/graph"));
    }

    @Test
    void getExpenseDetails_returns200AndCorrectView() throws Exception {
        when(expenseService.getMinimumExpenseDate()).thenReturn(LocalDate.now().minusMonths(1));
        when(expenseService.getExpenseCategories()).thenReturn(List.of());

        mockMvc.perform(get("/expenses/detail"))
                .andExpect(status().isOk())
                .andExpect(view().name("expenses/detail"));
    }

    @Test
    void getExpensesMonthlyDetails_withParams_returns200() throws Exception {
        when(expenseService.getExpensesForMonth(2024, 3)).thenReturn(List.of());

        mockMvc.perform(get("/expenses/detail/monthly").param("year", "2024").param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("expenses/fragments/details"));
    }

    @Test
    void getExpensesGraph_withData_coversLoopLogic() throws Exception {
        ExpenseSummary groceries = new ExpenseSummary("Groceries", BigDecimal.valueOf(200));
        ExpenseSummary transport = new ExpenseSummary("Transport", BigDecimal.valueOf(100));

        TreeMap<LocalDate, List<ExpenseSummary>> data = new TreeMap<>();
        data.put(LocalDate.of(2024, 1, 1), List.of(groceries, transport));
        data.put(LocalDate.of(2024, 2, 1), List.of(groceries));

        when(expenseService.getExpenseSummariesForLastMonths(any())).thenReturn(data);

        mockMvc.perform(get("/expenses/graph"))
                .andExpect(status().isOk())
                .andExpect(view().name("expenses/fragments/graph"));
    }
}
