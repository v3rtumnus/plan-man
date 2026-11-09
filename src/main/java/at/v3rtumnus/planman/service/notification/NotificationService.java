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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Inbox, read state, actions and push for notifications. Every query is scoped to one user. */
@Service
@Slf4j
public class NotificationService {

    /** Built-in action: only marks the notification as read. */
    public static final String ACTION_READ = "read";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<NotificationAction>> ACTIONS = new TypeReference<>() {
    };

    private final NotificationRepository notifications;
    private final PushSubscriptionRepository subscriptions;
    private final UserProfileRepository users;
    private final WebPushService webPush;
    private final Clock clock;
    private final Map<String, NotificationActionHandler> actionHandlers;

    public NotificationService(NotificationRepository notifications, PushSubscriptionRepository subscriptions,
                               UserProfileRepository users, WebPushService webPush, Clock clock,
                               ObjectProvider<NotificationActionHandler> actionHandlers) {
        this.notifications = notifications;
        this.subscriptions = subscriptions;
        this.users = users;
        this.webPush = webPush;
        this.clock = clock;
        this.actionHandlers = actionHandlers.orderedStream()
                .collect(Collectors.toMap(NotificationActionHandler::ruleType, Function.identity()));
    }

    public NotificationHeaderDTO header(String username) {
        return new NotificationHeaderDTO(
                notifications.countByRecipientUsernameAndReadAtIsNull(username),
                notifications.findTop5ByRecipientUsernameAndReadAtIsNullOrderByCreatedAtDesc(username).stream()
                        .map(this::toDto).toList(),
                pushDeviceCount(username));
    }

    /** All unread, then the 30 newest read ones. */
    public List<NotificationDTO> inbox(String username) {
        List<NotificationDTO> inbox = new ArrayList<>();
        notifications.findByRecipientUsernameAndReadAtIsNullOrderByCreatedAtDesc(username).stream()
                .map(this::toDto).forEach(inbox::add);
        notifications.findTop30ByRecipientUsernameAndReadAtIsNotNullOrderByCreatedAtDesc(username).stream()
                .map(this::toDto).forEach(inbox::add);
        return inbox;
    }

