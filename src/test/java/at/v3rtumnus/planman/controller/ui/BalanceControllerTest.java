package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.entity.balance.BalanceGroupType;
import at.v3rtumnus.planman.service.BalanceService;
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
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(BalanceController.class)
class BalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BalanceService balanceService;

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
    void getBalanceOverview_returns200AndCorrectView() throws Exception {
        mockMvc.perform(get("/balance"))
                .andExpect(status().isOk())
                .andExpect(view().name("balance/overview"));
    }

    @Test
    void getBalanceDetail_returns200AndRendersGroups() throws Exception {
        when(balanceService.retrieveBalanceGroups()).thenReturn(Map.of(
                BalanceGroupType.INCOME, List.of(),
                BalanceGroupType.EXPENDITURE, List.of()
        ));

        mockMvc.perform(get("/balance/detail"))
                .andExpect(status().isOk())
                .andExpect(view().name("balance/fragments/details"));
    }

    @Test
    void getBalanceComparison_returns200AndRendersComparisons() throws Exception {
        when(balanceService.getBalanceComparisons()).thenReturn(List.of());

        mockMvc.perform(get("/balance/comparison"))
                .andExpect(status().isOk())
                .andExpect(view().name("balance/fragments/comparison"));
    }
}
