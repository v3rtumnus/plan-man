package at.v3rtumnus.planman.dto.notification;

import java.time.LocalDateTime;

public record PushSubscriptionDTO(Long id, String deviceLabel, String endpoint, LocalDateTime createdAt,
                                  LocalDateTime lastSuccessAt, LocalDateTime lastFailureAt, String lastError) {
}
