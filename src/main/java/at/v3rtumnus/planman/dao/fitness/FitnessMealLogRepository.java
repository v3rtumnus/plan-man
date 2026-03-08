package at.v3rtumnus.planman.dao.fitness;

import at.v3rtumnus.planman.entity.fitness.FitnessMealLog;
import at.v3rtumnus.planman.entity.fitness.FitnessProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FitnessMealLogRepository extends JpaRepository<FitnessMealLog, Long> {

    Optional<FitnessMealLog> findByFitnessProfileAndLogDate(FitnessProfile fitnessProfile, LocalDate logDate);

    List<FitnessMealLog> findByFitnessProfileOrderByLogDateDesc(FitnessProfile fitnessProfile);

    void deleteByFitnessProfile(FitnessProfile fitnessProfile);
}
