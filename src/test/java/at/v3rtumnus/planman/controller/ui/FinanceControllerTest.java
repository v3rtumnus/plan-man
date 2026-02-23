package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.entity.finance.FinancialSnapshot;
import at.v3rtumnus.planman.service.FinanceService;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import at.v3rtumnus.planman.service.ThymeleafService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(FinanceController.class)
class FinanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinanceService financeService;

    @MockitoBean
    private PlanManUserDetailsService userDetailsService;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private CacheManager cacheManager;

    @TestConfiguration
    static class Config {
        @Bean
        ThymeleafService thymeleafService() {
            return Mockito.mock(ThymeleafService.class);
        }
    }

    @Test
    void getOverview_returns200AndCorrectView() throws Exception {
        // Snapshot on day 1 so it passes the graph filter (getDayOfMonth() == 1)
        FinancialSnapshot snapshot = new FinancialSnapshot(
                LocalDate.of(2024, 1, 1),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE
        );
        when(financeService.getFinancialSnapshots()).thenReturn(List.of(snapshot));

        mockMvc.perform(get("/finance/overview"))
                .andExpect(status().isOk())
                .andExpect(view().name("finance/overview"));
    }

    @Test
    void saveSavingsAmount_callsServiceAndRedirects() throws Exception {
        mockMvc.perform(post("/finance/savingsAmount").param("savingsAmount", "10000"))
                .andExpect(status().is3xxRedirection());

        verify(financeService).updateSavingsAmount("10000");
    }
}
