package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.finance.UploadResult;
import at.v3rtumnus.planman.dto.finance.UploadResultDto;
import at.v3rtumnus.planman.service.FinanceImportService;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.cache.CacheManager;

import javax.sql.DataSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FinanceApiController.class)
class FinanceApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinanceImportService financeImportService;

    @MockitoBean
    private PlanManUserDetailsService userDetailsService;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private CacheManager cacheManager;

    @Test
    @WithMockUser
    void uploadPdfFile_validPdf_callsImportService() throws Exception {
        UploadResultDto dto = UploadResultDto.builder()
                .result(UploadResult.SUCCESS)
                .filename("test.pdf")
                .build();

        when(financeImportService.importFinanceFile(any())).thenReturn(dto);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/finance").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"));

        verify(financeImportService).importFinanceFile(any());
    }

    @Test
    @WithMockUser
    void uploadNonPdfFile_wrongContentType_returnsFailureWithoutCallingService() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/finance").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("FAILURE"))
                .andExpect(jsonPath("$.error").value("Wrong file type"));

        verify(financeImportService, never()).importFinanceFile(any());
    }

}
