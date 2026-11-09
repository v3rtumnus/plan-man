package at.v3rtumnus.planman.service.notification;

import at.v3rtumnus.planman.dto.notification.NotificationAction;
import at.v3rtumnus.planman.entity.notification.NotificationRule;
import at.v3rtumnus.planman.entity.notification.NotificationSeverity;

import java.util.List;

/**
 * One bean per rule type. The evaluation job calls {@link #evaluate} for every enabled rule
 * of that type; each candidate becomes one notification per target user, deduplicated by
 * {@code periodKey}. Adding a rule type = one new {@code @Component} implementing this.
 */
public interface NotificationEvaluator {

    /** Matches {@code notification_rule.rule_type}. */
    String ruleType();

    /** Candidates whose condition is met right now; empty if nothing is due. */
    List<NotificationCandidate> evaluate(NotificationRule rule);

    /**
     * @param periodKey dedupe key per rule and user, e.g. "2026-11" for once a month
     * @param url       page that opens on tap (null = notification overview)
     * @param actions   up to two buttons, handled by the rule type's {@link NotificationActionHandler}
     * @param push      also send to the user's devices
     */
    record NotificationCandidate(String title, String message, NotificationSeverity severity, String periodKey,
                                 String url, List<NotificationAction> actions, boolean push) {

        public NotificationCandidate {
            actions = actions == null ? List.of() : List.copyOf(actions);
            if (actions.size() > 2) {
                throw new IllegalArgumentException("At most two actions per notification");
            }
        }
    }
}
