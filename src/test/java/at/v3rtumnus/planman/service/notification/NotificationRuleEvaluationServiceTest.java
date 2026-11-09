package at.v3rtumnus.planman.service.notification;

import at.v3rtumnus.planman.dao.NotificationRuleRepository;
import at.v3rtumnus.planman.dao.UserProfileRepository;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.notification.NotificationRule;
import at.v3rtumnus.planman.entity.notification.NotificationSeverity;
import at.v3rtumnus.planman.entity.notification.NotificationTargetingMode;
import at.v3rtumnus.planman.service.JobStatusService;
import at.v3rtumnus.planman.service.notification.NotificationEvaluator.NotificationCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NotificationRuleEvaluationServiceTest {

    private final NotificationRuleRepository rules = mock(NotificationRuleRepository.class);
    private final UserProfileRepository users = mock(UserProfileRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final JobStatusService jobs = mock(JobStatusService.class);
    private final NotificationEvaluator budget = mock(NotificationEvaluator.class);
    private final NotificationEvaluator broken = mock(NotificationEvaluator.class);
    private NotificationRuleEvaluationService service;
    private final UserProfile michael = user("michael");
    private final UserProfile aga = user("Aga");

    private static UserProfile user(String name) {
        UserProfile user = new UserProfile();
        user.setUsername(name);
        return user;
    }

    private static NotificationRule rule(String type, NotificationTargetingMode mode) {
        NotificationRule rule = new NotificationRule();
        rule.setName(type);
        rule.setRuleType(type);
        rule.setTargetingMode(mode);
        return rule;
    }

    private static NotificationCandidate candidate(String key) {
        return new NotificationCandidate("T", "M", NotificationSeverity.WARNING, key, null, List.of(), false);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(budget.ruleType()).thenReturn("BUDGET");
        when(broken.ruleType()).thenReturn("BROKEN");
        ObjectProvider<NotificationEvaluator> evaluators = mock(ObjectProvider.class);
        when(evaluators.orderedStream()).thenReturn(Stream.of(budget, broken));
        service = new NotificationRuleEvaluationService(rules, users, notificationService, jobs, evaluators);
        when(users.findAll()).thenReturn(List.of(michael, aga));
        when(notificationService.create(any(), any(), any())).thenReturn(true);
    }

    @Test
    void allUsersRuleFansOutToEveryUserAndCountsAsSuccess() {
        NotificationRule rule = rule("BUDGET", NotificationTargetingMode.ALL_USERS);
        NotificationCandidate c = candidate("2026-11");
        when(rules.findByEnabledTrue()).thenReturn(List.of(rule));
        when(budget.evaluate(rule)).thenReturn(List.of(c));

        service.evaluateAll();

        verify(notificationService).create(rule, michael, c);
        verify(notificationService).create(rule, aga, c);
        verify(jobs).success(JobStatusService.NOTIFICATION_RULES);
    }

    @Test
    void specificUsersRuleOnlyReachesItsTargets() {
        NotificationRule rule = rule("BUDGET", NotificationTargetingMode.SPECIFIC_USERS);
        rule.setTargetUsers(List.of(michael));
        when(rules.findByEnabledTrue()).thenReturn(List.of(rule));
        when(budget.evaluate(rule)).thenReturn(List.of(candidate("a"), candidate("b")));

        service.evaluateAll();

        verify(notificationService, times(2)).create(eq(rule), eq(michael), any());
        verify(notificationService, never()).create(any(), eq(aga), any());
    }

    @Test
    void noCandidatesCreatesNothing() {
        NotificationRule rule = rule("BUDGET", NotificationTargetingMode.ALL_USERS);
        when(rules.findByEnabledTrue()).thenReturn(List.of(rule));
        when(budget.evaluate(rule)).thenReturn(List.of());

        service.evaluateAll();

        verifyNoInteractions(notificationService);
        verify(jobs).success(JobStatusService.NOTIFICATION_RULES);
    }

    @Test
    void failingRuleDoesNotStopOthersButMarksTheRunFailed() {
        NotificationRule bad = rule("BROKEN", NotificationTargetingMode.ALL_USERS);
        NotificationRule good = rule("BUDGET", NotificationTargetingMode.ALL_USERS);
        RuntimeException error = new RuntimeException("boom");
        when(rules.findByEnabledTrue()).thenReturn(List.of(bad, good));
        when(broken.evaluate(bad)).thenThrow(error);
        when(budget.evaluate(good)).thenReturn(List.of(candidate("k")));

        service.evaluateAll();

        verify(notificationService).create(good, michael, candidate("k"));
        verify(jobs).failure(JobStatusService.NOTIFICATION_RULES, error);
        verify(jobs, never()).success(any());
    }

    @Test
    void unknownRuleTypeCountsAsFailure() {
        when(rules.findByEnabledTrue()).thenReturn(List.of(rule("NOPE", NotificationTargetingMode.ALL_USERS)));

        service.evaluateAll();

        verify(jobs).failure(eq(JobStatusService.NOTIFICATION_RULES), any(IllegalStateException.class));
    }

    @Test
    void noRulesIsStillASuccessfulRun() {
        when(rules.findByEnabledTrue()).thenReturn(List.of());

        service.evaluateAll();

        verify(jobs).success(JobStatusService.NOTIFICATION_RULES);
    }

    @Test
    void candidateWithRecipient_reachesOnlyThatUser() {
        NotificationRule rule = rule("BUDGET", NotificationTargetingMode.ALL_USERS);
        when(budget.evaluate(rule)).thenReturn(List.of(new NotificationCandidate("T", "M", NotificationSeverity.INFO,
                "review-2026-11-02", null, List.of(), false, "Aga")));
        when(notificationService.create(any(), any(), any())).thenReturn(true);

        assertThat(service.evaluate(rule)).isEqualTo(1);
        verify(notificationService).create(eq(rule), eq(aga), any());
        verify(notificationService, never()).create(eq(rule), eq(michael), any());
    }
}
