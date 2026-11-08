package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.health.SavedMeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedMealRepository extends JpaRepository<SavedMeal, Long> {

    List<SavedMeal> findByUserUsername(String username);

    Optional<SavedMeal> findByIdAndUserUsername(Long id, String username);

    Optional<SavedMeal> findByUserUsernameAndNameIgnoreCase(String username, String name);
}
