package at.v3rtumnus.planman.dao.fitness;

import at.v3rtumnus.planman.entity.fitness.FitnessAssessmentAnswer;
import at.v3rtumnus.planman.entity.fitness.FitnessProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FitnessAssessmentAnswerRepository extends JpaRepository<FitnessAssessmentAnswer, Long> {

    List<FitnessAssessmentAnswer> findByFitnessProfile(FitnessProfile fitnessProfile);

    void deleteByFitnessProfile(FitnessProfile fitnessProfile);
}
