package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.notification.PushSubscriptionDTO;
import at.v3rtumnus.planman.dto.notification.PushSubscriptionRequest;
import at.v3rtumnus.planman.service.notification.NotificationService;
import at.v3rtumnus.planman.service.notification.PushSubscriptionService;
import at.v3rtumnus.planman.service.notification.WebPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import at.v3rtumnus.planman.conf.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Turning push on and off per device, and the test push. */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushApiController {

    private final PushSubscriptionService subscriptionService;
    private final NotificationService notificationService;
    private final WebPushService webPush;

    @GetMapping("/subscriptions")
    public List<PushSubscriptionDTO> devices() {
        return subscriptionService.devices(CurrentUser.require());
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<PushSubscriptionDTO> subscribe(@RequestBody PushSubscriptionRequest request) {
        if (!webPush.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriptionService.subscribe(CurrentUser.require(), request));
    }

    @DeleteMapping("/subscriptions/{id}")
    public ResponseEntity<Void> unsubscribe(@PathVariable Long id) {
        subscriptionService.unsubscribe(id, CurrentUser.require());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public Map<String, Integer> test() {
        return Map.of("delivered", notificationService.sendTest(CurrentUser.require()));
    }
}
