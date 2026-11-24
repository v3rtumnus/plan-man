package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.HealthDayRepository;
import at.v3rtumnus.planman.entity.health.HealthDay;
import at.v3rtumnus.planman.service.JobStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/**
 * Phone data (H8): the Home Assistant companion app's Health Connect steps sensor →
 * {@code health_day.steps}. Hourly, plan-man reads HA's history for yesterday and today, so the
 * last steps before midnight still land on yesterday. Steps are the day's highest value (the
 * sensor counts up during the day). Nothing happens until the entity ID is set
 * ({@code planman.health.phone.steps-entity}); a wrong ID makes the job fail, so the Grafana alert
 * *plan-man job stale* shows it.
 */
@Service
@Slf4j
public class PhoneDataService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HealthService healthService;
    private final HealthDayRepository days;
    private final JobStatusService jobs;
    private final HttpClient http;
    private final Clock clock;
    private final String haUrl;
    private final String token;
    private final String username;
    private final String stepsEntity;

    @Autowired
    public PhoneDataService(HealthService healthService, HealthDayRepository days, JobStatusService jobs, Clock clock,
                            @Value("${planman.health.ha.url:http://homeassistant:8123}") String haUrl,
                            @Value("${planman.health.ha.token:${HA_TOKEN:}}") String token,
                            @Value("${planman.health.phone.user:michael}") String username,
                            @Value("${planman.health.phone.steps-entity:}") String stepsEntity) {
        this(healthService, days, jobs, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), clock,
                haUrl, token, username, stepsEntity);
    }

    PhoneDataService(HealthService healthService, HealthDayRepository days, JobStatusService jobs, HttpClient http,
                     Clock clock, String haUrl, String token, String username,
                     String stepsEntity) {
        this.healthService = healthService;
        this.days = days;
        this.jobs = jobs;
        this.http = http;
        this.clock = clock;
        this.haUrl = haUrl;
        this.token = token;
        this.username = username;
        this.stepsEntity = stepsEntity.trim();
    }

    public boolean configured() {
        return !stepsEntity.isBlank() && !token.isBlank();
    }

    @Scheduled(cron = "${planman.health.phone.cron:0 20 * * * *}")
    public void sync() {
        if (!configured()) {
            jobs.success(JobStatusService.PHONE_DATA);
            return;
        }
        try {
            int updated = syncDays();
            jobs.success(JobStatusService.PHONE_DATA);
            log.debug("Phone data: {} days updated", updated);
        } catch (Exception e) {
            log.warn("Phone data from Home Assistant failed: {}", e.getMessage());
            jobs.failure(JobStatusService.PHONE_DATA, e);
        }
    }

    /** Reads yesterday and today; returns how many day rows changed. */
    int syncDays() throws IOException, InterruptedException {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        LocalDate yesterday = today.minusDays(1);
        Map<String, List<Point>> history = history(yesterday.atStartOfDay(zone).toOffsetDateTime(),
                OffsetDateTime.now(clock));
        if (!history.containsKey(stepsEntity)) {
            throw new IllegalStateException("Home Assistant has no history for " + stepsEntity
                    + " (entity ID wrong or sensor off in the companion app?)");
        }
        int changed = 0;
        for (LocalDate date : List.of(yesterday, today)) {
            Integer steps = dayMax(history.get(stepsEntity), date, zone);
            if (steps == null) {
                continue;
            }
            HealthDay day = healthService.day(username, date);
            if (!steps.equals(day.getSteps())) {
                day.setSteps(steps);
                days.save(day);
                changed++;
            }
        }
        return changed;
    }

    record Point(Instant at, BigDecimal value) {
    }

    Map<String, List<Point>> history(OffsetDateTime from, OffsetDateTime to) throws IOException, InterruptedException {
        String url = haUrl + "/api/history/period/" + enc(from.toString())
                + "?end_time=" + enc(to.toString())
                + "&filter_entity_id=" + enc(stepsEntity)
                + "&minimal_response&no_attributes&significant_changes_only=0";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Home Assistant history: HTTP " + response.statusCode());
        }
        // HA starts each series with the state at the window start (yesterday 00:00), which
        // still carries the day before's last value: leave it out.
        Instant start = from.toInstant();
        Map<String, List<Point>> series = parse(response.body());
        series.replaceAll((entity, points) -> points.stream().filter(p -> p.at().isAfter(start)).toList());
        return series;
    }

    static Map<String, List<Point>> parse(String body) {
        Map<String, List<Point>> result = new HashMap<>();
        for (JsonNode series : JSON.readTree(body)) {
            if (!series.isArray() || series.isEmpty()) {
                continue;
            }
            String entity = series.get(0).path("entity_id").asString("");
            List<Point> points = new ArrayList<>();
            for (JsonNode p : series) {
                String state = p.path("state").asString("");
                String changed = p.path("last_changed").asString("");
                try {
                    points.add(new Point(OffsetDateTime.parse(changed).toInstant(), new BigDecimal(state)));
                } catch (RuntimeException e) {
                    // "unknown", "unavailable" or a bad timestamp: skip
                }
            }
            result.put(entity, points);
        }
        return result;
    }

    private static Integer dayMax(List<Point> points, LocalDate date, ZoneId zone) {
        return onDay(points, date, zone).map(Point::value).max(Comparator.naturalOrder())
                .map(v -> v.setScale(0, java.math.RoundingMode.HALF_UP).intValue()).orElse(null);
    }

    private static java.util.stream.Stream<Point> onDay(List<Point> points, LocalDate date, ZoneId zone) {
        if (points == null) {
            return java.util.stream.Stream.empty();
        }
        return points.stream().filter(p -> LocalDate.ofInstant(p.at(), zone).equals(date));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
