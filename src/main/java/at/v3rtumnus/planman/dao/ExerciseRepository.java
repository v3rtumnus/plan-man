package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.health.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    List<Exercise> findByUserUsernameAndActiveTrueOrderByCreatedAtAscIdAsc(String username);

    Optional<Exercise> findByIdAndUserUsername(Long id, String username);

    Optional<Exercise> findByUserUsernameAndNameIgnoreCase(String username, String name);
}
