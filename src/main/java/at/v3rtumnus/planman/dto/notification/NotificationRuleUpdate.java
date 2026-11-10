package at.v3rtumnus.planman.dto.notification;

import java.math.BigDecimal;
import java.util.Map;

/** Rules-page edit: on/off and new values for the rule's existing parameters. */
public record NotificationRuleUpdate(boolean enabled, Map<String, BigDecimal> parameters) {
}
