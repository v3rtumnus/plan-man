package at.v3rtumnus.planman.dao.fitness;

import at.v3rtumnus.planman.entity.fitness.FitnessPlan;
import at.v3rtumnus.planman.entity.fitness.FitnessPlanSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FitnessPlanSessionRepository extends JpaRepository<FitnessPlanSession, Long> {

    List<FitnessPlanSession> findByFitnessPlanOrderByWeekNumberAscSessionNumberAsc(FitnessPlan fitnessPlan);

    List<FitnessPlanSession> findByFitnessPlanAndWeekNumber(FitnessPlan fitnessPlan, Integer weekNumber);

    void deleteByFitnessPlan(FitnessPlan fitnessPlan);
}
