package at.v3rtumnus.planman.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecuritiesImportHealthIndicatorTest {

    @Mock
    private OnVistaFinancialService onVistaFinancialService;

    @InjectMocks
    private SecuritiesImportHealthIndicator indicator;

    @Test
    void health_noImportSinceStartup_returnsUnknown() {
        when(onVistaFinancialService.getLastSuccessfulImport()).thenReturn(null);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsKey("status");
    }

    @Test
    void health_recentImport_returnsUp() {
        when(onVistaFinancialService.getLastSuccessfulImport())
                .thenReturn(Instant.now().minus(1, ChronoUnit.HOURS));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("lastSuccessfulImport");
    }

    @Test
    void health_staleImport_returnsDown() {
        when(onVistaFinancialService.getLastSuccessfulImport())
                .thenReturn(Instant.now().minus(30, ChronoUnit.HOURS));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("staleSince");
    }

    @Test
    void health_staleImportWithError_includesErrorDetail() {
        when(onVistaFinancialService.getLastSuccessfulImport())
                .thenReturn(Instant.now().minus(30, ChronoUnit.HOURS));
        when(onVistaFinancialService.getLastImportError()).thenReturn("Connection timeout");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("lastError", "Connection timeout");
    }
}
