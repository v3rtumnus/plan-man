package at.v3rtumnus.planman.dto.health;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeightPoint(LocalDate date, BigDecimal kg) {
}