    @Transactional
    public void markRead(Long id, String username) {
        Notification notification = owned(id, username);
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now(clock));
            notifications.save(notification);
        }
    }

    @Transactional
    public int markAllRead(String username) {
        return notifications.markAllRead(username, LocalDateTime.now(clock));
    }

    @Transactional
    public void delete(Long id, String username) {
        notifications.delete(owned(id, username));
    }

    /**
     * Runs an action button. The key must be one of the notification's own actions; the rule
     * type's handler does the work, then the notification counts as read.
     */
    @Transactional
    public void performAction(Long id, String actionKey, String username) {
        Notification notification = owned(id, username);
        boolean offered = actions(notification).stream().anyMatch(a -> a.key().equals(actionKey));
        if (!offered) {
            throw new IllegalArgumentException("Action '" + actionKey + "' is not offered by notification " + id);
        }
        if (!ACTION_READ.equals(actionKey)) {
            String ruleType = notification.getRule() == null ? null : notification.getRule().getRuleType();
            NotificationActionHandler handler = ruleType == null ? null : actionHandlers.get(ruleType);
            if (handler == null) {
                throw new IllegalStateException("No handler for action '" + actionKey + "' of rule type " + ruleType);
            }
            handler.handle(notification, actionKey);
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now(clock));
            notifications.save(notification);
        }
    }

    /**
     * Stores a rule's notification for one user unless that rule already notified the user in
     * this period, and pushes it if asked. Returns false for a duplicate.
     */
    public boolean create(NotificationRule rule, UserProfile recipient, NotificationCandidate candidate) {
        Notification notification = store(rule, recipient, candidate);
        if (notification == null) {
            return false;
        }
        if (candidate.push()) {
            push(notification, candidate.severity() != NotificationSeverity.INFO);
        }
        return true;
    }

    /** Sends a test notification with a push to all of the user's devices; returns devices reached. */
    public int sendTest(String username) {
        UserProfile user = users.findByUsername(username).orElseThrow();
        LocalDateTime now = LocalDateTime.now(clock);
        Notification notification = store(null, user, new NotificationCandidate("Test-Push",
                "Push funktioniert. Gesendet um " + now.format(DateTimeFormatter.ofPattern("HH:mm")) + " Uhr.",
                NotificationSeverity.WARNING, null, "/notifications",
                List.of(new NotificationAction(ACTION_READ, "Gelesen")), true));
        return push(notification, true);
    }

    private Notification store(NotificationRule rule, UserProfile recipient, NotificationCandidate candidate) {
        if (rule != null && candidate.periodKey() != null
                && notifications.existsByRuleAndRecipientUsernameAndPeriodKey(rule, recipient.getUsername(), candidate.periodKey())) {
            return null;
        }
        Notification notification = new Notification();
        notification.setRule(rule);
        notification.setRecipient(recipient);
        notification.setTitle(candidate.title());
        notification.setMessage(candidate.message());
        notification.setSeverity(candidate.severity());
        notification.setUrl(candidate.url());
        notification.setActionsJson(candidate.actions().isEmpty() ? null : JSON.writeValueAsString(candidate.actions()));
        notification.setPush(candidate.push());
        notification.setPeriodKey(candidate.periodKey());
        notification.setCreatedAt(LocalDateTime.now(clock));
        try {
            notification = notifications.save(notification);
        } catch (DataIntegrityViolationException e) {
            // Unique (rule, user, period): a parallel run got there first.
            return null;
        }
        log.info("Notification {} '{}' for {}", notification.getId(), candidate.title(), recipient.getUsername());
        return notification;
    }

    public long pushDeviceCount(String username) {
        return subscriptions.countByUserUsername(username);
    }

    /** Unread notifications of every user for the Morning Briefing / Glance. */
    @Transactional(readOnly = true)
    public DigestDTO digest() {
        Map<String, List<Notification>> unread = notifications.findByReadAtIsNullOrderByCreatedAtDesc().stream()
                .collect(Collectors.groupingBy(n -> n.getRecipient().getUsername(), LinkedHashMap::new, Collectors.toList()));
        Map<String, DigestDTO.UserDigest> byUser = new TreeMap<>();
        for (UserProfile user : users.findAll()) {
            List<DigestDTO.DigestNotification> items = unread.getOrDefault(user.getUsername(), List.of()).stream()
                    .map(n -> new DigestDTO.DigestNotification(n.getId(), n.getTitle(), n.getMessage(),
                            n.getSeverity().name(), n.getUrl(), n.getCreatedAt()))
                    .toList();
            byUser.put(user.getUsername(), new DigestDTO.UserDigest(items.size(), items));
        }
        return new DigestDTO(LocalDateTime.now(clock), byUser);
    }

    private int push(Notification notification, boolean urgent) {
        int delivered = webPush.send(notification.getRecipient().getUsername(), new WebPushService.PushMessage(
                notification.getId(), notification.getTitle(), notification.getMessage(), notification.getUrl(),
                actions(notification), urgent));
        if (delivered > 0) {
            notification.setPushedAt(LocalDateTime.now(clock));
            notifications.save(notification);
        }
        return delivered;
    }

    private Notification owned(Long id, String username) {
        Notification notification = notifications.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Notification not found: " + id));
        if (!notification.getRecipient().getUsername().equals(username)) {
            throw new AccessDeniedException("Notification " + id + " does not belong to " + username);
        }
        return notification;
    }

    private List<NotificationAction> actions(Notification notification) {
        return notification.getActionsJson() == null ? List.of() : JSON.readValue(notification.getActionsJson(), ACTIONS);
    }

    NotificationDTO toDto(Notification n) {
        return new NotificationDTO(n.getId(), n.getTitle(), n.getMessage(), n.getSeverity(), n.getUrl(),
                actions(n), n.getCreatedAt(), n.isRead());
    }
}
