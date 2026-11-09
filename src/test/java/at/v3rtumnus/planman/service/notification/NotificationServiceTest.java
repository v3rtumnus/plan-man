package at.v3rtumnus.planman.service.notification;

import at.v3rtumnus.planman.dao.NotificationRepository;
import at.v3rtumnus.planman.dao.PushSubscriptionRepository;
import at.v3rtumnus.planman.dao.UserProfileRepository;
import at.v3rtumnus.planman.dto.notification.DigestDTO;
import at.v3rtumnus.planman.dto.notification.NotificationAction;
import at.v3rtumnus.planman.dto.notification.NotificationDTO;
import at.v3rtumnus.planman.dto.notification.NotificationHeaderDTO;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.notification.Notification;
import at.v3rtumnus.planman.entity.notification.NotificationRule;
import at.v3rtumnus.planman.entity.notification.NotificationSeverity;
import at.v3rtumnus.planman.service.notification.NotificationEvaluator.NotificationCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T19:00:00Z"), ZoneId.of("Europe/Vienna"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
    private final UserProfileRepository users = mock(UserProfileRepository.class);
    private final WebPushService webPush = mock(WebPushService.class);
    private final NotificationActionHandler footballHandler = mock(NotificationActionHandler.class);
    private NotificationService service;
    private UserProfile michael;
    private NotificationRule rule;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(footballHandler.ruleType()).thenReturn("FOOTBALL");
        ObjectProvider<NotificationActionHandler> handlers = mock(ObjectProvider.class);
        when(handlers.orderedStream()).thenReturn(Stream.of(footballHandler));
        service = new NotificationService(notifications, subscriptions, users, webPush, CLOCK, handlers);
        michael = user("michael");
        rule = new NotificationRule();
        rule.setId(1L);
        rule.setRuleType("FOOTBALL");
        when(notifications.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            if (n.getId() == null) {
                n.setId(99L);
            }
            return n;
        });
    }

    private static UserProfile user(String name) {
        UserProfile user = new UserProfile();
        user.setUsername(name);
        return user;
    }

    private Notification notification(Long id, UserProfile recipient, String actionsJson) {
        Notification n = new Notification();
        n.setId(id);
        n.setRule(rule);
        n.setRecipient(recipient);
        n.setTitle("Fußball heute?");
        n.setMessage("Hast du gespielt?");
        n.setSeverity(NotificationSeverity.INFO);
        n.setActionsJson(actionsJson);
        n.setCreatedAt(NOW.minusHours(1));
        return n;
    }

    private static NotificationCandidate candidate(boolean push, NotificationSeverity severity) {
        return new NotificationCandidate("Budget knapp", "Noch 240 €", severity, "2026-11", "/expenses",
                List.of(new NotificationAction("read", "Gelesen")), push);
    }

    @Test
    void header_countsUnreadAndListsNewest() {
        when(notifications.countByRecipientUsernameAndReadAtIsNull("michael")).thenReturn(3L);
        when(notifications.findTop5ByRecipientUsernameAndReadAtIsNullOrderByCreatedAtDesc("michael"))
                .thenReturn(List.of(notification(5L, michael, null)));
        when(subscriptions.countByUserUsername("michael")).thenReturn(1L);

        NotificationHeaderDTO header = service.header("michael");

        assertThat(header.unreadCount()).isEqualTo(3);
        assertThat(header.notifications()).extracting(NotificationDTO::id).containsExactly(5L);
        assertThat(header.pushDevices()).isEqualTo(1);
        assertThat(NotificationHeaderDTO.empty().unreadCount()).isZero();
    }

    @Test
    void inbox_unreadFirstThenRecentRead() {
        Notification read = notification(1L, michael, null);
        read.setReadAt(NOW);
        when(notifications.findByRecipientUsernameAndReadAtIsNullOrderByCreatedAtDesc("michael"))
                .thenReturn(List.of(notification(2L, michael, "[{\"key\":\"played\",\"label\":\"Gespielt\"}]")));
        when(notifications.findTop30ByRecipientUsernameAndReadAtIsNotNullOrderByCreatedAtDesc("michael"))
                .thenReturn(List.of(read));

        List<NotificationDTO> inbox = service.inbox("michael");

        assertThat(inbox).extracting(NotificationDTO::id).containsExactly(2L, 1L);
        assertThat(inbox.getFirst().actions()).containsExactly(new NotificationAction("played", "Gespielt"));
        assertThat(inbox.getFirst().iconClass()).isEqualTo("bi-info-circle");
        assertThat(inbox.getFirst().textClass()).isEqualTo("text-primary");
        assertThat(inbox.get(1).read()).isTrue();
    }

    @Test
    void markRead_setsReadAtOnce() {
        Notification n = notification(1L, michael, null);
        when(notifications.findById(1L)).thenReturn(Optional.of(n));

        service.markRead(1L, "michael");
        service.markRead(1L, "michael");

        assertThat(n.getReadAt()).isEqualTo(NOW);
        verify(notifications, times(1)).save(n);
    }

    @Test
    void markRead_refusesOtherUsersNotification() {
        when(notifications.findById(1L)).thenReturn(Optional.of(notification(1L, user("Aga"), null)));

        assertThatThrownBy(() -> service.markRead(1L, "michael")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(1L, "michael")).isInstanceOf(AccessDeniedException.class);
        verify(notifications, never()).delete(any());
    }

    @Test
    void markRead_unknownIdIsNotFound() {
        when(notifications.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(9L, "michael")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void markAllRead_isScopedToTheUser() {
        when(notifications.markAllRead("michael", NOW)).thenReturn(4);

        assertThat(service.markAllRead("michael")).isEqualTo(4);
    }

    @Test
    void delete_removesOwnNotification() {
        Notification n = notification(1L, michael, null);
        when(notifications.findById(1L)).thenReturn(Optional.of(n));

        service.delete(1L, "michael");

        verify(notifications).delete(n);
    }

    @Test
    void performAction_callsTheRuleTypesHandlerAndMarksRead() {
        Notification n = notification(1L, michael, "[{\"key\":\"played\",\"label\":\"Gespielt\"}]");
        when(notifications.findById(1L)).thenReturn(Optional.of(n));

        service.performAction(1L, "played", "michael");

        verify(footballHandler).handle(n, "played");
        assertThat(n.getReadAt()).isEqualTo(NOW);
    }

    @Test
    void performAction_readNeedsNoHandler() {
        Notification n = notification(1L, michael, "[{\"key\":\"read\",\"label\":\"Gelesen\"}]");
        n.setRule(null);
        when(notifications.findById(1L)).thenReturn(Optional.of(n));

        service.performAction(1L, "read", "michael");

        assertThat(n.getReadAt()).isEqualTo(NOW);
        verify(footballHandler, never()).handle(any(), any());
    }

    @Test
    void performAction_rejectsActionsTheNotificationDoesNotOffer() {
        when(notifications.findById(1L)).thenReturn(Optional.of(notification(1L, michael, null)));

        assertThatThrownBy(() -> service.performAction(1L, "played", "michael"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void performAction_withoutHandlerFails() {
        Notification n = notification(1L, michael, "[{\"key\":\"x\",\"label\":\"X\"}]");
        n.getRule().setRuleType("UNKNOWN");
        when(notifications.findById(1L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.performAction(1L, "x", "michael")).isInstanceOf(IllegalStateException.class);
        assertThat(n.getReadAt()).isNull();
    }

    @Test
    void create_storesAndPushesUrgentWarnings() {
        when(webPush.send(eq("michael"), any())).thenReturn(1);

        assertThat(service.create(rule, michael, candidate(true, NotificationSeverity.WARNING))).isTrue();

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, atLeastOnce()).save(saved.capture());
        Notification n = saved.getValue();
        assertThat(n.getTitle()).isEqualTo("Budget knapp");
        assertThat(n.getPeriodKey()).isEqualTo("2026-11");
        assertThat(n.getActionsJson()).contains("\"key\":\"read\"");
        assertThat(n.getPushedAt()).isEqualTo(NOW);
        ArgumentCaptor<WebPushService.PushMessage> message = ArgumentCaptor.forClass(WebPushService.PushMessage.class);
        verify(webPush).send(eq("michael"), message.capture());
        assertThat(message.getValue().urgent()).isTrue();
        assertThat(message.getValue().url()).isEqualTo("/expenses");
    }

    @Test
    void create_infoPushIsNotUrgentAndUndeliveredLeavesPushedAtEmpty() {
        when(webPush.send(eq("michael"), any())).thenReturn(0);

        service.create(rule, michael, candidate(true, NotificationSeverity.INFO));

        ArgumentCaptor<WebPushService.PushMessage> message = ArgumentCaptor.forClass(WebPushService.PushMessage.class);
        verify(webPush).send(eq("michael"), message.capture());
        assertThat(message.getValue().urgent()).isFalse();
        verify(notifications, times(1)).save(any());
    }

    @Test
    void create_inboxOnlyDoesNotPush() {
        assertThat(service.create(rule, michael, new NotificationCandidate("T", "M", NotificationSeverity.INFO,
                "k", null, null, false))).isTrue();

        verifyNoInteractions(webPush);
    }

    @Test
    void create_skipsDuplicatesOfTheSamePeriod() {
        when(notifications.existsByRuleAndRecipientUsernameAndPeriodKey(rule, "michael", "2026-11")).thenReturn(true);

        assertThat(service.create(rule, michael, candidate(true, NotificationSeverity.WARNING))).isFalse();

        verify(notifications, never()).save(any());
        verifyNoInteractions(webPush);
    }

    @Test
    void create_raceOnTheUniqueKeyCountsAsDuplicate() {
        when(notifications.save(any(Notification.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThat(service.create(rule, michael, candidate(true, NotificationSeverity.WARNING))).isFalse();

        verifyNoInteractions(webPush);
    }

    @Test
    void candidate_allowsAtMostTwoActions() {
        List<NotificationAction> three = List.of(new NotificationAction("a", "A"), new NotificationAction("b", "B"),
                new NotificationAction("c", "C"));

        assertThatThrownBy(() -> new NotificationCandidate("T", "M", NotificationSeverity.INFO, null, null, three, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendTest_storesAndPushesUrgently() {
        when(users.findByUsername("michael")).thenReturn(Optional.of(michael));
        when(webPush.send(eq("michael"), any())).thenReturn(2);

        assertThat(service.sendTest("michael")).isEqualTo(2);

        ArgumentCaptor<WebPushService.PushMessage> message = ArgumentCaptor.forClass(WebPushService.PushMessage.class);
        verify(webPush).send(eq("michael"), message.capture());
        assertThat(message.getValue().urgent()).isTrue();
        assertThat(message.getValue().title()).isEqualTo("Test-Push");
        assertThat(message.getValue().body()).contains("20:00");
        assertThat(message.getValue().actions()).containsExactly(new NotificationAction("read", "Gelesen"));
    }

    @Test
    void digest_listsUnreadPerUserIncludingUsersWithNone() {
        UserProfile aga = user("Aga");
        when(users.findAll()).thenReturn(List.of(michael, aga));
        when(notifications.findByReadAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(notification(3L, michael, null)));

        DigestDTO digest = service.digest();

        assertThat(digest.generatedAt()).isEqualTo(NOW);
        assertThat(digest.users()).containsOnlyKeys("michael", "Aga");
        assertThat(digest.users().get("michael").unread()).isEqualTo(1);
        assertThat(digest.users().get("michael").notifications().getFirst().title()).isEqualTo("Fußball heute?");
        assertThat(digest.users().get("Aga").unread()).isZero();
    }
}
