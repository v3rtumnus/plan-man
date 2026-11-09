package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.service.notification.NotificationService;
import at.v3rtumnus.planman.service.notification.PushSubscriptionService;
import at.v3rtumnus.planman.service.notification.WebPushService;
import lombok.RequiredArgsConstructor;
import at.v3rtumnus.planman.conf.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

/** Inbox page with the push settings of this device. */
@Controller
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final PushSubscriptionService subscriptionService;
    private final WebPushService webPush;

    @GetMapping("/notifications")
    public ModelAndView overview() {
        ModelAndView mav = new ModelAndView("notifications/overview");
        mav.addObject("notifications", notificationService.inbox(CurrentUser.require()));
        mav.addObject("devices", subscriptionService.devices(CurrentUser.require()));
        mav.addObject("vapidPublicKey", webPush.publicKey());
        return mav;
    }
}
