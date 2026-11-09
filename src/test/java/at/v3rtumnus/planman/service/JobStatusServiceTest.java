package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.JobStatusRepository;
import at.v3rtumnus.planman.entity.JobStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JobStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T19:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Europe/Vienna"));

    private final JobStatusRepository repository = mock(JobStatusRepository.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final JobStatusService service = new JobStatusService(repository, meters, CLOCK);

    @BeforeEach
    void setUp() {
        when(repository.findById(anyString())).thenReturn(Optional.empty());
    }

    private double gauge(String job) {
        return meters.get("planman.job.last.success.timestamp").tag("task", job).gauge().value();
    }

    @Test
    void gauges_startFromStoredSuccessOrStartup() {
        JobStatus quotes = new JobStatus();
        quotes.setName(JobStatusService.QUOTES);
        quotes.setLastSuccessAt(LocalDateTime.of(2026, 11, 9, 19, 0)); // Vienna = 18:00 UTC
        when(repository.findById(JobStatusService.QUOTES)).thenReturn(Optional.of(quotes));

        service.registerGauges();

        assertThat(gauge(JobStatusService.QUOTES)).isEqualTo(NOW.minusSeconds(3600).getEpochSecond());
        assertThat(gauge(JobStatusService.SNAPSHOT)).isEqualTo(NOW.getEpochSecond());
        assertThat(meters.get("planman.job.last.success.timestamp").gauges()).hasSize(JobStatusService.JOBS.size());
    }

    @Test
    void success_storesAndMovesTheGauge() {
        JobStatus stored = new JobStatus();
        stored.setName(JobStatusService.FX);
        stored.setLastSuccessAt(LocalDateTime.of(2026, 11, 1, 0, 0));
        when(repository.findById(JobStatusService.FX)).thenReturn(Optional.of(stored));
        service.registerGauges();

        service.success(JobStatusService.FX);

        assertThat(stored.getLastSuccessAt()).isEqualTo(LocalDateTime.now(CLOCK));
        verify(repository).save(stored);
        assertThat(gauge(JobStatusService.FX)).isEqualTo(NOW.getEpochSecond());
    }

    @Test
    void failure_recordsErrorButKeepsLastSuccess() {
        service.registerGauges();
        double before = gauge(JobStatusService.QUOTES);

        service.failure(JobStatusService.QUOTES, new RuntimeException("x".repeat(2000)));

        ArgumentCaptor<JobStatus> saved = ArgumentCaptor.forClass(JobStatus.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo(JobStatusService.QUOTES);
        assertThat(saved.getValue().getLastFailureAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(saved.getValue().getLastError()).hasSize(1000);
        assertThat(saved.getValue().getLastSuccessAt()).isNull();
        assertThat(gauge(JobStatusService.QUOTES)).isEqualTo(before);
        verify(repository, never()).delete(any());
    }
}
