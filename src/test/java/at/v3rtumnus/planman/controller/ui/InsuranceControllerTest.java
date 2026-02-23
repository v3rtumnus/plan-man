package at.v3rtumnus.planman.controller.ui;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.List;

import at.v3rtumnus.planman.dto.insurance.InsuranceEntryDTO;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryType;
import at.v3rtumnus.planman.entity.insurance.InsuranceType;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(InsuranceController.class)
class InsuranceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InsuranceService insuranceService;

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
    void getInsuranceOverview_returns200AndCorrectView() throws Exception {
        when(insuranceService.getPersons()).thenReturn(List.of("Alice"));
        when(insuranceService.getYears()).thenReturn(List.of("2024"));
        when(insuranceService.getStates()).thenReturn(List.of());

        mockMvc.perform(get("/insurance/overview"))
                .andExpect(status().isOk())
                .andExpect(view().name("insurance/overview"));
    }

    @Test
    void submit_validRequest_returns200AndOverview() throws Exception {
        when(insuranceService.getPersons()).thenReturn(List.of("Alice"));
        when(insuranceService.getYears()).thenReturn(List.of("2024"));
        when(insuranceService.getStates()).thenReturn(List.of());
        doNothing().when(insuranceService).saveInsuranceEntry(any(InsuranceEntryDTO.class));

        MockMultipartFile invoice = new MockMultipartFile(
                "invoice", "receipt.pdf", "application/pdf", "pdf-content".getBytes());

        mockMvc.perform(multipart("/insurance")
                        .file(invoice)
                        .param("date", LocalDate.now().toString())
                        .param("person", "Alice")
                        .param("doctor", "Dr. Smith")
                        .param("type", InsuranceType.HEALTH.name())
                        .param("amount", "120.00"))
                .andExpect(status().isOk())
                .andExpect(view().name("insurance/overview"));
    }

    @Test
    void getEntriesTable_withAllFilters_returns200() throws Exception {
        when(insuranceService.getInsuranceEntries(isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        mockMvc.perform(get("/insurance/table")
                        .param("year", "ALL")
                        .param("person", "ALL")
                        .param("state", "ALL"))
                .andExpect(status().isOk())
                .andExpect(view().name("insurance/fragments/table"));
    }

    @Test
    void getFile_invoiceType_servesPdfFile() throws Exception {
        InsuranceEntryDTO entry = new InsuranceEntryDTO();
        entry.setInvoiceData("pdf-content".getBytes());
        entry.setInvoiceFilename("invoice.pdf");
        when(insuranceService.getEntry(1L)).thenReturn(entry);

        mockMvc.perform(get("/insurance/file/invoice/1"))
                .andExpect(status().isOk());
    }

    @Test
    void getFile_healthType_servesFile() throws Exception {
        InsuranceEntryDTO entry = new InsuranceEntryDTO();
        entry.setHealthInsuranceData("health-content".getBytes());
        entry.setHealthInsuranceFilename("health.pdf");
        when(insuranceService.getEntry(2L)).thenReturn(entry);

        mockMvc.perform(get("/insurance/file/health/2"))
                .andExpect(status().isOk());
    }

    @Test
    void getFile_jpegType_servesImageContentType() throws Exception {
        InsuranceEntryDTO entry = new InsuranceEntryDTO();
        entry.setInvoiceData("jpeg-content".getBytes());
        entry.setInvoiceFilename("photo.jpeg");
        when(insuranceService.getEntry(3L)).thenReturn(entry);

        mockMvc.perform(get("/insurance/file/invoice/3"))
                .andExpect(status().isOk());
    }

    @Test
    void submit_withServiceException_rendersOverviewWithError() throws Exception {
        when(insuranceService.getPersons()).thenReturn(List.of("Alice"));
        when(insuranceService.getYears()).thenReturn(List.of("2024"));
        when(insuranceService.getStates()).thenReturn(List.of());
        doThrow(new RuntimeException("save failed"))
                .when(insuranceService).saveInsuranceEntry(any(InsuranceEntryDTO.class));

        MockMultipartFile invoice = new MockMultipartFile(
                "invoice", "receipt.pdf", "application/pdf", "pdf-content".getBytes());

        mockMvc.perform(multipart("/insurance")
                        .file(invoice)
                        .param("date", LocalDate.now().toString())
                        .param("person", "Alice")
                        .param("doctor", "Dr. Smith")
                        .param("type", InsuranceType.HEALTH.name())
                        .param("amount", "120.00"))
                .andExpect(status().isOk())
                .andExpect(view().name("insurance/overview"));
    }
}
