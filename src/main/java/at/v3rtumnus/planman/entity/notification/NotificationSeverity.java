package at.v3rtumnus.planman.entity.notification;

/** Icon and colour of a notification in the bell and on the overview page. */
public enum NotificationSeverity {
    INFO("bi-info-circle", "text-primary"),
    WARNING("bi-exclamation-circle", "text-warning"),
    DANGER("bi-x-circle", "text-danger");

    private final String iconClass;
    private final String textClass;

    NotificationSeverity(String iconClass, String textClass) {
        this.iconClass = iconClass;
        this.textClass = textClass;
    }

    public String getIconClass() {
        return iconClass;
    }

    public String getTextClass() {
        return textClass;
    }
}
