package at.v3rtumnus.planman.entity.health;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** The weight used for an exercise from validFrom on; at most one row per exercise and day. */
@Entity
@Table(name = "EXERCISE_WEIGHT")
@Getter
@Setter
@NoArgsConstructor
public class ExerciseWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "EXERCISE_ID")
    private Exercise exercise;

    @Column(name = "WEIGHT_KG")
    private BigDecimal weightKg;

    @Column(name = "VALID_FROM")
    private LocalDate validFrom;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}
