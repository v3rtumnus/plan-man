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
 * Phone data (H8): the Home Assistant companion app's Health Connect sensors → {@code health_day}
 * (steps, resting heart rate, sleep). Hourly, plan-man reads HA's history for yesterday and
 * today, so the last steps before midnight still land on yesterday. Steps are the day's highest
 * value (the sensor counts up during the day); heart rate and sleep the day's last value.
 * Sleep in minutes; a value below 24 is taken as hours. Nothing happens until the entity IDs are
 * set ({@code planman.health.phone.*}); a wrong ID makes the job fail, so the Grafana alert
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
    private final Map<String, String> entities;

    @Autowired
    public PhoneDataService(HealthService healthService, HealthDayRepository days, JobStatusService jobs, Clock clock,
                            @Value("${planman.health.ha.url:http://homeassistant:8123}") String haUrl,
                            @Value("${planman.health.ha.token:${HA_TOKEN:}}") String token,
                            @Value("${planman.health.phone.user:michael}") String username,
                            @Value("${planman.health.phone.steps-entity:}") String stepsEntity,
                            @Value("${planman.health.phone.resting-hr-entity:}") String restingHrEntity,
                            @Value("${planman.health.phone.sleep-entity:}") String sleepEntity) {
        this(healthService, days, jobs, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), clock,
                haUrl, token, username, stepsEntity, restingHrEntity, sleepEntity);
    }

    PhoneDataService(HealthService healthService, HealthDayRepository days, JobStatusService jobs, HttpClient http,
                     Clock clock, String haUrl, String token, String username,
                     String stepsEntity, String restingHrEntity, String sleepEntity) {
        this.healthService = healthService;
        this.days = days;
        this.jobs = jobs;
        this.http = http;
        this.clock = clock;
        this.haUrl = haUrl;
        this.token = token;
        this.username = username;
        Map<String, String> map = new LinkedHashMap<>();
        if (!stepsEntity.isBlank()) map.put("steps", stepsEntity.trim());
        if (!restingHrEntity.isBlank()) map.put("restingHr", restingHrEntity.trim());
        if (!sleepEntity.isBlank()) map.put("sleep", sleepEntity.trim());
        this.entities = Collections.unmodifiableMap(map);
    }

    public boolean configured() {
        return !entities.isEmpty() && !token.isBlank();
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
        for (Map.Entry<String, String> e : entities.entrySet()) {
            if (!history.containsKey(e.getValue())) {
                throw new IllegalStateException("Home Assistant has no history for " + e.getValue()
                        + " (entity ID wrong or sensor off in the companion app?)");
            }
        }
        int changed = 0;
        for (LocalDate date : List.of(yesterday, today)) {
            Integer steps = dayMax(history.get(entities.get("steps")), date, zone);
            Integer hr = dayLast(history.get(entities.get("restingHr")), date, zone);
            Integer sleep = sleepMinutes(lastValue(history.get(entities.get("sleep")), date, zone));
            if (steps == null && hr == null && sleep == null) {
                continue;
            }
            HealthDay day = healthService.day(username, date);
            boolean dirty = false;
            if (steps != null && !steps.equals(day.getSteps())) { day.setSteps(steps); dirty = true; }
            if (hr != null && !hr.equals(day.getRestingHr())) { day.setRestingHr(hr); dirty = true; }
            if (sleep != null && !sleep.equals(day.getSleepMinutes())) { day.setSleepMinutes(sleep); dirty = true; }
            if (dirty) {
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
                + "&filter_entity_id=" + enc(String.join(",", new LinkedHashSet<>(entities.values())))
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

    private static Integer dayLast(List<Point> points, LocalDate date, ZoneId zone) {
        BigDecimal value = lastValue(points, date, zone);
        return value == null ? null : value.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
    }

    private static BigDecimal lastValue(List<Point> points, LocalDate date, ZoneId zone) {
        return onDay(points, date, zone).max(Comparator.comparing(Point::at)).map(Point::value).orElse(null);
    }

    /** Minutes; a value below 24 is hours (the sensor's unit is not in the minimal history). */
    static Integer sleepMinutes(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal minutes = value.compareTo(BigDecimal.valueOf(24)) < 0 ? value.multiply(BigDecimal.valueOf(60)) : value;
        return minutes.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
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
