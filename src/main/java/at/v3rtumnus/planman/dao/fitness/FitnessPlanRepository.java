package at.v3rtumnus.planman.dao.fitness;

import at.v3rtumnus.planman.entity.fitness.FitnessPlan;
import at.v3rtumnus.planman.entity.fitness.FitnessProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FitnessPlanRepository extends JpaRepository<FitnessPlan, Long> {

    Optional<FitnessPlan> findByFitnessProfileAndActiveTrue(FitnessProfile fitnessProfile);

    List<FitnessPlan> findByFitnessProfileOrderByVersionDesc(FitnessProfile fitnessProfile);

    void deleteByFitnessProfile(FitnessProfile fitnessProfile);
}
