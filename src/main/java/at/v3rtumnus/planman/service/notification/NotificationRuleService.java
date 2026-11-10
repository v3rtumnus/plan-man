package at.v3rtumnus.planman.service.notification;

import at.v3rtumnus.planman.dao.NotificationRepository;
import at.v3rtumnus.planman.dao.NotificationRuleRepository;
import at.v3rtumnus.planman.dto.notification.NotificationRuleDTO;
import at.v3rtumnus.planman.dto.notification.NotificationRuleUpdate;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.notification.NotificationRule;
import at.v3rtumnus.planman.entity.notification.NotificationTargetingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/** The rules page: thresholds and on/off. Rules themselves come from the changelog. */
@Service
@RequiredArgsConstructor
public class NotificationRuleService {

    static final Map<String, String> LABELS = Map.of(
            "days", "Tage",
            "thresholdAmount", "Schwelle (€)",
            "sharePercent", "Aktien ± %",
            "fundPercent", "ETFs und Fonds ± %",
            "minDepotPercent", "Mindestanteil am Depot (%)",
            "bundleMin", "Bündeln ab (ETFs/Fonds pro Woche)",
            "staleDays", "Kurs höchstens so viele Tage alt");

    private final NotificationRuleRepository rules;
    private final NotificationRepository notifications;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<NotificationRuleDTO> list() {
        LocalDateTime since = LocalDateTime.now(clock).minusDays(30);
        return rules.findAll().stream()
                .sorted(Comparator.comparing(NotificationRule::getId))
                .map(rule -> new NotificationRuleDTO(rule.getId(), rule.getName(), rule.getRuleType(), rule.isEnabled(),
                        targets(rule),
                        RuleParameters.read(rule.getParametersJson()).entrySet().stream()
                                .map(e -> new NotificationRuleDTO.Parameter(e.getKey(),
                                        LABELS.getOrDefault(e.getKey(), e.getKey()), e.getValue()))
                                .toList(),
                        notifications.countByRuleAndCreatedAtAfter(rule, since),
                        notifications.countByRuleAndCreatedAtAfterAndReadAtIsNotNull(rule, since)))
                .toList();
    }

    /** Only the rule's existing keys can change; values must be numbers ≥ 0. */
    @Transactional
    public void update(Long id, NotificationRuleUpdate update) {
        NotificationRule rule = rules.findById(id).orElseThrow(() -> new NoSuchElementException("Rule not found: " + id));
        LinkedHashMap<String, BigDecimal> current = RuleParameters.read(rule.getParametersJson());
        Map<String, BigDecimal> changes = update.parameters() == null ? Map.of() : update.parameters();
        for (Map.Entry<String, BigDecimal> change : changes.entrySet()) {
            if (!current.containsKey(change.getKey())) {
                throw new IllegalArgumentException("Unknown parameter '" + change.getKey() + "' for " + rule.getName());
            }
            if (change.getValue() == null || change.getValue().signum() < 0) {
                throw new IllegalArgumentException("Parameter '" + change.getKey() + "' must be a number ≥ 0");
            }
            current.put(change.getKey(), change.getValue());
        }
        rule.setParametersJson(RuleParameters.write(current));
        rule.setEnabled(update.enabled());
        rules.save(rule);
    }

    private static String targets(NotificationRule rule) {
        if (rule.getTargetingMode() == NotificationTargetingMode.ALL_USERS) {
            return "Alle";
        }
        return rule.getTargetUsers().stream().map(UserProfile::getUsername).collect(Collectors.joining(", "));
    }
}
