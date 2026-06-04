package at.v3rtumnus.planman.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component("securitiesImport")
@RequiredArgsConstructor
public class SecuritiesImportHealthIndicator implements HealthIndicator {

    // Quotes run at 00:00 MON-FRI. Allow 25h before flagging as stale.
    private static final Duration STALE_THRESHOLD = Duration.ofHours(25);

    private final OnVistaFinancialService onVistaFinancialService;

    @Override
    public Health health() {
        Instant lastSuccess = onVistaFinancialService.getLastSuccessfulImport();
        String lastError = onVistaFinancialService.getLastImportError();

        if (lastSuccess == null) {
            return Health.unknown()
                    .withDetail("status", "No successful securities import since startup")
                    .build();
        }

        Duration age = Duration.between(lastSuccess, Instant.now());
        if (age.compareTo(STALE_THRESHOLD) > 0) {
            Health.Builder builder = Health.down()
                    .withDetail("lastSuccessfulImport", lastSuccess.toString())
                    .withDetail("staleSince", age.toHours() + "h ago");
            if (lastError != null) {
                builder.withDetail("lastError", lastError);
            }
            return builder.build();
        }

        return Health.up()
                .withDetail("lastSuccessfulImport", lastSuccess.toString())
                .build();
    }
}
