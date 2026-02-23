package at.v3rtumnus.planman.conf;

import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantTracingConfigTest {

    private final AssistantTracingConfig config = new AssistantTracingConfig();

    @Test
    void openTelemetry_returnsNonNull() {
        OpenTelemetry openTelemetry = config.openTelemetry();

        assertThat(openTelemetry).isNotNull();
    }

    @Test
    void tracer_returnsNonNull() {
        OpenTelemetry openTelemetry = config.openTelemetry();
        Tracer tracer = config.tracer(openTelemetry);

        assertThat(tracer).isNotNull();
    }
}
