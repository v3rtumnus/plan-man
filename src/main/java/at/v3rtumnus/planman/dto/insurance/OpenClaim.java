package at.v3rtumnus.planman.dto.insurance;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A claim as the notification rules see it (no attachments). */
public record OpenClaim(Long id, LocalDate entryDate, String person, String name, BigDecimal amount) {
}
