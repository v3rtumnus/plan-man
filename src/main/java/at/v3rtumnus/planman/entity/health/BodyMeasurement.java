package at.v3rtumnus.planman.entity.health;

import at.v3rtumnus.planman.entity.UserProfile;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** At most one value per user, type and day; entering a value again replaces it. */
@Entity
@Table(name = "BODY_MEASUREMENT")
@Data
@NoArgsConstructor
public class BodyMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "USER_PROFILE_ID")
    private UserProfile user;

    private LocalDate measuredOn;

    @Enumerated(EnumType.STRING)
    private MeasurementType type;

    private BigDecimal measuredValue;

    @Enumerated(EnumType.STRING)
    private MeasurementSource source;

    private LocalDateTime createdAt;
}
