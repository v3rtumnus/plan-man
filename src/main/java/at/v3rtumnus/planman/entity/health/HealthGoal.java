package at.v3rtumnus.planman.entity.health;

import at.v3rtumnus.planman.entity.UserProfile;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "HEALTH_GOAL")
@Data
@NoArgsConstructor
public class HealthGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "USER_PROFILE_ID")
    private UserProfile user;

    private String title;

    private LocalDate validFrom;

    /** First day the goal no longer applies; null for the current goal. */
    private LocalDate validTo;

    private int kcalTarget;

    @Column(name = "PROTEIN_TARGET_G")
    private int proteinTargetG;

    private int footballBonusKcal;

    private int freeleticsBonusKcal;

    private int stepsTarget;

    private BigDecimal startWeightKg;

    private BigDecimal targetWeightKg;

    private LocalDate targetDate;

    private Integer pushUpsTarget;

    private LocalDateTime createdAt;

    public boolean appliesTo(LocalDate date) {
        return !date.isBefore(validFrom) && (validTo == null || date.isBefore(validTo));
    }
}
