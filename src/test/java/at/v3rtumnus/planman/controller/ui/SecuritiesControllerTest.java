package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.service.FinanceImportService;
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
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(SecuritiesController.class)
class SecuritiesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinanceService financeService;

    @MockitoBean
    private FinanceImportService financeImportService;

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
        when(financeService.retrieveActiveSavingPlans()).thenReturn(List.of());
        when(financeService.retrieveFinancialProducts()).thenReturn(List.of());

        mockMvc.perform(get("/securities/overview"))
                .andExpect(status().isOk())
                .andExpect(view().name("securities/overview"));
    }

    @Test
    void getFinancialTransactionHistory_returns200AndCorrectView() throws Exception {
        when(financeService.retrieveFinancialTransactions()).thenReturn(List.of());

        mockMvc.perform(get("/securities/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("securities/history"));
    }

    @Test
    void getFinanceUpload_returns200AndCorrectView() throws Exception {
        when(financeImportService.retrieveUploadLogs()).thenReturn(List.of());

        mockMvc.perform(get("/securities/upload"))
                .andExpect(status().isOk())
                .andExpect(view().name("securities/upload"));
    }
}
