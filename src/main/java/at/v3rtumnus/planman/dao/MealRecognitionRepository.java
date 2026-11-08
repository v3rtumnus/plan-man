package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.health.MealRecognition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MealRecognitionRepository extends JpaRepository<MealRecognition, Long> {

    long countByUserUsernameAndModelBIsNotNull(String username);

    Optional<MealRecognition> findByIdAndUserUsername(Long id, String username);
}
