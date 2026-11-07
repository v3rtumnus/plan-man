package at.v3rtumnus.planman.entity.health;

import at.v3rtumnus.planman.entity.UserProfile;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "HEALTH_PAUSE")
@Data
@NoArgsConstructor
public class HealthPause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "USER_PROFILE_ID")
    private UserProfile user;

    private LocalDate startDate;

    /** Last paused day (inclusive); null while the pause is running. */
    private LocalDate endDate;

    private String reason;

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
    }
}
