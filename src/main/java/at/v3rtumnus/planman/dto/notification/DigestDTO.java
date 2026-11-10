package at.v3rtumnus.planman.dto.notification;

import at.v3rtumnus.planman.dto.health.HealthDigest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/digest}: what the Morning Briefing and Glance show from plan-man
 * (notifications, health, and the depot's last week; {@code depot} null without snapshots).
 */
public record DigestDTO(LocalDateTime generatedAt, Map<String, UserDigest> users, DepotWeek depot) {

    public DigestDTO(LocalDateTime generatedAt, Map<String, UserDigest> users) {
        this(generatedAt, users, null);
    }

    public DigestDTO withDepot(DepotWeek depot) {
        return new DigestDTO(generatedAt, users, depot);
    }

    /**
     * Depot value (shares, funds, ETFs) between two daily snapshots a week apart; {@code change}
     * leaves out buys and sales booked in between ({@code netFlows}, gifts not counted).
     */
    public record DepotWeek(LocalDate from, LocalDate to, BigDecimal valueFrom, BigDecimal valueTo,
                            BigDecimal netFlows, BigDecimal change, BigDecimal changePercent, String line) {
    }

    /** @param health null for users without a health goal */
    public record UserDigest(long unread, List<DigestNotification> notifications, HealthDigest health) {

        public UserDigest(long unread, List<DigestNotification> notifications) {
            this(unread, notifications, null);
        }
    }

    public record DigestNotification(Long id, String title, String message, String severity, String url,
                                     LocalDateTime createdAt) {
    }
}
