package at.v3rtumnus.planman.dao.fitness;

import at.v3rtumnus.planman.entity.fitness.FitnessPlan;
import at.v3rtumnus.planman.entity.fitness.FitnessPlanExercise;
import at.v3rtumnus.planman.entity.fitness.FitnessPlanSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FitnessPlanExerciseRepository extends JpaRepository<FitnessPlanExercise, Long> {

    List<FitnessPlanExercise> findByPlanSessionOrderByOrderIndex(FitnessPlanSession planSession);

    void deleteByPlanSession(FitnessPlanSession planSession);

    @Modifying
    @Query("DELETE FROM FitnessPlanExercise e WHERE e.planSession.fitnessPlan = :plan")
    void deleteByPlan(FitnessPlan plan);
}
