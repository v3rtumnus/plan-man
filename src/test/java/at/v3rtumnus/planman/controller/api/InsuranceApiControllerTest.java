package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;
import at.v3rtumnus.planman.entity.insurance.InsuranceType;
import at.v3rtumnus.planman.service.InsuranceService;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.cache.CacheManager;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InsuranceApiController.class)
class InsuranceApiControllerTest {

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

    @Test
    @WithMockUser
    void updateState_recordedState_transitionsToWaitingForHealthInsurance() throws Exception {
        mockMvc.perform(put("/api/insurance/1")
                        .param("currentState", "RECORDED"))
                .andExpect(status().isOk());

        verify(insuranceService).updateState(1L, InsuranceEntryState.WAITING_FOR_HEALTH_INSURANCE);
    }

    @Test
    @WithMockUser
    void updateState_healthInsuranceReceivedState_transitionsToWaitingForPrivate() throws Exception {
        mockMvc.perform(put("/api/insurance/1")
                        .param("currentState", "HEALH_INSURANCE_RECEIVED"))
                .andExpect(status().isOk());

        verify(insuranceService).updateState(1L, InsuranceEntryState.WAITING_FOR_PRIVATE_INSURANCE);
    }

    @Test
    @WithMockUser
    void updateAmountReceived_healthType_callsServiceWithHealthType() throws Exception {
        mockMvc.perform(put("/api/insurance/amount/5")
                        .param("type", "HEALTH"))
                .andExpect(status().isOk());

        verify(insuranceService).updateAmountReceived(5L, InsuranceType.HEALTH);
    }

    @Test
    @WithMockUser
    void updateState_waitingForHealthInsuranceState_transitionsWithAmountAndFile() throws Exception {
        mockMvc.perform(put("/api/insurance/1")
                        .param("currentState", "WAITING_FOR_HEALTH_INSURANCE")
                        .param("amount", "75.50"))
                .andExpect(status().isOk());

        verify(insuranceService).updateState(eq(1L), eq(InsuranceEntryState.HEALH_INSURANCE_RECEIVED),
                any(), isNull(), isNull());
    }

    @Test
    @WithMockUser
    void updateState_waitingForPrivateInsuranceState_transitionsWithAmountAndFile() throws Exception {
        mockMvc.perform(put("/api/insurance/2")
                        .param("currentState", "WAITING_FOR_PRIVATE_INSURANCE")
                        .param("amount", "50.00"))
                .andExpect(status().isOk());

        verify(insuranceService).updateState(eq(2L), eq(InsuranceEntryState.DONE),
                any(), isNull(), isNull());
    }

    @Test
    @WithMockUser
    void updateState_doneState_logsWarningAndDoesNotCallUpdateState() throws Exception {
        mockMvc.perform(put("/api/insurance/3")
                        .param("currentState", "DONE"))
                .andExpect(status().isOk());

        verify(insuranceService, never()).updateState(any(), any());
    }

    @Test
    @WithMockUser
    void updateState_withFile_extractsFileBytesFromMultipart() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "health.pdf", "application/pdf", "health-content".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/insurance/1")
                        .file(file)
                        .param("currentState", "WAITING_FOR_HEALTH_INSURANCE")
                        .param("amount", "100.00"))
                .andExpect(status().isOk());

        verify(insuranceService).updateState(eq(1L), eq(InsuranceEntryState.HEALH_INSURANCE_RECEIVED),
                any(), eq("health.pdf"), any());
    }

    @Test
    @WithMockUser
    void editInsuranceEntry_whenServiceThrows_propagatesException() {
        doThrow(new RuntimeException("service error"))
                .when(insuranceService).updateState(any(), any());

        assertThrows(Exception.class, () ->
                mockMvc.perform(put("/api/insurance/1")
                        .param("currentState", "RECORDED")));
    }
}
