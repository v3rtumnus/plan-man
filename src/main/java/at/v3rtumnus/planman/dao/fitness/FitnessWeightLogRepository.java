package at.v3rtumnus.planman.dao.fitness;

import at.v3rtumnus.planman.entity.fitness.FitnessProfile;
import at.v3rtumnus.planman.entity.fitness.FitnessWeightLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FitnessWeightLogRepository extends JpaRepository<FitnessWeightLog, Long> {

    List<FitnessWeightLog> findByFitnessProfileOrderByLogDateDesc(FitnessProfile fitnessProfile);

    Optional<FitnessWeightLog> findFirstByFitnessProfileOrderByLogDateDesc(FitnessProfile fitnessProfile);

    void deleteByFitnessProfile(FitnessProfile fitnessProfile);
}
