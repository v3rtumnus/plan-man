package at.v3rtumnus.planman.dao.fitness;

import at.v3rtumnus.planman.entity.fitness.FitnessProfile;
import at.v3rtumnus.planman.entity.fitness.FitnessPlanSession;
import at.v3rtumnus.planman.entity.fitness.FitnessSessionLog;
import at.v3rtumnus.planman.entity.fitness.SessionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FitnessSessionLogRepository extends JpaRepository<FitnessSessionLog, Long> {

    List<FitnessSessionLog> findByFitnessProfileOrderByLogDateDesc(FitnessProfile fitnessProfile);

    Optional<FitnessSessionLog> findTopByPlanSessionOrderByIdDesc(FitnessPlanSession planSession);

    void deleteByFitnessProfile(FitnessProfile fitnessProfile);

    List<FitnessSessionLog> findByFitnessProfileAndLogDateAndSessionType(FitnessProfile fitnessProfile, LocalDate logDate, SessionType sessionType);

    List<FitnessSessionLog> findByFitnessProfileAndPlanSessionIsNullAndSessionTypeAndAiAnalyzedFalse(FitnessProfile fitnessProfile, SessionType sessionType);
}
