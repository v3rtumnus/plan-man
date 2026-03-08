package at.v3rtumnus.planman.entity.fitness;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fitness_weight_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitnessWeightLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fitness_profile_id", nullable = false)
    private FitnessProfile fitnessProfile;

    @Column(nullable = false)
    private LocalDate logDate;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(length = 255)
    private String notes;
}
