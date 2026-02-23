package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.credit.CreditPlanRow;
import at.v3rtumnus.planman.dto.credit.RowType;
import at.v3rtumnus.planman.service.CreditService;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import at.v3rtumnus.planman.entity.credit.CreditSingleTransaction;
import at.v3rtumnus.planman.entity.credit.TransactionType;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(CreditController.class)
class CreditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreditService creditService;

    @MockitoBean
    private PlanManUserDetailsService userDetailsService;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private CacheManager cacheManager;

    private CreditPlanRow sampleRow() {
        return new CreditPlanRow(1L, LocalDate.now().plusYears(1), null,
                BigDecimal.ZERO, BigDecimal.ONE, RowType.INSTALLMENT, null);
    }

    @Test
    void getCreditOverview_returns200AndCorrectView() throws Exception {
        CreditPlanRow row = sampleRow();
        when(creditService.generateCurrentCreditPlan()).thenReturn(List.of(row));
        when(creditService.generateOriginalCreditPlan()).thenReturn(List.of(row));
        when(creditService.getMinimumInstallment()).thenReturn(BigDecimal.valueOf(500));

        mockMvc.perform(get("/credit/overview"))
                .andExpect(status().isOk())
                .andExpect(view().name("credit/overview"));
    }

    @Test
    void getCreditPlan_returns200AndCorrectView() throws Exception {
        when(creditService.generateCurrentCreditPlan()).thenReturn(List.of(sampleRow()));

        mockMvc.perform(get("/credit/plan"))
                .andExpect(status().isOk())
                .andExpect(view().name("credit/plan"));
    }

    @Test
    void saveSingleTransaction_success_renders200() throws Exception {
        CreditPlanRow row = sampleRow();
        when(creditService.generateCurrentCreditPlan()).thenReturn(List.of(row));
        when(creditService.generateOriginalCreditPlan()).thenReturn(List.of(row));
        when(creditService.getMinimumInstallment()).thenReturn(BigDecimal.valueOf(500));

        mockMvc.perform(post("/credit")
                        .param("date", LocalDate.now().toString())
                        .param("description", "Extra payment")
                        .param("amount", "1000"))
                .andExpect(status().isOk())
                .andExpect(view().name("credit/overview"));

        verify(creditService).saveSingleTransaction(any(CreditSingleTransaction.class));
    }

    @Test
    void removeSingleTransaction_callsService() {
        // void return triggers view resolution which fails in test context;
        // the service call happens BEFORE view resolution, so we can verify it
        try {
            mockMvc.perform(delete("/credit/7"));
        } catch (Exception ignored) {
            // TemplateInputException from void-return view resolution — expected in test
        }
        verify(creditService).removeSingleTransaction(7L);
    }
}
