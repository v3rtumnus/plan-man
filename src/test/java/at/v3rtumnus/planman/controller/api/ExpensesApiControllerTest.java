package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.service.ExpenseService;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.cache.CacheManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpensesApiController.class)
class ExpensesApiControllerTest {

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

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @WithMockUser
    void postExpense_authenticatedUser_returns200() throws Exception {
        ExpenseDTO dto = new ExpenseDTO(null, BigDecimal.TEN, "Food", "Lunch", LocalDate.now());

        mockMvc.perform(post("/api/expenses")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(expenseService).saveExpense(any(ExpenseDTO.class));
    }

    @Test
    @WithMockUser
    void deleteExpense_authenticatedUser_returns200() throws Exception {
        mockMvc.perform(delete("/api/expenses/42"))
                .andExpect(status().isOk());

        verify(expenseService).deleteExpense(42L);
    }

    @Test
    @WithMockUser
    void putExpenses_authenticatedUser_returns200() throws Exception {
        List<ExpenseDTO> expenses = List.of(
                new ExpenseDTO(1L, BigDecimal.TEN, "Food", "Lunch", LocalDate.now())
        );

        mockMvc.perform(put("/api/expenses")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expenses)))
                .andExpect(status().isOk());

        verify(expenseService).updateExpenses(anyList());
    }

}
