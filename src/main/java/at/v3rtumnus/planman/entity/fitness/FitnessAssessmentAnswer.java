package at.v3rtumnus.planman.entity.fitness;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fitness_assessment_answer")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitnessAssessmentAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fitness_profile_id", nullable = false)
    private FitnessProfile fitnessProfile;

    @Column(nullable = false, length = 50)
    private String questionKey;

    @Column(length = 255)
    private String answerValue;
}
