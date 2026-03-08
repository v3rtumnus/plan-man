package at.v3rtumnus.planman.dao.fitness;

import at.v3rtumnus.planman.entity.fitness.FitnessProfile;
import at.v3rtumnus.planman.entity.fitness.FitnessSessionExerciseLog;
import at.v3rtumnus.planman.entity.fitness.FitnessSessionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FitnessSessionExerciseLogRepository extends JpaRepository<FitnessSessionExerciseLog, Long> {

    List<FitnessSessionExerciseLog> findBySessionLogOrderByOrderIndex(FitnessSessionLog sessionLog);

    @Modifying
    @Query("DELETE FROM FitnessSessionExerciseLog e WHERE e.sessionLog.fitnessProfile = :profile")
    void deleteByFitnessProfile(FitnessProfile profile);
}
