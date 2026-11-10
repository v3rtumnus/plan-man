package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.HealthDayRepository;
import at.v3rtumnus.planman.entity.health.HealthDay;
import at.v3rtumnus.planman.service.JobStatusService;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PhoneDataServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private final Clock clock = Clock.fixed(TODAY.atTime(14, 20).atZone(ZONE).toInstant(), ZONE);
    private final HealthService healthService = mock(HealthService.class);
    private final HealthDayRepository days = mock(HealthDayRepository.class);
    private final JobStatusService jobs = mock(JobStatusService.class);
    private final HttpClient http = mock(HttpClient.class);

    private PhoneDataService service(String steps, String hr, String sleep, String token) {
        return new PhoneDataService(healthService, days, jobs, http, clock, "http://ha:8123", token, "michael", steps, hr, sleep);
    }

    @SuppressWarnings("unchecked")
    private HttpRequest[] respond(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        HttpRequest[] seen = new HttpRequest[1];
        when(http.<String>send(any(HttpRequest.class), any())).thenAnswer(i -> {
            seen[0] = i.getArgument(0);
            return response;
        });
        return seen;
    }

    // Times in UTC: Vienna is UTC+1 in November.
    private static final String HISTORY = """
            [[{"entity_id":"sensor.handy_steps","state":"8000","last_changed":"2026-11-08T23:00:00+00:00"},
              {"state":"4200","last_changed":"2026-11-09T12:00:00+00:00"},
              {"state":"9100","last_changed":"2026-11-09T22:30:00+00:00"},
              {"state":"40","last_changed":"2026-11-09T23:10:00+00:00"},
              {"state":"unavailable","last_changed":"2026-11-10T08:00:00+00:00"},
              {"state":"3500","last_changed":"2026-11-10T12:00:00+00:00"}],
             [{"entity_id":"sensor.handy_resting_hr","state":"61","last_changed":"2026-11-09T06:00:00+00:00"},
              {"state":"58","last_changed":"2026-11-10T06:00:00+00:00"}],
             [{"entity_id":"sensor.handy_sleep","state":"7.5","last_changed":"2026-11-10T06:00:00+00:00"}]]""";

    @Test
    void sync_writesYesterdayAndToday() throws Exception {
        HttpRequest[] seen = respond(200, HISTORY);
        HealthDay yesterday = new HealthDay();
        HealthDay today = new HealthDay();
        today.setSteps(3500);
        when(healthService.day("michael", TODAY.minusDays(1))).thenReturn(yesterday);
        when(healthService.day("michael", TODAY)).thenReturn(today);
        PhoneDataService service = service("sensor.handy_steps", "sensor.handy_resting_hr", "sensor.handy_sleep", "t0k");
        assertThat(service.configured()).isTrue();

        service.sync();

        assertThat(yesterday.getSteps()).isEqualTo(9100);
        assertThat(yesterday.getRestingHr()).isEqualTo(61);
        assertThat(yesterday.getSleepMinutes()).isNull();
        assertThat(today.getSteps()).isEqualTo(3500);
        assertThat(today.getRestingHr()).isEqualTo(58);
        assertThat(today.getSleepMinutes()).isEqualTo(450);
        verify(days).save(yesterday);
        verify(days).save(today);
        verify(jobs).success(JobStatusService.PHONE_DATA);
        assertThat(seen[0].uri().toString()).startsWith("http://ha:8123/api/history/period/2026-11-09T00%3A00%2B01%3A00?end_time=")
                .contains("filter_entity_id=sensor.handy_steps%2Csensor.handy_resting_hr%2Csensor.handy_sleep");
        assertThat(seen[0].headers().firstValue("Authorization")).contains("Bearer t0k");
    }

    @Test
    void sync_unchangedDaysAreNotSaved_andMinutesStayMinutes() throws Exception {
        respond(200, """
                [[{"entity_id":"sensor.s","state":"450","last_changed":"2026-11-10T06:00:00+00:00"}]]""");
        HealthDay today = new HealthDay();
        today.setSleepMinutes(450);
        when(healthService.day("michael", TODAY)).thenReturn(today);

        assertThat(service("", "", "sensor.s", "t").syncDays()).isZero();
        verify(days, never()).save(any());
        verify(healthService, never()).day(eq("michael"), eq(TODAY.minusDays(1)));
    }

    @Test
    void sync_notConfigured_isANoOpSuccess() {
        service("", "", "", "t").sync();
        service("sensor.x", "", "", "").sync();
        verify(jobs, times(2)).success(JobStatusService.PHONE_DATA);
        verifyNoInteractions(http);
    }

    @Test
    void sync_wrongEntityOrHttpError_fails() throws Exception {
        respond(200, "[]");
        service("sensor.missing", "", "", "t").sync();
        verify(jobs).failure(eq(JobStatusService.PHONE_DATA), any());

        respond(401, "");
        service("sensor.x", "", "", "t").sync();
        verify(jobs, times(2)).failure(eq(JobStatusService.PHONE_DATA), any());
    }

    @Test
    void parse_skipsEmptySeries() {
        assertThat(PhoneDataService.parse("[[], {\"x\":1}]")).isEmpty();
    }
}
