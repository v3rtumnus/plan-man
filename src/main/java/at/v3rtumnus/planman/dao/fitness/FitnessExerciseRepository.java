package at.v3rtumnus.planman.dao.fitness;

import at.v3rtumnus.planman.entity.fitness.ExerciseCategory;
import at.v3rtumnus.planman.entity.fitness.FitnessExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FitnessExerciseRepository extends JpaRepository<FitnessExercise, Long> {

    List<FitnessExercise> findByCategory(ExerciseCategory category);
}
