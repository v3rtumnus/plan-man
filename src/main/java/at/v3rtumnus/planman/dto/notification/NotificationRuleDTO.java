package at.v3rtumnus.planman.dto.notification;

import java.math.BigDecimal;
import java.util.List;

/**
 * A rule on the rules page: its thresholds with labels, who it reaches, and how often it fired
 * in the last 30 days (per recipient) and how many of those were read.
 */
public record NotificationRuleDTO(Long id, String name, String ruleType, boolean enabled, String targets,
                                  List<Parameter> parameters, long fired30Days, long read30Days) {

    public record Parameter(String key, String label, BigDecimal value) {
    }
}
