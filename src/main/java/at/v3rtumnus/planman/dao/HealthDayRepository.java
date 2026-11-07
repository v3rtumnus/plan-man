package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.health.HealthDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface HealthDayRepository extends JpaRepository<HealthDay, Long> {

    Optional<HealthDay> findByUserUsernameAndDate(String username, LocalDate date);
}
