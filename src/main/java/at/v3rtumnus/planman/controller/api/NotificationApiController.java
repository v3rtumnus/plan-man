package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.notification.NotificationDTO;
import at.v3rtumnus.planman.dto.notification.NotificationHeaderDTO;
import at.v3rtumnus.planman.dto.notification.NotificationRuleDTO;
import at.v3rtumnus.planman.dto.notification.NotificationRuleUpdate;
import at.v3rtumnus.planman.service.notification.NotificationRuleService;
import at.v3rtumnus.planman.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import at.v3rtumnus.planman.conf.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Inbox actions of the logged-in user (bell, overview page and the service worker). */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

    private final NotificationService notificationService;
    private final NotificationRuleService ruleService;

    @GetMapping
    public List<NotificationDTO> list() {
        return notificationService.inbox(CurrentUser.require());
    }

    @GetMapping("/header")
    public NotificationHeaderDTO header() {
        return notificationService.header(CurrentUser.require());
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id, CurrentUser.require());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        notificationService.markAllRead(CurrentUser.require());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        notificationService.delete(id, CurrentUser.require());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/actions/{action}")
    public ResponseEntity<Void> perform(@PathVariable Long id, @PathVariable String action) {
        notificationService.performAction(id, action, CurrentUser.require());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rules")
    public List<NotificationRuleDTO> rules() {
        return ruleService.list();
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<Void> updateRule(@PathVariable Long id, @RequestBody NotificationRuleUpdate update) {
        ruleService.update(id, update);
        return ResponseEntity.noContent().build();
    }
}
