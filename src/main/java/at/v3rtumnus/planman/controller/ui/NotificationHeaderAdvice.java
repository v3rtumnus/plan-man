package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.conf.CurrentUser;
import at.v3rtumnus.planman.dto.notification.NotificationHeaderDTO;
import at.v3rtumnus.planman.service.notification.NotificationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Feeds the bell in the header on every page (UI controllers only, not the JSON API). */
@ControllerAdvice(basePackageClasses = NotificationController.class)
public class NotificationHeaderAdvice {

    // ObjectProvider: @WebMvcTest slices of other controllers load this advice without the service.
    private final ObjectProvider<NotificationService> notificationService;

    public NotificationHeaderAdvice(ObjectProvider<NotificationService> notificationService) {
        this.notificationService = notificationService;
    }

    @ModelAttribute("notificationHeader")
    public NotificationHeaderDTO notificationHeader() {
        NotificationService service = notificationService.getIfAvailable();
        String username = CurrentUser.name();
        if (service == null || username == null) {
            return NotificationHeaderDTO.empty();
        }
        return service.header(username);
    }
}
