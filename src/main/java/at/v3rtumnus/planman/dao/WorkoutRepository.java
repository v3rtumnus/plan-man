package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.health.Workout;
import at.v3rtumnus.planman.entity.health.WorkoutType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    List<Workout> findByUserUsernameAndDoneOnOrderByCreatedAtAsc(String username, LocalDate doneOn);

    List<Workout> findByUserUsernameAndDoneOnBetweenOrderByDoneOnAscCreatedAtAsc(String username, LocalDate from, LocalDate to);

    List<Workout> findByUserUsernameAndTypeAndDoneOnGreaterThanEqual(String username, WorkoutType type, LocalDate from);

    List<Workout> findTop10ByUserUsernameOrderByDoneOnDescCreatedAtDesc(String username);

    Optional<Workout> findByIdAndUserUsername(Long id, String username);
}
