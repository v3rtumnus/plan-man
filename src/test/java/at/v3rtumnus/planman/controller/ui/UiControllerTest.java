package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.entity.finance.FinancialSnapshot;
import at.v3rtumnus.planman.service.BalanceService;
import at.v3rtumnus.planman.service.CreditService;
import at.v3rtumnus.planman.service.ExpenseService;
import at.v3rtumnus.planman.service.FinanceService;
import at.v3rtumnus.planman.service.InsuranceService;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import at.v3rtumnus.planman.service.ThymeleafService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(UiController.class)
class UiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlanManUserDetailsService userDetailsService;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private FinanceService financeService;

    @MockitoBean
    private BalanceService balanceService;

    @MockitoBean
    private CreditService creditService;

    @MockitoBean
    private InsuranceService insuranceService;

    @MockitoBean
    private ExpenseService expenseService;

    @TestConfiguration
    static class Config {
        @Bean
        ThymeleafService thymeleafService() {
            return Mockito.mock(ThymeleafService.class);
        }
    }

    @Test
    @WithMockUser
    void homePage_returns200AndCorrectView() throws Exception {
        // UiController's @ModelAttribute reads SecurityContextHolder — needs @WithMockUser
        FinancialSnapshot snapshot = new FinancialSnapshot(
                LocalDate.of(2024, 1, 1),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE
        );
        when(financeService.getFinancialSnapshots()).thenReturn(List.of(snapshot));
        when(insuranceService.getInsuranceEntries(null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    @WithMockUser
    void login_returnsLoginView() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }
}
