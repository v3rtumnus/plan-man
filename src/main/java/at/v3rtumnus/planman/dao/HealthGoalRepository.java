package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.health.HealthGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HealthGoalRepository extends JpaRepository<HealthGoal, Long> {

    @Query("SELECT g FROM HealthGoal g WHERE g.user.username = :username AND g.validFrom <= :date " +
            "AND (g.validTo IS NULL OR g.validTo > :date) ORDER BY g.validFrom DESC, g.id DESC")
    List<HealthGoal> findApplyingTo(@Param("username") String username, @Param("date") LocalDate date);

    default Optional<HealthGoal> findGoalFor(String username, LocalDate date) {
        return findApplyingTo(username, date).stream().findFirst();
    }

    Optional<HealthGoal> findFirstByUserUsernameAndValidToIsNullOrderByIdDesc(String username);

    List<HealthGoal> findByUserUsernameOrderByValidFromDescIdDesc(String username);

    @Query("SELECT DISTINCT g.user.username FROM HealthGoal g")
    List<String> findUsernamesWithGoal();
}
