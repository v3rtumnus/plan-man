package at.v3rtumnus.planman.service.notification;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A rule's thresholds: a flat JSON object of numbers, e.g. {"days":14}. A missing or empty
 * key falls back to the evaluator's default, so an older row keeps working after a new key.
 */
public final class RuleParameters {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<LinkedHashMap<String, BigDecimal>> MAP = new TypeReference<>() {
    };

    private final Map<String, BigDecimal> values;

    private RuleParameters(Map<String, BigDecimal> values) {
        this.values = values;
    }

    public static RuleParameters parse(String json) {
        return new RuleParameters(read(json));
    }

    /** Parses and checks a rules-page edit: an object whose values are all numbers ≥ 0. */
    public static LinkedHashMap<String, BigDecimal> read(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        LinkedHashMap<String, BigDecimal> map;
        try {
            map = JSON.readValue(json, MAP);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Parameters must be a JSON object of numbers: " + json, e);
        }
        map.forEach((key, value) -> {
            if (value == null || value.signum() < 0) {
                throw new IllegalArgumentException("Parameter '" + key + "' must be a number ≥ 0");
            }
        });
        return map;
    }

    public static String write(Map<String, BigDecimal> values) {
        return JSON.writeValueAsString(values);
    }

    public BigDecimal decimal(String key, BigDecimal fallback) {
        BigDecimal value = values.get(key);
        return value != null ? value : fallback;
    }

    public int integer(String key, int fallback) {
        BigDecimal value = values.get(key);
        return value != null ? value.intValue() : fallback;
    }
}
