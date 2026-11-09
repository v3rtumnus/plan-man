package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.JobStatusRepository;
import at.v3rtumnus.planman.entity.JobStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deadman for the scheduled jobs: {@code planman_job_last_success_timestamp_seconds{task}} is
 * the last time each job finished without an error. Grafana's "plan-man job stale" alert fires
 * when it gets too old, so a job that silently stops is noticed even though plan-man is up.
 * The value is kept in {@code job_status}, so a restart does not reset it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobStatusService {

    public static final String QUOTES = "quotes";
    public static final String SNAPSHOT = "snapshot";
    public static final String FX = "fx";
    public static final String NOTIFICATION_RULES = "notification_rules";
    /** Daily: last week's numbers written for every user with a goal. */
    public static final String HEALTH_REVIEW = "health_review";
    /** Daily: last week's Opus text is there (or not needed); fails while the API call keeps failing. */
    public static final String HEALTH_REVIEW_AI = "health_review_ai";

    /** Jobs with a gauge from startup on. Add new scheduled jobs here and to the Grafana alert. */
    static final List<String> JOBS = List.of(QUOTES, SNAPSHOT, FX, NOTIFICATION_RULES, HEALTH_REVIEW, HEALTH_REVIEW_AI);

    private final JobStatusRepository repository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    private final Map<String, Double> lastSuccessSeconds = new ConcurrentHashMap<>();

    @PostConstruct
    void registerGauges() {
        // A job that never succeeded counts from startup, so it still turns stale.
        double startup = epochSeconds(LocalDateTime.now(clock));
        for (String job : JOBS) {
            double initial = repository.findById(job)
                    .map(JobStatus::getLastSuccessAt)
                    .map(this::epochSeconds)
                    .orElse(startup);
            lastSuccessSeconds.put(job, initial);
            Gauge.builder("planman.job.last.success.timestamp", lastSuccessSeconds, m -> m.get(job))
                    .baseUnit("seconds")
                    .description("Last time the scheduled job finished without an error")
                    .tag("task", job)
                    .register(meterRegistry);
        }
    }

    /** Own transaction, so a failure is still recorded when the job's transaction rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(String job) {
        LocalDateTime now = LocalDateTime.now(clock);
        JobStatus status = load(job);
        status.setLastSuccessAt(now);
        repository.save(status);
        lastSuccessSeconds.put(job, epochSeconds(now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(String job, Throwable error) {
        log.warn("Scheduled job '{}' failed: {}", job, error.toString());
        JobStatus status = load(job);
        status.setLastFailureAt(LocalDateTime.now(clock));
        String message = error.toString();
        status.setLastError(message.length() > 1000 ? message.substring(0, 1000) : message);
        repository.save(status);
    }

    private JobStatus load(String job) {
        return repository.findById(job).orElseGet(() -> {
            JobStatus status = new JobStatus();
            status.setName(job);
            return status;
        });
    }

    private double epochSeconds(LocalDateTime time) {
        ZoneId zone = clock.getZone();
        return time.atZone(zone).toEpochSecond();
    }
}
