package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.health.HealthPause;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HealthPauseRepository extends JpaRepository<HealthPause, Long> {

    @Query("SELECT p FROM HealthPause p WHERE p.user.username = :username AND p.startDate <= :date " +
            "AND (p.endDate IS NULL OR p.endDate >= :date) ORDER BY p.startDate DESC")
    List<HealthPause> findCovering(@Param("username") String username, @Param("date") LocalDate date);

    default Optional<HealthPause> findActive(String username, LocalDate date) {
        return findCovering(username, date).stream().findFirst();
    }

    /** Pauses overlapping the range from..to. */
    @Query("SELECT p FROM HealthPause p WHERE p.user.username = :username AND p.startDate <= :to " +
            "AND (p.endDate IS NULL OR p.endDate >= :from)")
    List<HealthPause> findOverlapping(@Param("username") String username, @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);
}
