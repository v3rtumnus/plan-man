package at.v3rtumnus.planman.service.notification;

import at.v3rtumnus.planman.dao.NotificationRuleRepository;
import at.v3rtumnus.planman.dao.UserProfileRepository;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.notification.NotificationRule;
import at.v3rtumnus.planman.service.JobStatusService;
import at.v3rtumnus.planman.service.notification.NotificationEvaluator.NotificationCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hourly job: runs every enabled rule through its evaluator and fans each candidate out to the
 * rule's target users. A rule that throws does not stop the others, but the run then does not
 * count as a success, so the "plan-man job stale" alert surfaces a rule that keeps failing.
 */
@Service
@Slf4j
public class NotificationRuleEvaluationService {

    private final NotificationRuleRepository rules;
    private final UserProfileRepository users;
    private final NotificationService notificationService;
    private final JobStatusService jobs;
    private final Map<String, NotificationEvaluator> evaluators;

    public NotificationRuleEvaluationService(NotificationRuleRepository rules, UserProfileRepository users,
                                             NotificationService notificationService, JobStatusService jobs,
                                             ObjectProvider<NotificationEvaluator> evaluators) {
        this.rules = rules;
        this.users = users;
        this.notificationService = notificationService;
        this.jobs = jobs;
        this.evaluators = evaluators.orderedStream()
                .collect(Collectors.toMap(NotificationEvaluator::ruleType, Function.identity()));
    }

    @Scheduled(cron = "${planman.notifications.evaluation-cron:0 5 * * * *}")
    public void evaluateAll() {
        Exception firstError = null;
        int created = 0;
        for (NotificationRule rule : rules.findByEnabledTrue()) {
            try {
                created += evaluate(rule);
            } catch (Exception e) {
                log.error("Notification rule '{}' ({}) failed", rule.getName(), rule.getRuleType(), e);
                if (firstError == null) {
                    firstError = e;
                }
            }
        }
        if (firstError == null) {
            jobs.success(JobStatusService.NOTIFICATION_RULES);
        } else {
            jobs.failure(JobStatusService.NOTIFICATION_RULES, firstError);
        }
        if (created > 0) {
            log.info("Notification rules created {} notifications", created);
        }
    }

    /** Evaluates one rule and returns how many notifications it created. */
    int evaluate(NotificationRule rule) {
        NotificationEvaluator evaluator = evaluators.get(rule.getRuleType());
        if (evaluator == null) {
            throw new IllegalStateException("No evaluator for rule type " + rule.getRuleType());
        }
        List<NotificationCandidate> candidates = evaluator.evaluate(rule);
        if (candidates.isEmpty()) {
            return 0;
        }
        List<UserProfile> targets = switch (rule.getTargetingMode()) {
            case ALL_USERS -> users.findAll();
            case SPECIFIC_USERS -> rule.getTargetUsers();
        };
        int created = 0;
        for (NotificationCandidate candidate : candidates) {
            for (UserProfile user : targets) {
                if (notificationService.create(rule, user, candidate)) {
                    created++;
                }
            }
        }
        return created;
    }
}
